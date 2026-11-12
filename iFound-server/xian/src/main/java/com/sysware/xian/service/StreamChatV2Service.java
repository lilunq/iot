package com.sysware.xian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 代理 Hermes StreamChatV2，按照 agentId + userName 维护服务端会话缓存及旁路记录。
 */
@Slf4j
@Service
public class StreamChatV2Service {

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    /** 共用模式下的 pcId 占位符，使所有来源共用同一 agent 的 sessionKey */
    private static final String SHARED_PC_ID = "__SHARED__";

    /** 匹配从触发词（加载/使用/调用/应用/切换）到 "技能来/技能进行" 等之间的任意内容，整体移除 */
    private static final Pattern SKILL_NAME_PATTERN =
            Pattern.compile("(?:加载|使用|调用|应用|切换).*?技能(?:来|进行|以|去)?");

    /** 消息内容命中 output length limit/纯英文 等异常时，返回给前端的统一提示语 */
    private static final String WORK_MODE_NOTICE = "正在转为工作模式，请稍后重试";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final StreamChatV2RecordService recordService;
    private final String streamChatV2Url;
    /** 是否按用户区分会话缓存；true=每用户独立 sessionKey，false=同 agent 全局共用 */
    private final boolean sessionUserDistinguished;
    private final Map<SessionCacheKey, String> sessionIdCache = new ConcurrentHashMap<>();
    private final Map<SessionCacheKey, AtomicLong> cacheGenerations = new ConcurrentHashMap<>();
    /** 已拼接过用户信息前缀的用户集合（与会话缓存键解耦，按真实 (agentId, userName) 标记） */
    private final Map<String, Boolean> introducedUsers = new ConcurrentHashMap<>();
    /** 标记是否有 StreamChatV2 请求正在进行中 */
    private final AtomicBoolean streamInProgress = new AtomicBoolean(false);
    /** 全局登记当前活跃请求的连接句柄（前端 SSE emitter + 上游订阅），打断时由 Java 端主动关闭，不依赖 Hermes 断流 */
    private final AtomicReference<ActiveStream> activeStream = new AtomicReference<>();
    /** 当前活跃请求的 sessionId，供外部推送 useData 事件时使用 */
    private final AtomicReference<String> activeSessionId = new AtomicReference<>();
    /** 工具短名 → 含义映射（如 getSubareaState → 区域列表），匹配上的 tool.full 会缓存含义 */
    private final Map<String, String> toolMeanings;
    /** 工具含义缓存，键 (agentId, pcId)，值 = 最近一次匹配的工具含义 */
    private final Map<SessionCacheKey, String> toolMeaningCache = new ConcurrentHashMap<>();

    /** 进度推送调度器（共享线程池，每次流式请求创建独立的定时任务） */
    private final ScheduledExecutorService progressScheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "progress-scheduler");
        t.setDaemon(true);
        return t;
    });

    /** getSubareaState 工具触发时的进度消息（第一条立即推送，后续每5秒推送一条，不循环） */
    private static final List<String> CREATE_REPAIR_ISSUE_AI_DIAG = List.of(
            "正在收集相关数据...",
            "正在分析故障原因...",
            "正在生成排障方案...",
            "正在匹配维修人员...",
            "正在整理最终结果,请耐心等待..."
    );

    public StreamChatV2Service(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            StreamChatV2RecordService recordService,
            @Value("${ai.stream-chat-v2-url}") String streamChatV2Url,
            @Value("${ai.session.user-distinguished:false}") boolean sessionUserDistinguished,
            @Value("${ai.tool-meanings:}") String toolMeaningsStr
    ) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.recordService = recordService;
        this.streamChatV2Url = streamChatV2Url;
        this.sessionUserDistinguished = sessionUserDistinguished;
        this.toolMeanings = parseToolMeanings(toolMeaningsStr);
        log.debug("StreamChatV2 会话缓存模式: user-distinguished={}, tool-meanings={}", sessionUserDistinguished, this.toolMeanings);
    }

    /**
     * 解析 "getSubareaState:区域列表,getDeviceStatePage:设备列表" 格式为 Map
     */
    private static Map<String, String> parseToolMeanings(String str) {
        Map<String, String> map = new HashMap<>();
        if (str == null || str.isBlank()) return map;
        for (String pair : str.split(",")) {
            pair = pair.trim();
            if (pair.isEmpty()) continue;
            int sep = pair.indexOf(':');
            if (sep > 0 && sep < pair.length() - 1) {
                map.put(pair.substring(0, sep).trim(), pair.substring(sep + 1).trim());
            }
        }
        return map;
    }

    /**
     * 根据配置解析会话缓存键。
     * 区分模式 → (agentId, pcId)；共用模式 → (agentId, __SHARED__)。
     */
    private SessionCacheKey resolveSessionCacheKey(String agentId, String pcId) {
        return sessionUserDistinguished
                ? new SessionCacheKey(agentId, pcId)
                : new SessionCacheKey(agentId, SHARED_PC_ID);
    }

    /**
     * 判断用户是否首次接入（需拼接身份前缀）。
     * 首次返回 true 并标记；后续返回 false。两种缓存模式下均按真实 (agentId, userName) 判断，
     * 确保每个用户的身份信息在自身首次接入时各拼接一次。
     */
    public boolean shouldPrependUserInfo(String agentId, String userName) {
        String userKey = agentId + "::" + userName;
        return introducedUsers.putIfAbsent(userKey, Boolean.TRUE) == null;
    }

    public void stream(String message, String userName, String userId, String pcId, SseEmitter emitter) {
        stream(message, userName, userId, pcId, "commander", streamChatV2Url, emitter);
    }

    public void streamWithAgent(
            String message,
            String userName,
            String userId,
            String pcId,
            String agentId,
            String agentHermesUrl,
            SseEmitter emitter
    ) {
        String resolvedAgentId = StringUtils.hasText(agentId) ? agentId : "commander";
        String resolvedStreamUrl = resolveStreamV2Url(agentHermesUrl);
        stream(message, userName, userId, pcId, resolvedAgentId, resolvedStreamUrl, emitter);
    }

    private void stream(
            String message,
            String userName,
            String userId,
            String pcId,
            String agentId,
            String targetStreamUrl,
            SseEmitter emitter
    ) {
        stream(message, userName, userId, pcId, agentId, targetStreamUrl, emitter, false, false);
    }

    private void stream(
            String message,
            String userName,
            String userId,
            String pcId,
            String agentId,
            String targetStreamUrl,
            SseEmitter emitter,
            boolean retried,
            boolean forceEmptySession
    ) {
        LocalDateTime requestTime = LocalDateTime.now();
        // 若上一次请求尚未结束，先主动关闭其连接，再继续当前请求
        if (streamInProgress.get()) {
            interruptPreviousStream();
        }
        streamInProgress.set(true);
        SessionCacheKey sessionCacheKey = resolveSessionCacheKey(agentId, pcId);
        // sessionKey 过期重试时强制使用空 sessionKey，由 Hermes 重建会话，成功后 forwardAndCapture 会更新缓存
        String requestSessionId = forceEmptySession
                ? ""
                : sessionIdCache.getOrDefault(sessionCacheKey, "");
        AtomicLong userCacheGeneration = cacheGenerations.computeIfAbsent(sessionCacheKey, key -> new AtomicLong());
        long requestGeneration = userCacheGeneration.get();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("message", message);
        requestBody.put("sessionKey", requestSessionId);

        AtomicReference<String> responseSessionId = new AtomicReference<>();
        AtomicReference<String> completePayload = new AtomicReference<>();
        AtomicReference<String> actionContent = new AtomicReference<>();
        AtomicReference<String> errorMessage = new AtomicReference<>();
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> progressRef = new AtomicReference<>();
        AtomicBoolean finalized = new AtomicBoolean(false);
        // 标记本轮是否已向前端转发过上游事件，用于判断连接中断时能否安全重试
        AtomicBoolean forwardedAny = new AtomicBoolean(false);
        // stationAgent 提前完成前端 SSE 时标记，防止 onCompletion 中断 Hermes 上游订阅
        AtomicBoolean earlyComplete = new AtomicBoolean(false);

        // 登记当前请求的连接句柄及消息内容，供后续新请求打断时由 Java 端主动关闭并打印日志
        ActiveStream currentActive = new ActiveStream(emitter, subscriptionRef, message);
        activeStream.set(currentActive);

        Runnable finalizeRecord = () -> {
            if (finalized.compareAndSet(false, true)) {
                cancelProgressTask(progressRef);
                streamInProgress.set(false);
                // 仅当活跃登记仍是本次请求时清除，避免误清后续请求的登记
                activeStream.compareAndSet(currentActive, null);
                try {
                    recordService.saveAsync(
                            requestTime,
                            LocalDateTime.now(),
                            message,
                            userName,
                            userId,
                            agentId,
                            requestSessionId,
                            responseSessionId.get(),
                            completePayload.get(),
                            actionContent.get(),
                            errorMessage.get()
                    );
                } catch (RuntimeException e) {
                    // 线程池拒绝任务时只记录告警，绝不回退到 SSE 线程同步写库。
                    log.error("提交 StreamChatV2 异步落库任务失败: sessionId={}", responseSessionId.get(), e);
                }
            }
        };

        emitter.onTimeout(() -> {
            errorMessage.compareAndSet(null, "SSE connection timeout");
            dispose(subscriptionRef);
            finalizeRecord.run();
        });
        emitter.onError(error -> {
            errorMessage.compareAndSet(null, error.getMessage());
            dispose(subscriptionRef);
            finalizeRecord.run();
        });
        emitter.onCompletion(() -> {
            // stationAgent 提前完成前端 SSE 时保留 Hermes 上游订阅不断开
            if (!earlyComplete.get()) {
                dispose(subscriptionRef);
            }
            finalizeRecord.run();
        });

        Disposable subscription = webClient.post()
                .uri(targetStreamUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(SSE_TYPE)
                .subscribe(
                        event -> forwardAndCapture(
                                event,
                                emitter,
                                sessionCacheKey,
                                requestGeneration,
                                responseSessionId,
                                completePayload,
                                actionContent,
                                errorMessage,
                                subscriptionRef,
                                finalizeRecord,
                                progressRef,
                                forwardedAny,
                                earlyComplete
                        ),
                        error -> {
                            String errorMsg = error.getMessage();
                            errorMessage.compareAndSet(null, errorMsg);
                            log.error("调用 StreamChatV2 流式接口失败: {}", errorMsg);
                            // 上游返回 200 后连接被提前切断（Hermes 重启/代理空闲超时）：
                            // 若尚未向前端转发任何事件，保留原 sessionKey 自动重试一次，前端无感知；
                            // 已转发过内容则不重试，避免前端出现重复回答
                            if (!retried && !forwardedAny.get() && isPrematureCloseError(error)) {
                                log.warn("StreamChatV2 连接被提前关闭且未向前端推送内容，保留 sessionKey 自动重试一次: key={}", sessionCacheKey);
                                finalizeRecord.run();
                                stream(message, userName, userId, pcId, agentId, targetStreamUrl, emitter, true, false);
                                return;
                            }
                            // sessionKey 过期时 Hermes 返回 5xx 或提示会话不存在：
                            // 清除该缓存并用空 sessionKey 自动重试一次，成功后会重新缓存新的 sessionKey
                            if (!retried
                                    && StringUtils.hasText(requestSessionId)
                                    && isSessionExpiredError(error, errorMsg)) {
                                log.error("StreamChatV2 sessionKey 过期导致报错: key={}, 过期的sessionKey={}, 错误信息={}, 上游响应体={}",
                                        sessionCacheKey, requestSessionId, errorMsg, extractResponseBody(error), error);
                                log.warn("StreamChatV2 清除过期 sessionKey 缓存并使用空 sessionKey 重试: key={}", sessionCacheKey);
                                clearSessionKey(sessionCacheKey);
                                finalizeRecord.run();
                                stream(message, userName, userId, pcId, agentId, targetStreamUrl, emitter, true, true);
                                return;
                            }
                            // 会话不存在（503/会话过期），清除所有缓存；其余不可恢复错误也无条件清除当前会话缓存，
                            // 避免失效 sessionKey 残留导致下次请求连环报错，下次请求自动用空 sessionKey 重建会话
                            if (errorMsg != null && errorMsg.contains("该会话不存在")) {
                                log.warn("StreamChatV2 会话不存在，清除所有 sessionKey 缓存");
                                clearAllSessionCaches();
                            } else {
                                log.warn("StreamChatV2 请求失败进入兜底处理，清除当前 sessionKey 缓存: key={}", sessionCacheKey);
                                clearSessionKey(sessionCacheKey);
                            }
                            sendErrorEvent(emitter, errorMsg);
                            finalizeRecord.run();
                            emitter.complete();
                        },
                        () -> {
                            finalizeRecord.run();
                            emitter.complete();
                        }
                );
        subscriptionRef.set(subscription);
        if (finalized.get()) {
            subscription.dispose();
        }
    }

    /**
     * 检查指定 pcId 是否有缓存的 sessionKey
     */
    public boolean hasSessionCache(String pcId) {
        return sessionIdCache.keySet().stream()
                .anyMatch(key -> key.pcId().equals(pcId) && StringUtils.hasText(sessionIdCache.get(key)));
    }

    /**
     * 手动设置指定 (agentId, pcId) 的 sessionKey 缓存，仅用于测试过期重试场景。
     * 返回实际生效的缓存键描述及旧值。
     */
    public Map<String, Object> setSessionKeyForTest(String agentId, String pcId, String sessionKey) {
        String resolvedAgentId = StringUtils.hasText(agentId) ? agentId : "commander";
        SessionCacheKey cacheKey = resolveSessionCacheKey(resolvedAgentId, pcId);
        String oldValue = sessionIdCache.put(cacheKey, sessionKey);
        log.debug("【测试】手动设置 sessionKey: key={}, 旧值={}, 新值={}", cacheKey, oldValue, sessionKey);
        Map<String, Object> result = new HashMap<>();
        result.put("cacheKey", cacheKey.toString());
        result.put("oldSessionKey", oldValue);
        result.put("newSessionKey", sessionKey);
        return result;
    }

    /**
     * 提取上游错误响应体，用于 sessionKey 过期时打印完整报错内容。
     */
    private String extractResponseBody(Throwable error) {
        if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException responseError) {
            return responseError.getResponseBodyAsString();
        }
        return "";
    }

    /**
     * 判断上游错误是否为连接被提前关闭（200 后流中断，多见于上游重启或代理空闲超时）。
     */
    private boolean isPrematureCloseError(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof reactor.netty.http.client.PrematureCloseException) {
                return true;
            }
        }
        String msg = error.getMessage();
        return msg != null && msg.contains("Connection prematurely closed");
    }

    /**
     * 判断上游错误是否由 sessionKey 过期导致：5xx 服务端错误或明确提示会话不存在。
     */
    private boolean isSessionExpiredError(Throwable error, String errorMsg) {
        if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException responseError
                && responseError.getStatusCode().is5xxServerError()) {
            return true;
        }
        return errorMsg != null && errorMsg.contains("该会话不存在");
    }

    /**
     * 清除单个会话缓存键的 sessionKey 及其工具含义缓存，并递增代次防止旧请求回写。
     */
    private void clearSessionKey(SessionCacheKey key) {
        AtomicLong generation = cacheGenerations.get(key);
        if (generation != null) {
            generation.incrementAndGet();
        }
        sessionIdCache.remove(key);
        toolMeaningCache.remove(key);
    }

    public int clearSessionCache(String pcId) {
        cacheGenerations.forEach((key, generation) -> {
            if (key.pcId().equals(pcId)) {
                generation.incrementAndGet();
            }
        });

        List<SessionCacheKey> keysToRemove = sessionIdCache.keySet().stream()
                .filter(key -> key.pcId().equals(pcId))
                .toList();
        int clearedCount = 0;
        for (SessionCacheKey key : keysToRemove) {
            if (sessionIdCache.remove(key) != null) {
                clearedCount++;
            }
        }
        // 同步清理该 pcId 的工具含义缓存
        toolMeaningCache.keySet().removeIf(key -> key.pcId().equals(pcId));
        return clearedCount;
    }

    public int clearAllSessionCaches() {
        cacheGenerations.values().forEach(AtomicLong::incrementAndGet);
        int clearedCount = sessionIdCache.size();
        sessionIdCache.clear();
        // 同步清理所有用户的"已拼接前缀"标记
        introducedUsers.clear();
        toolMeaningCache.clear();
        return clearedCount;
    }

    private void forwardAndCapture(
            ServerSentEvent<String> event,
            SseEmitter emitter,
            SessionCacheKey sessionCacheKey,
            long requestGeneration,
            AtomicReference<String> responseSessionId,
            AtomicReference<String> completePayload,
            AtomicReference<String> actionContent,
            AtomicReference<String> errorMessage,
            AtomicReference<Disposable> subscriptionRef,
            Runnable finalizeRecord,
            AtomicReference<ScheduledFuture<?>> progressRef,
            AtomicBoolean forwardedAny,
            AtomicBoolean earlyComplete
    ) {
        String data = event.data();
        if (data == null) {
            return;
        }

        // 过滤 Hermes 追加在正文末尾的 file-mutation verifier 提示
        // （形如 "⚠️ File-mutation verifier: ..."），保留其前的正文内容。
        String forwardedData = stripFileMutationFooter(data);

        // 过滤文本中形如 "加载 `area-status-checker`" 的技能名称标记
        forwardedData = stripSkillName(forwardedData);

        // 先解析 JSON 以获取 type，用于设置 SSE 事件名
        JsonNode root = null;
        String jsonType = null;
        try {
            root = objectMapper.readTree(forwardedData);
            jsonType = root.path("type").asText(null);
        } catch (Exception e) {
            // JSON 解析失败不影响透传，后续逻辑跳过
        }

        // 内容过滤1：包含 interrupted 的事件不推送前端（仍继续会话缓存与旁路记录）
        boolean skipForward = forwardedData.toLowerCase().contains("interrupted");
        if (skipForward) {
            log.debug("事件内容包含 interrupted，跳过推送前端: type={}", jsonType);
        }

        // 内容过滤2：消息类事件命中 output length limit 或纯英文时，替换为统一提示语。
        // delta 阶段直接跳过不推送，统一由 message.complete 事件向前端返回提示语，避免提示语重复出现。
        if (!skipForward && root != null && jsonType != null && jsonType.startsWith("message")) {
            JsonNode textPayload = root.path("payload");
            if (textPayload.isTextual()) {
                String text = textPayload.asText();
                if (text.contains("output length limit") || isAllEnglishText(text)) {
                    if ("message.complete".equals(jsonType)) {
                        log.warn("消息内容命中 output length limit/纯英文，已替换为提示语，原内容: {}", text);
                        ((ObjectNode) root).put("payload", WORK_MODE_NOTICE);
                        try {
                            forwardedData = objectMapper.writeValueAsString(root);
                        } catch (Exception e) {
                            log.warn("替换提示语后序列化失败，跳过推送: {}", e.getMessage());
                            skipForward = true;
                        }
                    } else {
                        skipForward = true;
                    }
                }
            }
        }

        if (!skipForward) {
            try {
                SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event().data(forwardedData);
                // 优先使用上游事件名，若为空或为 "message" 则使用 JSON body 的 type 作为事件名
                String eventName = StringUtils.hasText(event.event()) && !"message".equals(event.event())
                        ? event.event() : jsonType;
                if (StringUtils.hasText(eventName)) {
                    eventBuilder.name(eventName);
                }
                emitter.send(eventBuilder);
                forwardedAny.set(true);
            } catch (Exception e) {
                errorMessage.compareAndSet(null, e.getMessage());
                dispose(subscriptionRef);
                finalizeRecord.run();
                return;
            }
        }

        if (root == null) return;

        try {
            String sessionId = root.path("sessionId").asText(null);
            if (StringUtils.hasText(sessionId)) {
                responseSessionId.set(sessionId);
                activeSessionId.set(sessionId);
                AtomicLong currentGeneration = cacheGenerations.get(sessionCacheKey);
                if (currentGeneration != null && currentGeneration.get() == requestGeneration) {
                    sessionIdCache.put(sessionCacheKey, sessionId);
                }
            }

            String type = jsonType != null ? jsonType : event.event();
            if ("message.complete".equals(type)) {
                cancelProgressTask(progressRef);
                JsonNode payload = root.get("payload");
                if (payload != null && !payload.isNull()) {
                    completePayload.set(payload.isTextual() ? payload.asText() : payload.toString());
                }
            } else if ("action".equals(type)) {
                // action 要求保存最终返回的原始 data JSON，而不是重新序列化后的 payload。
                actionContent.set(data);
            } else if ("tool.full".equals(type)) {
                // 缓存指定工具的含义，供下次意图识别时提供上下文
                if (!toolMeanings.isEmpty()) {
                    cacheToolMeaning(sessionCacheKey, root);
                }
                // 检测 createRepairIssueAiDiag 工具调用，启动进度推送
                String shortName = extractToolShortName(root);
                if ("createRepairIssueAiDiag".equals(shortName)) {
                    String sid = responseSessionId.get() != null ? responseSessionId.get() : "";
                    startCreateRepairIssueAiDiag(emitter, progressRef, sid);
                }
                // stationAgent 返回结果提取 data，封装为 message.complete 提前推送前端并完成 SSE
                if ("stationAgent".equals(shortName)) {
                    String dataContent = handleStationAgentResult(emitter, root, responseSessionId, earlyComplete);
                    if (dataContent != null) {
                        completePayload.set(dataContent);
                    }
                }
                // 提取设备/摄像头id，推送给前端 useData 事件
                sendUseDataEvent(emitter, root);
            }
        } catch (Exception e) {
            // 解析仅用于会话缓存和旁路记录，不影响已经完成的流式透传。
            log.warn("解析 StreamChatV2 SSE data 失败，已保持原数据透传: {}", e.getMessage());
        }
    }

    /**
     * 过滤 Hermes 在正文末尾追加的 file-mutation verifier 提示。
     * <p>
     * Hermes 默认会在助手回复末尾追加形如
     * "⚠️ File-mutation verifier: ... Write denied ..." 的提示信息，对前端展示无意义。
     * 此处解析 SSE data JSON，递归清除任意字符串值中出现的 "File-mutation" 标记及其之后内容，
     * 同时去除标记前多余的换行/空白及 "⚠️" 警示符。未命中标记时原样透传。
     */
    private String stripFileMutationFooter(String data) {
        if (data == null || data.isEmpty() || !data.contains("File-mutation")) {
            return data;
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode cleaned = stripFileMutationInNode(root);
            // cleaned 与 root 引用相同时无需重新序列化
            if (cleaned == root) {
                return data;
            }
            log.debug("去除了 file-mutation verifier 提示");
            return objectMapper.writeValueAsString(cleaned);
        } catch (Exception e) {
            // 解析失败时保持原数据透传，不中断流式响应
            log.warn("过滤 file-mutation verifier 失败，已保持原数据透传: {}", e.getMessage());
            return data;
        }
    }

    /**
     * 递归处理节点：对象按字段、数组按元素递归清洗；字符串值则截断 "File-mutation" 标记及其后内容。
     * 对非字符串叶子节点（数字、布尔、null）不做处理。
     */
    private JsonNode stripFileMutationInNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return node;
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (!text.contains("File-mutation")) {
                return node;
            }
            int idx = text.indexOf("File-mutation");
            String head = text.substring(0, idx);
            // 去除标记前多余的换行、空白及 "⚠️" 警示符
            head = head.replaceAll("[\\s\\u26A0\\uFE0F]+$", "");
            return TextNode.valueOf(head);
        }
        if (node.isObject()) {
            boolean changed = false;
            List<Entry<String, JsonNode>> entries = new ArrayList<>(node.size());
            for (Entry<String, JsonNode> entry : node.properties()) {
                JsonNode original = entry.getValue();
                JsonNode cleaned = stripFileMutationInNode(original);
                if (cleaned != original) {
                    changed = true;
                }
                entries.add(Map.entry(entry.getKey(), cleaned));
            }
            if (!changed) {
                return node;
            }
            ObjectNode obj = objectMapper.createObjectNode();
            for (Entry<String, JsonNode> entry : entries) {
                obj.set(entry.getKey(), entry.getValue());
            }
            return obj;
        }
        if (node.isArray()) {
            boolean changed = false;
            List<JsonNode> items = new ArrayList<>(node.size());
            for (JsonNode item : node) {
                JsonNode original = item;
                JsonNode cleaned = stripFileMutationInNode(original);
                if (cleaned != original) {
                    changed = true;
                }
                items.add(cleaned);
            }
            if (!changed) {
                return node;
            }
            ArrayNode arr = objectMapper.createArrayNode();
            for (JsonNode item : items) {
                arr.add(item);
            }
            return arr;
        }
        return node;
    }

    /**
     * 过滤文本中标有 "技能来/技能进行" 等的技能标记段落，从触发词到 "技能来" 整体移除。
     * <p>
     * 例如：
     * <ul>
     *   <li>加载 `area-status-checker` 技能来查询 → 查询</li>
     *   <li>加载相关技能来查询 → 查询</li>
     *   <li>使用一些乱七八糟的内容技能来进行温度调节 → 温度调节</li>
     * </ul>
     */
    private String stripSkillName(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        Matcher matcher = SKILL_NAME_PATTERN.matcher(data);
        StringBuffer sb = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String matched = matcher.group();
            // 尝试从匹配文本中提取反引号内的技能名称，若有则精确打印
            int bt = matched.indexOf('`');
            if (bt >= 0) {
                int btEnd = matched.indexOf('`', bt + 1);
                if (btEnd > bt) {
                    log.debug("去除了技能名称`{}`", matched.substring(bt + 1, btEnd));
                } else {
                    log.debug("去除了技能标记");
                }
            } else {
                log.debug("去除了技能标记");
            }
            matcher.appendReplacement(sb, "");
        }
        if (found) {
            matcher.appendTail(sb);
            // 清理可能残留的多余空格
            return sb.toString().replaceAll("\\s{2,}", " ").trim();
        }
        return data;
    }

    /**
     * 判断文本是否全是英文：不含任何中文字符（CJK 统一表意文字）且至少包含一个英文字母。
     * 空白文本不视为纯英文。
     */
    private static boolean isAllEnglishText(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        boolean hasLetter = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                return false;
            }
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') {
                hasLetter = true;
            }
        }
        return hasLetter;
    }

    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            String data = objectMapper.createObjectNode()
                    .put("error", message == null ? "StreamChatV2 call failed" : message)
                    .toString();
            emitter.send(SseEmitter.event().name("error").data(data));
            // 错误后补发 complete 事件，让前端正常收尾并提示用户重试，避免页面无响应
            emitter.send(SseEmitter.event().name("complete").data("正在转为工作模式，请稍后重试"));
        } catch (Exception sendError) {
            log.debug("发送 StreamChatV2 错误事件失败", sendError);
        }
    }

    // ==================== 进度推送相关方法 ====================

    /**
     * 发送一条进度消息，SSE 事件名为 message.delta。
     */
    private void sendProgressMessage(SseEmitter emitter, String sessionId, String message) {
        try {
            Map<String, Object> progress = new HashMap<>();
            progress.put("type", "message.delta");
            progress.put("payload", message);
            progress.put("sessionId", sessionId != null ? sessionId : "");
            String json = objectMapper.writeValueAsString(progress);
            emitter.send(SseEmitter.event().name("message.delta").data(json));
        } catch (Exception e) {
            log.debug("发送进度消息失败: {}", e.getMessage());
        }
    }

    /**
     * 取消进度推送定时任务。
     */
    private void cancelProgressTask(AtomicReference<ScheduledFuture<?>> progressRef) {
        ScheduledFuture<?> future = progressRef.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 从 tool.full 事件中提取工具短名（如 mcp__iot_mcp__getSubareaState → getSubareaState）。
     */
    private String extractToolShortName(JsonNode root) {
        try {
            JsonNode payload = root.path("payload");
            JsonNode message = payload.path("message");
            JsonNode msgPayload = message.path("payload");
            JsonNode textNode = msgPayload.path("text");
            if (!textNode.isTextual()) return null;
            JsonNode textObj = objectMapper.readTree(textNode.asText());
            String toolName = textObj.path("tool_name").asText("");
            if (toolName.contains("__")) {
                return toolName.substring(toolName.lastIndexOf("__") + 2);
            }
            return toolName;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 启动 createRepairIssueAiDiag 进度推送：立即推送第一条，后续每5秒推送一条（不循环）。
     * 收到 message.complete 时由调用方取消。
     */
    private void startCreateRepairIssueAiDiag(SseEmitter emitter,
                                      AtomicReference<ScheduledFuture<?>> progressRef,
                                      String sessionId) {
        cancelProgressTask(progressRef);
        sendProgressMessage(emitter, sessionId, CREATE_REPAIR_ISSUE_AI_DIAG.get(0));
        AtomicInteger index = new AtomicInteger(1);
        ScheduledFuture<?> future = progressScheduler.scheduleAtFixedRate(() -> {
            try {
                int i = index.getAndIncrement();
                if (i >= CREATE_REPAIR_ISSUE_AI_DIAG.size()) {
                    cancelProgressTask(progressRef);
                    return;
                }
                sendProgressMessage(emitter, sessionId, CREATE_REPAIR_ISSUE_AI_DIAG.get(i));
            } catch (Exception e) {
                cancelProgressTask(progressRef);
            }
        }, 5, 5, TimeUnit.SECONDS);
        progressRef.set(future);
        log.debug("已启动 createRepairIssueAiDiag 进度推送, sessionId={}", sessionId);
    }

    // ==================== 进度推送相关方法结束 ====================

    private void dispose(AtomicReference<Disposable> subscriptionRef) {
        Disposable subscription = subscriptionRef.getAndSet(null);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    /** 当前活跃流式请求的连接句柄：前端 SSE emitter + Hermes 上游订阅 + 用户发送的消息内容 */
    private record ActiveStream(SseEmitter emitter, AtomicReference<Disposable> subscriptionRef, String message) {
    }

    /**
     * 中断上一次未结束的请求：由 Java 端主动关闭旧连接（上游订阅 + 前端 SSE）。
     * Hermes 侧同一时刻只处理一条请求、新请求自动打断旧请求，无需再主动 DELETE 会话。
     */
    private void interruptPreviousStream() {
        // 主动关闭上一次请求的连接
        ActiveStream previous = activeStream.getAndSet(null);
        String previousMessage = previous != null ? previous.message() : null;
        if (previous != null) {
            // 先取消上游订阅，避免关闭 emitter 后上游事件仍尝试写入
            dispose(previous.subscriptionRef());
            try {
                previous.emitter().complete();
                log.debug("已主动关闭上一次请求的前端 SSE 连接及 Hermes 上游订阅, 上一次用户发送内容: {}", previousMessage);
            } catch (Exception e) {
                // 旧连接可能已超时或异常关闭，忽略
                log.debug("关闭上一次请求的 SSE 连接时忽略异常: {}", e.getMessage());
            }
        }
    }

    private String resolveStreamV2Url(String agentHermesUrl) {
        if (!StringUtils.hasText(agentHermesUrl)) {
            return streamChatV2Url;
        }
        if (agentHermesUrl.endsWith("/api/chat/streamV2")) {
            return agentHermesUrl;
        }
        int apiPathIndex = agentHermesUrl.indexOf("/api/");
        if (apiPathIndex >= 0) {
            return agentHermesUrl.substring(0, apiPathIndex) + "/api/chat/streamV2";
        }
        return agentHermesUrl.replaceAll("/+$", "") + "/api/chat/streamV2";
    }

    /**
     * 解析 tool.full 事件，若 tool_name 在 toolMeanings 映射中则缓存对应的含义。
     * SSE JSON 结构: { type:"tool.full", payload:{ message:{ payload:{ text:"{\"tool_name\":\"...\", ...}" } } } }
     */
    private void cacheToolMeaning(SessionCacheKey cacheKey, JsonNode root) {
        try {
            JsonNode payload = root.path("payload");
            if (!payload.isObject()) return;
            JsonNode message = payload.path("message");
            if (!message.isObject()) return;
            JsonNode msgPayload = message.path("payload");
            if (!msgPayload.isObject()) return;
            JsonNode textNode = msgPayload.path("text");
            if (textNode.isNull() || !textNode.isTextual()) return;

            String textJson = textNode.asText();
            JsonNode textObj = objectMapper.readTree(textJson);
            String toolName = textObj.path("tool_name").asText("");

            // 取短名（mcp__iot_mcp__getSubareaState → getSubareaState）
            String shortName = toolName.contains("__")
                    ? toolName.substring(toolName.lastIndexOf("__") + 2)
                    : toolName;
            String meaning = toolMeanings.get(shortName);
            if (meaning == null) return;

            toolMeaningCache.put(cacheKey, meaning);
            log.debug("已缓存 tool 含义, key={}, tool={}, meaning={}", cacheKey, shortName, meaning);
        } catch (Exception e) {
            log.debug("缓存 tool 含义失败: {}", e.getMessage());
        }
    }

    /**
     * 从 tool.full 事件中提取 result 数据，解析出 subarea_cid / id / deviceId 等字段，
     * 封装成 useData 事件推送给前端。
     * SSE JSON 结构: { type:"tool.full", payload:{ message:{ payload:{ text:"{\"tool_name\":\"...\", \"result\":\"...\"}" } } } }
     * result 是一个 AjaxResultVO 的 JSON 字符串，其中 data 是数组。
     */
    private void sendUseDataEvent(SseEmitter emitter, JsonNode root) {
        try {
            JsonNode payload = root.path("payload");
            if (!payload.isObject()) return;
            JsonNode message = payload.path("message");
            if (!message.isObject()) return;
            JsonNode msgPayload = message.path("payload");
            if (!msgPayload.isObject()) return;
            JsonNode textNode = msgPayload.path("text");
            if (textNode.isNull() || !textNode.isTextual()) return;

            String textJson = textNode.asText();
            JsonNode textObj = objectMapper.readTree(textJson);
            String toolName = textObj.path("tool_name").asText("");

            // 取短名
            String shortName = toolName.contains("__")
                    ? toolName.substring(toolName.lastIndexOf("__") + 2)
                    : toolName;

            // 判断shortName是工具之一才继续 listCamera、getDeviceStatePage、getDeviceInfoWithPoints、getSubareaState、openCamera
            if(shortName.equals("listCamera")||shortName.equals("getDeviceStatePage")||shortName.equals("getDeviceInfoWithPoints")||shortName.equals("getSubareaState")||shortName.equals("openCamera")){


            // 解析 args（openCamera 用）
            JsonNode argsNode = textObj.path("args");

            // openCamera: 从 args 中提取 cameraId、subareaCid
            if ("openCamera".equals(shortName) && argsNode.isObject()) {
                String cameraId = argsNode.path("cameraId").asText("");
                String subareaCid = argsNode.path("subareaCid").asText("");
                if (cameraId.isEmpty() && subareaCid.isEmpty()) return;

                String sessionId = root.path("sessionId").asText("");
                Map<String, Object> useDataPayload = new HashMap<>();
                useDataPayload.put("subarea_cid", subareaCid);
                useDataPayload.put("type", "摄像头");
                useDataPayload.put("data_id", cameraId);

                Map<String, Object> useDataEvent = new HashMap<>();
                useDataEvent.put("type", "useData");
                useDataEvent.put("payload", useDataPayload);
                useDataEvent.put("sessionId", sessionId);

                String useDataJson = objectMapper.writeValueAsString(useDataEvent);
                emitter.send(SseEmitter.event().name("useData").data(useDataJson));
                log.debug("已推送 useData 事件, tool=openCamera, subareaCid={}, dataId={}", subareaCid, cameraId);
                return;
            }

            // 解析 result（可能是多层转义的 JSON 字符串或嵌套 result 对象，递归解析直到拿到 data 数组）
            JsonNode resultNode = textObj.path("result");
            if (resultNode == null || resultNode.isNull()) return;

            // 递归解析：处理文本转义和嵌套 result 字段，最多 10 层
            JsonNode resultObj = resultNode;
            for (int i = 0; i < 10; i++) {
                // 如果是文本，解析为 JSON
                if (resultObj.isTextual()) {
                    resultObj = objectMapper.readTree(resultObj.asText());
                    continue;
                }
                // 如果是对象且已有 data 数组，停止解析
                if (resultObj.isObject() && resultObj.path("data").isArray()) {
                    break;
                }
                // 没有 data 数组，尝试钻入嵌套的 result 字段
                JsonNode innerResult = resultObj.path("result");
                if (!innerResult.isMissingNode() && !innerResult.isNull()) {
                    resultObj = innerResult;
                    continue;
                }
                break;
            }
            if (!resultObj.isObject()) return;

            // getSubareaState 的 data 为对象结构 { subareaState, passengerFlow }，无法按 data[0] 数组方式提取：
            // 仅当本次调用指定了区域（args.subareaCid 非空）或结果仅含一个区域时，推送区域定位 useData；
            // 全站查询（多区域）不推送，避免误定位
            if ("getSubareaState".equals(shortName)) {
                String areaCid = argsNode.path("subareaCid").asText("");
                if (areaCid.isEmpty()) {
                    JsonNode subareaStateArr = resultObj.path("data").path("subareaState");
                    if (subareaStateArr.isArray() && subareaStateArr.size() == 1) {
                        areaCid = subareaStateArr.get(0).path("subarea_cid").asText("");
                    }
                }
                if (areaCid.isEmpty()) return;

                String areaSessionId = root.path("sessionId").asText("");
                Map<String, Object> areaPayload = new HashMap<>();
                areaPayload.put("subarea_cid", areaCid);
                areaPayload.put("type", "区域");
                areaPayload.put("data_id", areaCid);

                Map<String, Object> areaEvent = new HashMap<>();
                areaEvent.put("type", "useData");
                areaEvent.put("payload", areaPayload);
                areaEvent.put("sessionId", areaSessionId);

                String areaJson = objectMapper.writeValueAsString(areaEvent);
                emitter.send(SseEmitter.event().name("useData").data(areaJson));
                log.debug("已推送 useData 事件, tool=getSubareaState, subareaCid={}", areaCid);
                return;
            }

            // 取 data 数组
            JsonNode dataNode = resultObj.path("data");
            if (dataNode == null || !dataNode.isArray() || dataNode.isEmpty()) return;

            // 第一条数据
            JsonNode firstItem = dataNode.get(0);
            String subareaCid = firstItem.path("subarea_cid").asText(firstItem.path("subareaCid").asText(""));
            String dataId;

            // 根据工具类型确定 type 和 data_id
            String useType;
            if ("listCamera".equals(shortName)) {
                useType = "摄像头";
                dataId = firstItem.path("id").asText("");
            } else if ("getDeviceStatePage".equals(shortName)) {
                useType = "机电设备";
                dataId = firstItem.path("id").asText("");
            } else if ("getDeviceInfoWithPoints".equals(shortName)) {
                useType = "机电设备";
                dataId = firstItem.path("deviceId").asText("");
                if (subareaCid.isEmpty()) {
                    subareaCid = firstItem.path("subareaCid").asText("");
                }
            } else {
                return;
            }

            if (subareaCid.isEmpty() && dataId.isEmpty()) return;

            // 获取 sessionId
            String sessionId = root.path("sessionId").asText("");

            // 构建 useData 事件
            Map<String, Object> useDataPayload = new HashMap<>();
            useDataPayload.put("subarea_cid", subareaCid);
            useDataPayload.put("type", useType);
            useDataPayload.put("data_id", dataId);

            Map<String, Object> useDataEvent = new HashMap<>();
            useDataEvent.put("type", "useData");
            useDataEvent.put("payload", useDataPayload);
            useDataEvent.put("sessionId", sessionId);

            String useDataJson = objectMapper.writeValueAsString(useDataEvent);
            emitter.send(SseEmitter.event().name("useData").data(useDataJson));
            log.debug("已推送 useData 事件, tool={}, subareaCid={}, dataId={}", shortName, subareaCid, dataId);
            }
        } catch (Exception e) {
            log.debug("发送 useData 事件失败: {}", e.getMessage());
        }
    }

    /**
     * 处理 stationAgent 的 tool.full 事件：从多层嵌套的 result 中提取 data 字符串，
     * 封装为 message.complete 事件推送给前端并完成 SSE，不断开 Hermes 上游订阅。
     * <p>
     * SSE JSON 结构:
     * { type:"tool.full", payload:{ message:{ payload:{ text:"{\"tool_name\":\"mcp__iot_mcp__stationAgent\", \"result\":\"...\"}" } } } }
     * result 经过多层转义，递归解析直到拿到包含 data 字符串的对象。
     *
     * @return 提取出的 data 内容，失败返回 null
     */
    private String handleStationAgentResult(
            SseEmitter emitter,
            JsonNode root,
            AtomicReference<String> responseSessionId,
            AtomicBoolean earlyComplete) {
        try {
            // 1. 从 tool.full 事件中解析 text JSON
            JsonNode payload = root.path("payload");
            JsonNode message = payload.path("message");
            JsonNode msgPayload = message.path("payload");
            JsonNode textNode = msgPayload.path("text");
            if (!textNode.isTextual()) return null;

            JsonNode textObj = objectMapper.readTree(textNode.asText());

            // 2. 递归解析 result，直到拿到包含 data 字符串的对象
            JsonNode resultNode = textObj.path("result");
            if (resultNode.isMissingNode() || resultNode.isNull()) return null;

            JsonNode resultObj = resultNode;
            for (int i = 0; i < 10; i++) {
                if (resultObj.isTextual()) {
                    resultObj = objectMapper.readTree(resultObj.asText());
                    continue;
                }
                if (resultObj.isObject() && resultObj.has("data") && resultObj.path("data").isTextual()) {
                    break;
                }
                JsonNode innerResult = resultObj.path("result");
                if (!innerResult.isMissingNode() && !innerResult.isNull()) {
                    resultObj = innerResult;
                    continue;
                }
                break;
            }

            if (!resultObj.isObject()) return null;
            JsonNode dataNode = resultObj.path("data");
            if (dataNode.isMissingNode() || dataNode.isNull() || !dataNode.isTextual()) return null;

            String dataContent = dataNode.asText();
            String sessionId = root.path("sessionId").asText(
                    responseSessionId.get() != null ? responseSessionId.get() : "");

            // 3. 封装为 message.complete 事件推送给前端
            Map<String, Object> completeEvent = new HashMap<>();
            completeEvent.put("type", "message.complete");
            completeEvent.put("payload", dataContent);
            completeEvent.put("sessionId", sessionId);
            String jsonData = objectMapper.writeValueAsString(completeEvent);
            emitter.send(SseEmitter.event().name("message.complete").data(jsonData));

            // 4. 标记提前完成并关闭前端 SSE（保留 Hermes 上游订阅不断开）
            earlyComplete.set(true);
            emitter.complete();
            log.debug("stationAgent 结果已推送前端并完成 SSE, sessionId={}", sessionId);
            return dataContent;
        } catch (Exception e) {
            log.warn("处理 stationAgent tool.full 事件失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取指定 (agentId, pcId) 缓存的工具含义（如"区域列表"），用于意图识别 prompt。
     */
    public String getToolMeaning(String agentId, String pcId) {
        SessionCacheKey key = resolveSessionCacheKey(agentId, pcId);
        String cached = toolMeaningCache.get(key);
        return cached != null ? cached : "";
    }

    private record SessionCacheKey(String agentId, String pcId) {
    }

    /**
     * 向当前活跃的前端 SSE 连接推送 useData 事件，供外部（如 WebSocket 监听器）调用。
     *
     * @param payload useData 事件的 payload 内容
     * @return true=推送成功，false=无活跃连接或推送失败
     */
    public boolean pushUseDataToActiveStream(Map<String, Object> payload) {
        ActiveStream current = activeStream.get();
        if (current == null) {
            log.debug("无活跃 SSE 连接，跳过推送 useData 事件");
            return false;
        }
        try {
            Map<String, Object> useDataEvent = new HashMap<>();
            useDataEvent.put("type", "useData");
            useDataEvent.put("payload", payload);
            useDataEvent.put("sessionId", activeSessionId.get() != null ? activeSessionId.get() : "");
            String useDataJson = objectMapper.writeValueAsString(useDataEvent);
            current.emitter().send(SseEmitter.event().name("useData").data(useDataJson));
            log.debug("外部推送 useData 事件成功: {}", payload);
            return true;
        } catch (Exception e) {
            log.warn("外部推送 useData 事件失败: {}", e.getMessage());
            return false;
        }
    }
}
