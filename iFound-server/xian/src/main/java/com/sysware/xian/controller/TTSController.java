package com.sysware.xian.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sysware.xian.domain.*;
import com.sysware.xian.mapper.SpeakerPermissionMapper;
import com.sysware.xian.service.CachedSpeakerPermissionService;
import com.sysware.xian.service.ReportService;
import com.sysware.xian.service.StreamChatV2Service;
import com.sysware.xian.service.TtsConversationService;
import com.sysware.xian.service.PresentationChatService;
import com.sysware.xian.util.MyUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.validation.Valid;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "语音接口", description = "语音接口")
public class TTSController {
    private final PresentationChatService presentationChatService;
    private final TtsConversationService ttsConversationService;
    private final StreamChatV2Service streamChatV2Service;
    private final SpeakerPermissionMapper speakerPermissionMapper;
    private final ChatClient.Builder chatClientBuilder;
    private final ReportService reportService;
    private final CachedSpeakerPermissionService cachedSpeakerPermissionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 用户对话responseId缓存 key: requesterName, value: responseId
    private static final ConcurrentHashMap<String, String> responseIdCache = new ConcurrentHashMap<>();

    /**
     * SSE过期时间
     */
    public static final Long SSE_TIMEOUT = 600000L;

    /**
     * 清理所有StreamChatV2 session缓存和responseId缓存（由 ScheduledService 定时调用）
     */
    public void scheduledClearAllCaches() {
        log.debug("开始定时清理所有TTS缓存...");
        try {
            int sessionCount = streamChatV2Service.clearAllSessionCaches();
            int responseCount = responseIdCache.size();
            responseIdCache.clear();
            log.debug("TTS缓存清理完成：session缓存 {} 条，responseId缓存 {} 条", sessionCount, responseCount);
        } catch (Exception e) {
            log.error("定时清理TTS缓存失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 调用 Hermes StreamChatV2。sessionKey 由服务端按照 userName 自动缓存复用。
     */
    @PostMapping(value = "/tts/chat/streamV2", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "StreamChatV2流式对话", description = "原样透传StreamChatV2事件，按pcId自动复用sessionKey并异步记录最终数据")
    public SseEmitter streamChatV2(@RequestBody @jakarta.validation.Valid StreamChatV2Request request) {
        long start = System.currentTimeMillis();
        log.debug("===== /tts/chat/streamV2 开始 =====" + request.getMessage());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        if (request == null
                || request.getMessage() == null || request.getMessage().isBlank()
                || request.getUserName() == null || request.getUserName().isBlank()
                || request.getUserId() == null || request.getUserId().isBlank()
                || request.getPcId() == null || request.getPcId().isBlank()) {
            log.debug("[步骤1/5] 参数校验不通过, 耗时={}ms", System.currentTimeMillis() - start);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"error\":\"message, userName, userId and pcId are required\"}"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }
        log.debug("[步骤1/5] 参数校验通过, 耗时={}ms", System.currentTimeMillis() - start);

        String userName = request.getUserName().trim();
        String userId = request.getUserId().trim();
        String pcId = request.getPcId().trim();
        String message = request.getMessage();

        // 步骤2：用户信息前缀
        if (streamChatV2Service.shouldPrependUserInfo("commander", userName)) {
            String userPrefix = String.format("用户信息：用户名称：%s，用户id：%s。", userName, userId);
            message = "严格按照技能执行用户说的所有任务。首先：" + message + "。" + userPrefix;
            log.debug("[步骤2/5] 用户 {} 首次接入，已拼接用户信息前缀, 耗时={}ms", userName, System.currentTimeMillis() - start);
        } else {
            log.debug("[步骤2/5] 用户 {} 已有会话，跳过前缀拼接, 耗时={}ms", userName, System.currentTimeMillis() - start);
        }

        // 步骤3：统一意图识别（优先关键词匹配，走缓存）
        int intent;
        if ((message.contains("查看") || message.contains("生成")) && (message.contains("报表") || message.contains("报告"))) {
            intent = 3;
            log.debug("[步骤3/5] 关键词匹配意图=3(报表), 耗时={}ms", System.currentTimeMillis() - start);
        } else {
            String cachedToolMeaning = streamChatV2Service.getToolMeaning("commander", pcId);
            intent = classifyIntent(message, cachedToolMeaning);
            log.debug("[步骤3/5] AI意图识别结果={}, 耗时={}ms", intent, System.currentTimeMillis() - start);
        }

        if (intent == 2) {
            // 步骤4a：设备控制权限校验
            log.debug("[步骤4a/5] 设备控制意图，开始权限校验, 耗时={}ms", System.currentTimeMillis() - start);
            SpeakerPermission permission = cachedSpeakerPermissionService.getByName(userName);
            if (permission == null || !Boolean.TRUE.equals(permission.getCanYk())) {
                log.debug("[步骤4a/5] 用户 {} 无设备控制权限, 耗时={}ms", userName, System.currentTimeMillis() - start);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":\"抱歉，您没有控制权限\"}"));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
                return emitter;
            }
            log.debug("[步骤4a/5] 用户 {} 有设备控制权限, 耗时={}ms", userName, System.currentTimeMillis() - start);
        } else if (intent == 3) {
            // 步骤4b：报表意图 —— 复用定时任务(每日凌晨1点)生成并落库的最新运行报告，避免实时生成的长耗时
            log.debug("[步骤4b/5] 报表意图，读取最新运行报告, 耗时={}ms", System.currentTimeMillis() - start);
            final String reportMessage = message;
            final long reportStart = start;
            // 独立线程执行，控制器立即返回 emitter 建立 async context
            java.util.concurrent.ExecutorService reportExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
            reportExecutor.submit(() -> {
                try {
                    // 1. 优先取最新一次已落库的运行报告（近实时）
                    String reportContent = reportService.getLatestReportContent();
                    // 2. 无历史报告时兜底实时生成（如首次运行、定时任务尚未执行）
                    if (reportContent == null || reportContent.isBlank()) {
                        log.debug("[步骤4b/5] 暂无已落库的运行报告，回退实时生成");
                        reportContent = reportService.reportChat(reportMessage, userName, userId, null);
                    }
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("type", "message.complete");
                    payload.put("payload", reportContent);
                    payload.put("sessionId", "");
                    payload.put("reportName", "站房运行报表");
                    String jsonData = objectMapper.writeValueAsString(payload);
                    emitter.send(SseEmitter.event().name("message.complete").data(jsonData));
                    emitter.complete();
                    log.debug("[步骤5/5] 运行报告返回完成, 总耗时={}ms", System.currentTimeMillis() - reportStart);
                } catch (Exception e) {
                    log.error("[步骤4b/5] 运行报告获取失败: {}, 耗时={}ms", e.getMessage(), System.currentTimeMillis() - reportStart, e);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data("{\"error\":\"报表生成失败\"}"));
                    } catch (Exception ex) {
                        // ignore
                    }
                    emitter.completeWithError(e);
                } finally {
                    reportExecutor.shutdown();
                }
            });
            // 立即返回 emitter，Spring 建立 async context 后事件才能实时推送
            return emitter;
        }

        // 步骤5：正常流式对话
        log.debug("[步骤5/5] 开始流式对话, 耗时={}ms", System.currentTimeMillis() - start);
        streamChatV2Service.stream(
                message,
                userName,
                userId,
                pcId,
                emitter
        );
        log.debug("===== /tts/chat/streamV2 结束, 总耗时={}ms =====", System.currentTimeMillis() - start);
        return emitter;
    }

    @PostMapping("/tts/chat/presentation/stream")
    @Operation(summary = "专用于演示的streamChat的流式对话接口", description = "由ai判断并路由到相应的请求中，并让java直接调用接口返回")
    public SseEmitter presentationStreamChat(@RequestBody StreamChatV2Request request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        // 演示分支包含多路接口聚合查询（报表聚合+环境监测等），独立线程执行，控制器立即返回 emitter 建立 async context，
        // 使"正在检索分析"等即时提示能在查询完成前推送到前端，避免首字节等待整个查询耗时
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                presentationChatService.streamChat(emitter, request);
            } catch (Exception e) {
                log.error("演示模式对话处理异常: {}", request.getMessage(), e);
                // 即使异常（如 Gateway WebSocket 连接失败等），也推送正常的 message.complete 数据，避免前端收不到任何内容
                try {
                    com.sysware.xian.domain.StreamChatResponseV2<Object> fallback = com.sysware.xian.domain.StreamChatResponseV2.builder()
                            .type("message.complete")
                            .payload("演示模式对话处理异常，请稍后重试")
                            .build();
                    emitter.send(SseEmitter.event().name("message.complete").data(fallback));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            } finally {
                executor.shutdown();
            }
        });
        return emitter;
    }


    /**
     * 通用 StreamChatV2：根据用户意图从 agent_config 中选择最佳 Agent 后进行流式调用。
     */
    @PostMapping(value = "/tts/chat/common/streamV2", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "通用StreamChatV2流式对话", description = "根据用户意图路由最佳Agent，原样透传SSE并异步记录最终数据")
    public SseEmitter commonStreamChatV2(@RequestBody @jakarta.validation.Valid StreamChatV2Request request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // 校验设备控制权限
        String userName = request.getUserName() != null ? request.getUserName().trim() : "";
        String message = request.getMessage();
        if (!userName.isBlank() && message != null && !message.isBlank()) {
            boolean isDeviceControl = checkDeviceControlIntent(message);
            if (isDeviceControl) {
                SpeakerPermission permission = speakerPermissionMapper.selectByName(userName);
                if (permission == null || !Boolean.TRUE.equals(permission.getCanYk())) {
                    log.debug("用户 {} 尝试设备控制但无权限", userName);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data("{\"error\":\"抱歉，您没有控制权限\"}"));
                        emitter.complete();
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                    return emitter;
                }
                log.debug("用户 {} 有设备控制权限，继续执行", userName);
            }
        }

        AgentConfig matchedAgent = ttsConversationService.routeToBestAgent(request.getMessage());
        String agentId = matchedAgent != null && matchedAgent.getAgentId() != null
                ? matchedAgent.getAgentId()
                : "commander";
        String hermesUrl = matchedAgent != null ? matchedAgent.getHermesUrl() : null;

        log.debug("通用StreamChatV2路由结果: userName={}, agentId={}, hermesUrl={}",
                request.getUserName(), agentId, hermesUrl);
        String pcId = request.getPcId() != null ? request.getPcId().trim() : "";
        streamChatV2Service.streamWithAgent(
                request.getMessage(),
                request.getUserName().trim(),
                request.getUserId().trim(),
                pcId,
                agentId,
                hermesUrl,
                emitter
        );
        return emitter;
    }

    /**
     * 【测试用】手动设置 StreamChatV2 sessionKey 缓存，用于模拟 sessionKey 过期场景。
     * 设置一个无效的 sessionKey 后再调用 /tts/chat/streamV2，即可验证过期自动重试逻辑。
     */
    @PostMapping("/tts/chat/streamV2/cache")
    @Operation(summary = "【测试】设置StreamChatV2 sessionKey缓存", description = "手动设置指定pcId的sessionKey，用于模拟会话key过期场景测试自动重试")
    public Map<String, Object> setStreamChatV2SessionCache(
            @Parameter(description = "来源终端标识，与/tts/chat/streamV2请求中的pcId一致，用于定位会话缓存键", required = true, example = "test-pc-01")
            @RequestParam String pcId,
            @Parameter(description = "要写入缓存的会话key；传入无效值可模拟sessionKey过期场景", required = true, example = "expired-fake-key-123")
            @RequestParam String sessionKey,
            @Parameter(description = "Agent标识，不传默认commander", example = "commander")
            @RequestParam(required = false, defaultValue = "commander") String agentId
    ) {
        Map<String, Object> data = streamChatV2Service.setSessionKeyForTest(agentId, pcId.trim(), sessionKey);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "sessionKey已设置");
        result.put("data", data);
        return result;
    }

    /**
     * 清除 StreamChatV2 sessionKey 缓存。pcId 为空时清除全部。
     */
    @DeleteMapping("/tts/chat/streamV2/cache")
    @Operation(summary = "清除StreamChatV2 sessionKey缓存", description = "pcId有值时清除指定来源的会话；为空或不传时清除全部")
    public Map<String, Object> clearStreamChatV2SessionCache(
            @Parameter(description = "来源终端标识；有值时仅清除该终端的会话缓存，为空或不传时清除全部", example = "test-pc-01")
            @RequestParam(required = false, defaultValue = "") String pcId
    ) {
        Map<String, Object> data = new HashMap<>();
        if (pcId == null || pcId.isBlank()) {
            int clearedCount = streamChatV2Service.clearAllSessionCaches();
            data.put("scope", "all");
            data.put("cleared", clearedCount > 0);
            data.put("clearedCount", clearedCount);
        } else {
            String normalizedPcId = pcId.trim();
            int clearedCount = streamChatV2Service.clearSessionCache(normalizedPcId);
            data.put("scope", "pc");
            data.put("pcId", normalizedPcId);
            data.put("cleared", clearedCount > 0);
            data.put("clearedCount", clearedCount);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "缓存已清除");
        result.put("data", data);
        return result;
    }


    /**
     * 清除responseId缓存
     */
    @DeleteMapping("/tts/cache")
    @Operation(summary = "清除TTS responseId缓存", description = "清除所有用户的responseId缓存，用于重置对话链")
    public Map<String, Object> clearResponseIdCache() {
        int size = responseIdCache.size();
        responseIdCache.clear();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "缓存已清除");
        result.put("data", Map.of("clearedCount", size));
        log.debug("已清除 responseIdCache，共 {} 条记录", size);
        return result;
    }


    /**
     * TTS对话中转接口
     * body:{requester_name:"语音操作用户名",input:"","previous_response_id": "resp_abc123"}
     * return
     * {
     * "id": "resp_81b849561ea34c6f9cfac3cb19b4",
     * "status": "completed",
     * "output": "你想控制哪个设备？",
     * "model": "hermes-agent",
     * "created_at": 1781836087
     * }
     */

    @PostMapping("/tts/chat")
    @Operation(summary = "TTS对话,调用hermes", description = "调用TTS接口并存储对话记录，支持设备控制意图识别")
    public Map<String, Object> ttsChat(@RequestBody @Valid TtsChatRequest request) {
        long startTime = System.currentTimeMillis();
        log.debug(" -----------------语音对话,调用hermes ----------------------");
        log.debug(" 1. 用户语音指令下发  {} ：{}（耗时 {}ms）", LocalDateTime.now(), request.getInput(), System.currentTimeMillis() - startTime);
        String input = request.getInput();
        String requesterName = request.getRequesterName();
        String previousResponseId = request.getPreviousResponseId();//上一次的数据返回id
        // 从缓存获取previousResponseId
        if (MyUtil.isNotEmpty(responseIdCache.get(request.getRequesterName()))) {
            previousResponseId = responseIdCache.get(request.getRequesterName());
        } else {
            input = input + "。使用技能";
        }

        // 使用ChatClient判断是否是设备控制意图
        long step1Time = System.currentTimeMillis();
        boolean isDeviceControl = checkDeviceControlIntent(input);
        log.debug(" 2. 使用java判断是否是设备控制意图  {} ：{}（耗时 {}ms）", LocalDateTime.now(), isDeviceControl, System.currentTimeMillis() - step1Time);

        if (isDeviceControl) {
            // 设备控制意图，检查用户权限
            long step2Time = System.currentTimeMillis();
            SpeakerPermission permission = speakerPermissionMapper.selectByName(requesterName);
            if (permission == null || !Boolean.TRUE.equals(permission.getCanYk())) {
                // 没有控制权限，返回无权限提示
                log.debug("用户 {} 尝试设备控制但无权限（耗时 {}ms）", requesterName, System.currentTimeMillis() - step2Time);
                return buildNoPermissionResponse(previousResponseId, requesterName, input);
            }
            // 有权限，继续执行原有TTS流程
            log.debug("用户 {} 有设备控制权限，继续执行（耗时 {}ms）", requesterName, System.currentTimeMillis() - step2Time);
        }

        long step3Time = System.currentTimeMillis();
        log.debug(" 3. 设备控制意图，检查用户权限  {}（耗时 {}ms）", LocalDateTime.now(), System.currentTimeMillis() - step3Time);
        // 普通对话意图，走原有TTS流程
        input = input + "";
        Map<String, Object> response = ttsConversationService.callTtsAndSave(input, previousResponseId, requesterName);

        // 缓存responseId
        String responseId = (String) response.get("id");
        if (responseId != null && !responseId.isEmpty()) {
            responseIdCache.put(requesterName, responseId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "操作成功");
        result.put("data", response);
        log.debug(" 4. hermes调用  {}：{}（耗时 {}ms，总耗时 {}ms）", LocalDateTime.now(), response, System.currentTimeMillis() - step3Time, System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * TTS通用对话接口（智能路由到最佳Agent）
     * body:{requester_name:"语音操作用户名",input:"","previous_response_id": "resp_abc123"}
     * return
     * {
     * "id": "resp_81b849561ea34c6f9cfac3cb19b4",
     * "status": "completed",
     * "output": "你想控制哪个设备？",
     * "model": "hermes-agent",
     * "created_at": 1781836087
     * }
     */
    @PostMapping("/tts/chat/common")
    @Operation(summary = "TTS通用对话,智能路由Agent", description = "根据用户意图智能路由到最佳Agent，调用对应Hermes接口并存储对话记录，支持设备控制意图识别")
    public Map<String, Object> ttsCommonChat(@RequestBody TtsChatRequest request) {
        String input = request.getInput();
        String previousResponseId = request.getPreviousResponseId();
        String requesterName = request.getRequesterName();

        // 使用ChatClient判断是否是设备控制意图
        boolean isDeviceControl = checkDeviceControlIntent(input);

        if (isDeviceControl) {
            // 设备控制意图，检查用户权限
            SpeakerPermission permission = speakerPermissionMapper.selectByName(requesterName);
            if (permission == null || !Boolean.TRUE.equals(permission.getCanYk())) {
                // 没有控制权限，返回无权限提示
                log.debug("用户 {} 尝试设备控制但无权限", requesterName);
                return buildNoPermissionResponse(previousResponseId, requesterName, input);
            }
            // 有权限，继续执行原有TTS流程
            log.debug("用户 {} 有设备控制权限，继续执行", requesterName);
        }

        // 智能路由到最佳Agent并调用其Hermes接口
        input = input + "";
        Map<String, Object> response = ttsConversationService.callTtsWithAgentRouting(input, previousResponseId, requesterName);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "操作成功");
        result.put("data", response);
        return result;
    }


    /**
     * TTS流式对话接口
     * 返回SSE流式响应，同时保存完整对话记录到数据库
     */
    @PostMapping(value = "/tts/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "TTS流式对话,调用hermes", description = "调用TTS流式接口并存储对话记录，支持设备控制意图识别，返回SSE流式响应")
    public SseEmitter ttsStreamChat(@RequestBody TtsStreamChatRequest request) {
        String input = request.getMessage();
        String previousResponseId = request.getPreviousResponseId();
        String requesterName = request.getRequesterName();
        String sessionKey = request.getSessionKey();

        // 创建SSE发射器，设置超时时间为5分钟
        SseEmitter emitter = new SseEmitter(300000L);

        // 设置超时回调
        emitter.onTimeout(() -> {
            log.warn("TTS流式连接超时");
        });

        // 设置完成回调
        emitter.onCompletion(() -> {
            log.debug("TTS流式连接关闭");
        });

        // 设置错误回调
        emitter.onError(e -> {
            log.warn("TTS流式连接错误: {}", e.getMessage());
        });

        // 使用ChatClient判断是否是设备控制意图
        boolean isDeviceControl = checkDeviceControlIntent(input);

        if (isDeviceControl) {
            // 设备控制意图，检查用户权限
            SpeakerPermission permission = speakerPermissionMapper.selectByName(requesterName);
            if (permission == null || !Boolean.TRUE.equals(permission.getCanYk())) {
                // 没有控制权限，返回无权限提示
                log.debug("用户 {} 尝试设备控制但无权限", requesterName);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":\"抱歉，您没有控制权限\"}"));
                    emitter.complete();
                } catch (Exception ex) {
                    log.error("发送错误事件失败: {}", ex.getMessage());
                }
                return emitter;
            }
            // 有权限，继续执行流式TTS流程
            log.debug("用户 {} 有设备控制权限，继续执行", requesterName);
        }

        // 普通对话意图，走流式TTS流程
        input = input + "。总结成一句话。500字以内";
        ttsConversationService.streamCallTtsAndSave(input, previousResponseId, requesterName, sessionKey, emitter);

        return emitter;
    }

    /**
     * 查询对话记录
     */
    @GetMapping("/tts/records")
    @Operation(summary = "查询TTS对话记录", description = "分页查询对话记录，支持时间段筛选")
    public Map<String, Object> queryRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String start_time,
            @RequestParam(required = false) String end_time) {

        Page<TtsConversation> records = ttsConversationService.queryRecords(page, size, start_time, end_time);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "操作成功");
        result.put("data", records);
        return result;
    }

    /**
     * 删除单条对话记录
     */
    @DeleteMapping("/tts/records/{id}")
    @Operation(summary = "删除TTS对话记录", description = "根据ID删除对话记录")
    public Map<String, Object> deleteRecord(@PathVariable Long id) {
        boolean success = ttsConversationService.deleteRecord(id);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", success ? "删除成功" : "删除失败");
        return result;
    }

    /**
     * 批量删除指定时间之前的记录
     */
    @DeleteMapping("/tts/records/batch")
    @Operation(summary = "批量删除TTS对话记录", description = "删除指定时间之前的记录，支持相对时间如7d/30d或绝对时间")
    public Map<String, Object> deleteRecordsBefore(@RequestParam String before_time) {
        int count = ttsConversationService.deleteRecordsBefore(before_time);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "删除成功");
        result.put("data", count);
        return result;
    }

    /**
     * 使用ChatClient判断输入是否是设备控制意图
     */
    private boolean checkDeviceControlIntent(String input) {
        ChatClient chatClient = chatClientBuilder
                .defaultSystem("你是一个意图识别助手。判断用户输入是否是设备控制意图（如打开/关闭/启动/停止/设置空调、风机、灯光等设备，" +
                        "注意不校验区域和摄像头，例如打开/进入西安东站/站台层/地下层/出站层/站台层/高架候车层/旅服夹层/冷热源/出发层/某摄像头）。只回答'是'或'否'，不要解释。")
                .build();

        String result = chatClient.prompt()
                .user("用户输入: " + input + "\n这是设备控制意图吗？")
                .call()
                .content();

        return result != null && result.trim().equals("是");
    }

    /**
     * 统一意图三分类：1-普通意图 2-设备控制，3-报表报告
     */
    private int classifyIntent(String input, String cachedToolMeaning) {
        String contextPrompt = "";
        if (cachedToolMeaning != null && !cachedToolMeaning.isBlank()) {
            contextPrompt = "\n注：用户上一步查询返回的是【" + cachedToolMeaning + "】。\n";
        }
        ChatClient chatClient = chatClientBuilder
                .defaultSystem("你是一个意图分类助手。判断用户输入属于以下哪种意图：\n" +
                        "1 - 普通意图（如所有的查询、查看，打开摄像头，打开某个区域的摄像头，进入某区域，打开某区域，生成维修工单）\n" +
                        "2 - 设备控制意图（如打开/关闭/启动/停止/设置空调、风机、灯光等设备控制操作)。注意区域和摄像头不属于设备控制，例如打开/进入西安东站/站台层/地下层/出站层/站台层/高架候车层/旅服夹层/冷热源/出发层/某摄像头）\n" +
                        "只回答数字1或2，不要解释任何其他内容。")
                .build();

        String result = chatClient.prompt()
                .user("用户输入: " + input + contextPrompt + "\n请分类：")
                .call()
                .content();
        if (result != null && result.trim().equals("1")) return 1;
        if (result != null && result.trim().equals("2")) return 2;
        return 3;
    }

    /**
     * 构建无权限响应并更新数据库
     */
    private Map<String, Object> buildNoPermissionResponse(String previousResponseId, String requesterName, String input) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "completed");
        data.put("output", "抱歉，您没有控制权限");
        data.put("model", "jave-agent");
        data.put("created_at", System.currentTimeMillis() / 1000);
        // 更新TtsConversation数据库
        ttsConversationService.saveNoPermissionRecord(requesterName, input, previousResponseId, data);


        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "操作成功");
        result.put("data", data);
        return result;
    }

}

