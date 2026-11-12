package com.sysware.xian.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.domain.AiAnalysisResult;
import com.sysware.xian.swcloud.domain.*;
import com.sysware.xian.swcloud.fegin.MCPXClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * @Description: 应急调度服务
 * @Author: LiYanRu
 * @date: 2026-07-16
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private static final String LEADER_KEYWORD = "领导";
    /** 大屏应急预案使用的知识库chatId */
    private static final String SCREEN_CHAT_ID = "56a0ca68955711f18039c54d238aefed";
    /** 大屏应急预案落库类型标识 */
    public static final String SCREEN_RESULT_TYPE = "emergency-plan";

    private final MCPXClient mcpxClient;
    private final RosterService rosterService;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final AiAnalysisResultService aiAnalysisResultService;
    private final RestTemplate restTemplate;

    /** RagFlow API 地址 */
    @Value("${ai.ragflow-base-url:http://192.168.128.235:9380}")
    private String ragflowBaseUrl;

    /** RagFlow API Key（含 Bearer 前缀） */
    @Value("${ai.ragflow-api-key:Bearer ragflow-kMZ2nqsJd-ixeqGhRMJNx8Jvk8VQzI7CT4XTX0j6kMM}")
    private String ragflowApiKey;

    /** 通讯录：姓名 → 电话号码（从 contacts.json 加载） */
    private Map<String, String> contactsMap = Collections.emptyMap();

    /**
     * 大屏应急预案并发控制：question → Semaphore(1)
     * <p>
     * 每个问题同时只允许一个 AI 分析在跑。请求到达立刻返回 data=null，
     * AI 分析在后台异步执行，结果落库后前端轮询查询缓存。
     */
    private final Map<String, Semaphore> screenSemaphores = new ConcurrentHashMap<>();

    /**
     * 启动时从 classpath 加载 contacts.json，构建 姓名→联系方式 映射
     */
    @PostConstruct
    public void loadContacts() {
        try (InputStream is = new ClassPathResource("contacts.json").getInputStream()) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, String> map = new HashMap<>();
            for (Map<String, Object> item : list) {
                String name = strVal(item.get("姓名"));
                String phone = strVal(item.get("联系方式"));
                if (!name.isBlank() && !phone.isBlank()) {
                    map.put(name, phone);
                }
            }
            contactsMap = map;
            log.info("通讯录 contacts.json 加载成功，共 {} 条记录", map.size());
        } catch (Exception e) {
            log.warn("加载 contacts.json 失败，值班人员电话将无法填充: {}", e.getMessage());
            contactsMap = Collections.emptyMap();
        }
    }

    /**
     * 应急调度：获取应急预案答案 + 今日值班领导 → AI筛选最佳人选 → 组装结果
     *
     * @param request 调度请求
     * @return 调度结果
     */
    public DispatchResult dispatch(DispatchRequest request) {
        // 1. 调用应急预案知识库，获取结构化响应（含 answer + reference）
        KnowledgeResponse emergencyResp = queryEmergencyPlan(request.getQuestion());
        String emergencyAnswer = emergencyResp != null ? emergencyResp.answerText() : "";
        JsonNode reference = emergencyResp != null && emergencyResp.getData() != null
                ? emergencyResp.getData().getReference()
                : null;

        // 2. 获取今日值班人员，筛选值班领导
        List<Map<String, Object>> leaders = getTodayLeaders();

        // 3. AI 筛选最佳值班领导
        Map<String, Object> bestLeader = selectBestLeader(emergencyAnswer, leaders);

        // 4. 组装最终结果
        DispatchResult result = DispatchResult.builderWithNow()
                .operatorName(request.getOperatorName())
                .operatorId(request.getOperatorId())
                .emergencyAnswer(emergencyAnswer)
                .reference(reference)
                .dutyPersonId(bestLeader != null ? bestLeader.get("id") : null)
                .positionGroupName(bestLeader != null ? mapStr(bestLeader, "positionGroupName", "depname") : null)
                .dutyPersonName(bestLeader != null ? mapStr(bestLeader, "personName", "membername") : null)
                .build();

        log.debug("应急调度完成: operator={}, 应急预案={}, 值班领导={}",
                request.getOperatorName(),
                emergencyAnswer.substring(0, Math.min(50, emergencyAnswer.length())),
                bestLeader != null ? mapStr(bestLeader, "personName", "membername") : "无");

        return result;
    }

    /**
     * 尝试获取大屏应急预案的信号量许可（非阻塞）
     * <p>
     * 由 Controller 调用：拿到许可返回 true（随后提交异步任务），拿不到返回 false（说明正在分析中）。
     *
     * @param lockKey 信号量 key（处理后的 question）
     * @return true=获取成功，false=正在分析中
     */
    public boolean tryAcquireScreenSemaphore(String lockKey) {
        Semaphore semaphore = screenSemaphores.computeIfAbsent(lockKey, k -> new Semaphore(1));
        return semaphore.tryAcquire();
    }

    /**
     * 释放大屏应急预案的信号量许可（异步提交失败时由 Controller 调用）
     *
     * @param lockKey 信号量 key
     */
    public void releaseScreenSemaphore(String lockKey) {
        Semaphore semaphore = screenSemaphores.get(lockKey);
        if (semaphore != null) {
            semaphore.release();
            screenSemaphores.remove(lockKey, semaphore);
        }
    }

    /**
     * 大屏应急预案异步入口：Controller 拿到 Semaphore 许可后调用此方法，
     * 在后台线程池中执行 AI 分析，完成后释放许可并清理信号量。
     * <p>
     * 注意：此方法通过 @Async 在独立线程池中执行，不阻塞 Controller 线程。
     * 无论成功还是异常，finally 中都会释放 Semaphore 许可并从 map 清理。
     *
     * @param request 调度请求
     * @param lockKey 信号量 map 中的 key（处理后的 question）
     */
    @Async("streamChatRecordExecutor")
    public void emergencyPlanScreenAsync(DispatchRequest request, String lockKey) {
        try {
            log.info("大屏应急预案异步分析开始: question={}", lockKey);
            emergencyPlanScreen(request);
            log.info("大屏应急预案异步分析完成: question={}", lockKey);
        } catch (Exception e) {
            log.error("大屏应急预案异步分析失败: question={}", lockKey, e);
        } finally {
            // 无论成功失败，释放许可并从 map 清理
            Semaphore semaphore = screenSemaphores.get(lockKey);
            if (semaphore != null) {
                semaphore.release();
                screenSemaphores.remove(lockKey, semaphore);
            }
        }
    }

    /**
     * 查询今天已缓存的大屏应急预案结果（供 Controller 在走信号量逻辑之前先查缓存）
     * <p>
     * 如果今天已经有相同 question + type 的分析记录，直接返回缓存结果，不需要再走 AI 分析。
     *
     * @param question 处理后的 question（已替换级别 + 追加"生成执行预案"）
     * @param type     处理模式（1/2/3，null 默认为 3）
     * @return 缓存结果，无缓存时返回 null
     */
    public EmergencyPlanScreenResult getCachedScreenResult(String question, Integer type) {
        int t = (type == null || (type != 2 && type != 3)) ? 3 : type;
        String remake = buildScreenRemake(question, t);
        try {
            LocalDateTime todayStart = LocalDateTime.now()
                    .withHour(0).withMinute(0).withSecond(0).withNano(0);
            AiAnalysisResult existing = aiAnalysisResultService.getOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiAnalysisResult>()
                            .eq(AiAnalysisResult::getType, SCREEN_RESULT_TYPE)
                            .eq(AiAnalysisResult::getRemake, remake)
//                            .ge(AiAnalysisResult::getCreateTime, todayStart)
                            .orderByDesc(AiAnalysisResult::getCreateTime)
                            .last("LIMIT 1"));
            if (existing != null && existing.getResultData() != null) {
                log.info("大屏应急预案命中缓存(Controller): remake={}, id={}", remake, existing.getId());
                return objectMapper.readValue(existing.getResultData(), EmergencyPlanScreenResult.class);
            }
        } catch (Exception e) {
            log.warn("大屏应急预案查询缓存失败(Controller): {}", e.getMessage());
        }
        return null;
    }

    /**
     * 大屏应急预案：获取应急预案答案 + 今日值班人员岗位匹配 → 填充人员名称和电话 → 落库
     * <p>
     * 支持三种处理模式（通过 request.type 指定）：
     * <ul>
     *   <li>type=1（默认）：AI完成人员匹配并格式化输出，将 answer + 值班人员列表交给 AI，
     *       由 AI 智能匹配岗位与人员，一步生成格式化结果</li>
     *   <li>type=2：关键词匹配 + AI格式化，先按 positionName/positionGroupName 关键词匹配值班人员，
     *       再交给 AI 提炼格式化</li>
     *   <li>type=3：使用 reference.chunks 的 content 原文让 AI 总结+格式化输出，
     *       从检索到的原始分块内容中提炼应急措施并匹配值班人员</li>
     * </ul>
     * 结果落库到 ai_analysis_result（type=emergency-plan）
     *
     * @param request 调度请求（question + operatorName + operatorId + type）
     * @return 大屏应急预案结果
     */
    public EmergencyPlanScreenResult emergencyPlanScreen(DispatchRequest request) {
        int type = (request.getType() == null || (request.getType() != 2 && request.getType() != 3)) ? 3 : request.getType();
        log.info("大屏应急预案: type={}, question={}", type, request.getQuestion());
        // 0. 优先从数据库查询今天最新一条 type=emergency-plan 且 remake 匹配的记录
        String remake = buildScreenRemake(request.getQuestion(), type);
        try {
            LocalDateTime todayStart = LocalDateTime.now()
                    .withHour(0).withMinute(0).withSecond(0).withNano(0);
            AiAnalysisResult existing = aiAnalysisResultService.getOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiAnalysisResult>()
                            .eq(AiAnalysisResult::getType, SCREEN_RESULT_TYPE)
                            .eq(AiAnalysisResult::getRemake, remake)
                            .ge(AiAnalysisResult::getCreateTime, todayStart)
                            .orderByDesc(AiAnalysisResult::getCreateTime)
                            .last("LIMIT 1"));
            if (existing != null && existing.getResultData() != null) {
                EmergencyPlanScreenResult cached = objectMapper.readValue(
                        existing.getResultData(), EmergencyPlanScreenResult.class);
                log.info("大屏应急预案命中缓存: remake={}, id={}", remake, existing.getId());
                return cached;
            }
        } catch (Exception e) {
            log.warn("大屏应急预案查询缓存失败，继续调AI: {}", e.getMessage());
        }

        // 1. 动态选择应急预案知识库：获取知识库列表 → AI根据用户问题选择最佳知识库 → 获取chatId
        String chatId = selectBestEmergencyChatId(request.getQuestion());
        log.info("大屏应急预案: 动态选择知识库 chatId={}", chatId);

        // 2. 调用选中的应急预案知识库，获取结构化响应
        KnowledgeResponse emergencyResp = queryEmergencyPlanWithChatId(request.getQuestion(), chatId);
        String emergencyAnswer = emergencyResp != null ? emergencyResp.answerText() : "";

        // 2. 获取今日全部值班人员
        List<Map<String, Object>> allStaff = getTodayAllStaff();

        String formattedAnswer;
        List<EmergencyPlanScreenResult.DutyPersonInfo> matchedPersons;

        if (type == 1) {
            // type=1: AI一步完成人员匹配+格式化输出（基于 answer）
            matchedPersons = buildAllDutyPersonInfos(allStaff);
            formattedAnswer = aiMatchAndFormat(emergencyAnswer, matchedPersons);
        } else if (type == 3) {
            // type=3: 使用 reference.chunks 的 content 原文让 AI 总结+格式化输出
            String chunksContent = extractChunksContent(emergencyResp);
            matchedPersons = buildAllDutyPersonInfos(allStaff);
            formattedAnswer = aiChunksMatchAndFormat(chunksContent, emergencyAnswer, matchedPersons, request);
        } else {
            // type=2: 关键词匹配 + AI格式化
            matchedPersons = matchDutyPersons(emergencyAnswer, allStaff);
            String enrichedAnswer = enrichAnswerWithDutyPersons(emergencyAnswer, matchedPersons);
            formattedAnswer = aiFormatEmergencyPlan(enrichedAnswer, matchedPersons);
        }

        // 追加来源文件（从 reference.doc_aggs 提取 doc_name）
        String sourceSuffix = buildSourceSuffix(emergencyResp);
        if (!sourceSuffix.isBlank()) {
            formattedAnswer = formattedAnswer + sourceSuffix;
        }

        // 组装结果
        EmergencyPlanScreenResult result = EmergencyPlanScreenResult.builderWithNow()
                .operatorName(request.getOperatorName())
                .operatorId(request.getOperatorId())
                .question(request.getQuestion())
                .emergencyAnswer(formattedAnswer)
                .dutyPersons(matchedPersons)
                .build();

        // 落库
        saveScreenResult(result, allStaff, type);

        log.debug("大屏应急预案完成: operator={}, 匹配值班人员数={}, answer长度={}",
                request.getOperatorName(), matchedPersons.size(), formattedAnswer.length());

        return result;
    }

    /**
     * 动态选择最佳应急预案知识库的 chatId
     * <p>
     * 流程：调用 RagFlow API 获取知识库列表 → 筛选 name 含"应急预案"的 → 
     * 让 AI 根据用户问题选择最匹配的知识库 → 返回其 id 作为 chatId
     * 如果选择失败（API不可用/AI无法判断），fallback 到默认 SCREEN_CHAT_ID
     *
     * @param question 用户提问内容（如"火灾爆炸应急处置"）
     * @return 选中的知识库 chatId
     */
    private String selectBestEmergencyChatId(String question) {
        try {
            // 1. 获取应急预案相关的知识库列表
            List<ChatInfo> chatList = fetchEmergencyChatList();
            if (chatList.isEmpty()) {
                log.warn("未获取到应急预案知识库列表，使用默认 chatId: {}", SCREEN_CHAT_ID);
                return SCREEN_CHAT_ID;
            }

            // 只有一个知识库时直接使用
            if (chatList.size() == 1) {
                String id = chatList.get(0).id();
                log.info("仅有一个应急预案知识库，直接使用: name={}, id={}", chatList.get(0).name(), id);
                return id;
            }

            // 2. 多个知识库时，让 AI 根据用户问题选择最佳匹配
            String selectedId = aiSelectChatId(question, chatList);
            if (selectedId != null && !selectedId.isBlank()) {
                return selectedId;
            }

            log.warn("AI选择知识库失败，使用默认 chatId: {}", SCREEN_CHAT_ID);
            return SCREEN_CHAT_ID;
        } catch (Exception e) {
            log.warn("动态选择知识库异常，使用默认 chatId: {}", e.getMessage());
            return SCREEN_CHAT_ID;
        }
    }

    /**
     * 调用 RagFlow API 获取知识库列表，筛选 name 包含"应急预案"的条目
     * <p>
     * GET {ragflowBaseUrl}/api/v1/chats?page=1&page_size=50
     * Authorization: {ragflowApiKey}
     *
     * @return 应急预案相关的知识库列表（id + name）
     */
    @SuppressWarnings("unchecked")
    private List<ChatInfo> fetchEmergencyChatList() {
        String url = ragflowBaseUrl + "/api/v1/chats?page=1&page_size=50";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", ragflowApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        log.debug("获取RagFlow知识库列表: url={}", url);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("RagFlow知识库列表返回空");
                return List.of();
            }
            JsonNode root = objectMapper.readTree(body);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                log.warn("RagFlow知识库列表返回错误: code={}", code);
                return List.of();
            }
            JsonNode chats = root.path("data").path("chats");
            if (!chats.isArray() || chats.isEmpty()) {
                log.warn("RagFlow知识库列表 data.chats 为空");
                return List.of();
            }

            List<ChatInfo> result = new ArrayList<>();
            for (JsonNode chat : chats) {
                String id = chat.path("id").asText("");
                String name = chat.path("name").asText("");
                if (!id.isBlank() && name.contains("应急预案")) {
                    result.add(new ChatInfo(id, name));
                }
            }
            log.info("筛选到应急预案知识库 {} 个: {}", result.size(),
                    result.stream().map(ChatInfo::name).toList());
            return result;
        } catch (Exception e) {
            log.error("调用RagFlow知识库列表API失败", e);
            return List.of();
        }
    }

    /**
     * 让 AI 根据用户问题从知识库列表中选择最匹配的一个
     *
     * @param question 用户提问内容
     * @param chatList 可选的知识库列表
     * @return 选中的知识库 id，AI无法判断时返回 null
     */
    private String aiSelectChatId(String question, List<ChatInfo> chatList) {
        StringBuilder listText = new StringBuilder();
        for (int i = 0; i < chatList.size(); i++) {
            listText.append(i + 1).append(". id=").append(chatList.get(i).id())
                    .append(", 名称=").append(chatList.get(i).name()).append("\n");
        }

        String prompt = """
                你是铁路车站应急管理系统。请根据用户的问题，从以下应急预案知识库列表中选择最匹配的一个知识库。

                【用户问题】
                %s

                【可选知识库列表】
                %s

                请只返回最匹配的知识库的 id 值（如 c62026d89c6211f18039c54d238aefed），不要返回任何其他内容。
                如果无法判断，返回列表中第一个知识库的 id。
                """.formatted(question, listText);

        try {
            String aiAnswer = chatClientBuilder.build().prompt()
                    .user(prompt)
                    .call()
                    .content();
            if (aiAnswer != null) {
                aiAnswer = aiAnswer.trim();
                // AI 可能返回带引号或多余文字，提取其中的 id 片段
                // id 通常是32位十六进制字符串
                if (aiAnswer.length() >= 32) {
                    // 尝试提取32+位十六进制字符串
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("[0-9a-f]{24,}").matcher(aiAnswer);
                    if (m.find()) {
                        String extracted = m.group();
                        // 验证提取的 id 在列表中存在
                        for (ChatInfo chat : chatList) {
                            if (chat.id().equals(extracted)) {
                                log.info("AI选择知识库: question='{}', selected name='{}', id='{}'",
                                        question, chat.name(), extracted);
                                return extracted;
                            }
                        }
                    }
                    // 如果 AI 返回的完整文本恰好等于某个 id
                    for (ChatInfo chat : chatList) {
                        if (chat.id().equals(aiAnswer)) {
                            log.info("AI选择知识库(直接匹配): name='{}', id='{}'",
                                    chat.name(), aiAnswer);
                            return aiAnswer;
                        }
                    }
                }
                log.warn("AI返回的知识库id无法匹配列表中的任何条目: {}", aiAnswer);
            }
        } catch (Exception e) {
            log.warn("AI选择知识库失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 使用指定chatId调用应急预案知识库
     */
    private KnowledgeResponse queryEmergencyPlanWithChatId(String question, String chatId) {
        KnowledgeResponse response = mcpxClient.opsKnowledge(
                new KnowledgeRequest(question, false, true, true, chatId));
        if (response == null || Boolean.FALSE.equals(response.getSuccess())) {
            log.warn("应急预案知识库调用失败(chatId={}): {}", chatId,
                    response != null ? response.getError() : "无响应");
        }
        return response;
    }

    /**
     * 获取今日全部值班人员列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getTodayAllStaff() {
        RosterPageRequest rosterReq = new RosterPageRequest();
        rosterReq.setPage(1);
        rosterReq.setLimit(100);
        rosterReq.fillDefaultDate();

        AjaxResultVO rosterResult = rosterService.queryPage(rosterReq);
        Object dataObj = rosterResult.get("data");
        if (!(dataObj instanceof List)) {
            log.warn("值班人员接口返回 data 不是列表");
            return List.of();
        }
        List<Map<String, Object>> allStaff = (List<Map<String, Object>>) dataObj;
        log.debug("今日值班总人数={}", allStaff.size());
        return allStaff;
    }

    /**
     * 根据answer中提到的岗位匹配今日值班人员
     * <p>
     * 遍历全部值班人员，若其 positionName 或 positionGroupName 出现在 answer 文本中，则视为匹配。
     *
     * @param answer  知识库返回的答案文本
     * @param allStaff 今日全部值班人员
     * @return 匹配到的值班人员信息列表
     */
    private List<EmergencyPlanScreenResult.DutyPersonInfo> matchDutyPersons(
            String answer, List<Map<String, Object>> allStaff) {
        List<EmergencyPlanScreenResult.DutyPersonInfo> matched = new ArrayList<>();
        if (answer == null || answer.isBlank() || allStaff.isEmpty()) {
            return matched;
        }
        for (Map<String, Object> person : allStaff) {
            // 新接口字段
            String membername = strVal(person.get("membername")).trim();
            String depname = strVal(person.get("depname"));
            String membertype = strVal(person.get("membertype"));
            // 兼容旧接口字段
            String positionName = strVal(person.get("positionName"));
            String positionGroupName = strVal(person.get("positionGroupName"));
            // 优先用旧接口字段，没有则用新接口字段
            String effectivePosition = !positionName.isBlank() ? positionName : membertype;
            String effectiveGroup = !positionGroupName.isBlank() ? positionGroupName : depname;

            boolean positionMatched = isPositionInAnswer(effectivePosition, answer)
                    || isPositionInAnswer(effectiveGroup, answer);
            if (positionMatched) {
                EmergencyPlanScreenResult.DutyPersonInfo info = EmergencyPlanScreenResult.DutyPersonInfo.builder()
                        .personName(!membername.isBlank() ? membername : strVal(person.get("personName")))
                        .phone(extractPhone(person))
                        .positionName(effectivePosition)
                        .positionGroupName(effectiveGroup)
                        .memberType(membertype)
                        .depname(depname)
                        .area(strVal(person.get("area")))
                        .build();
                matched.add(info);
                log.debug("匹配到值班人员: name={}, position={}, phone={}",
                        info.getPersonName(), info.getPositionName(), info.getPhone());
            }
        }
        return matched;
    }

    /**
     * 判断岗位名称是否出现在answer中（岗位名称至少2个字符才参与匹配，避免单字误匹配）
     */
    private boolean isPositionInAnswer(String position, String answer) {
        if (position == null || position.isBlank() || position.length() < 2) {
            return false;
        }
        return answer.contains(position);
    }

    /**
     * 从通讯录中按姓名查找电话号码
     */
    private String extractPhone(Map<String, Object> person) {
        // 新接口字段名 membername，兼容旧接口 personName
        String name = strVal(person.get("membername"));
        if (name.isBlank()) {
            name = strVal(person.get("personName"));
        }
        if (!name.isBlank()) {
            String phone = contactsMap.get(name.trim());
            if (phone != null && !phone.isBlank()) {
                return phone;
            }
        }
        return "";
    }

    /**
     * 在answer文中岗位名称出现的位置插入今日该岗位值班人员姓名和电话（原文不删减）
     * <p>
     * 例如 answer 含“立即向值班领导汇报”，值班领导为李四，电话13800138000，
     * 则替换为“立即向值班领导【李四，电话：13800138000】汇报”。
     * 同一岗位多人时用分号分隔：值班领导【李四，电话：13800138000；王五，电话：13900139000】
     *
     * @param answer      知识库返回的答案文本
     * @param dutyPersons 匹配到的值班人员列表
     * @return 在岗位名称后插入了人员信息的答案文本
     */
    private String enrichAnswerWithDutyPersons(
            String answer, List<EmergencyPlanScreenResult.DutyPersonInfo> dutyPersons) {
        if (dutyPersons == null || dutyPersons.isEmpty() || answer == null || answer.isBlank()) {
            return answer;
        }

        // 1. 按answer中实际出现的岗位名称分组人员（同岗位可能多人）
        Map<String, List<EmergencyPlanScreenResult.DutyPersonInfo>> positionToPersons = new LinkedHashMap<>();
        for (EmergencyPlanScreenResult.DutyPersonInfo p : dutyPersons) {
            // 优先用 positionName 匹配，其次用 positionGroupName
            String matchKey = null;
            if (isPositionInAnswer(p.getPositionName(), answer)) {
                matchKey = p.getPositionName();
            } else if (isPositionInAnswer(p.getPositionGroupName(), answer)) {
                matchKey = p.getPositionGroupName();
            }
            if (matchKey != null) {
                positionToPersons.computeIfAbsent(matchKey, k -> new ArrayList<>()).add(p);
            }
        }

        if (positionToPersons.isEmpty()) {
            return answer;
        }

        // 2. 按岗位名称长度降序排列，避免短名称先替换导致长名称匹配失败
        List<String> sortedPositions = new ArrayList<>(positionToPersons.keySet());
        sortedPositions.sort((a, b) -> b.length() - a.length());

        // 3. 逐个岗位名称，将 answer 中所有出现处替换为 岗位名称【人员，电话：xxx】
        String result = answer;
        for (String position : sortedPositions) {
            List<EmergencyPlanScreenResult.DutyPersonInfo> persons = positionToPersons.get(position);
            String insertion = formatPersonsInsertion(persons);
            result = result.replace(position, position + insertion);
            log.debug("岗位[{}]插入值班人员: {}", position, insertion);
        }

        return result;
    }

    /**
     * 格式化岗位人员插入文本：【姓名1，电话：xxx；姓名2，电话：yyy】
     */
    private String formatPersonsInsertion(List<EmergencyPlanScreenResult.DutyPersonInfo> persons) {
        StringBuilder sb = new StringBuilder("【");
        for (int i = 0; i < persons.size(); i++) {
            EmergencyPlanScreenResult.DutyPersonInfo p = persons.get(i);
            if (i > 0) {
                sb.append("；");
            }
            sb.append(p.getPersonName());
            if (p.getPhone() != null && !p.getPhone().isBlank()) {
                sb.append("，电话：").append(p.getPhone());
            }
        }
        sb.append("】");
        return sb.toString();
    }

    /**
     * 构建今日全部值班人员的 DutyPersonInfo 列表（不做岗位匹配，全部返回，供 type=1/3 的 AI 匹配使用）
     * <p>
     * 适配新接口字段：membername(姓名)、depname(部门)、membertype(类型)、area(区域)
     * 兼容旧接口字段：personName、positionName、positionGroupName
     */
    private List<EmergencyPlanScreenResult.DutyPersonInfo> buildAllDutyPersonInfos(List<Map<String, Object>> allStaff) {
        List<EmergencyPlanScreenResult.DutyPersonInfo> persons = new ArrayList<>();
        for (Map<String, Object> person : allStaff) {
            // 新接口字段
            String membername = strVal(person.get("membername")).trim();
            String depname = strVal(person.get("depname"));
            String membertype = strVal(person.get("membertype"));
            String area = strVal(person.get("area"));
            // 兼容旧接口字段
            String personName = strVal(person.get("personName"));
            String positionName = strVal(person.get("positionName"));
            String positionGroupName = strVal(person.get("positionGroupName"));

            EmergencyPlanScreenResult.DutyPersonInfo info = EmergencyPlanScreenResult.DutyPersonInfo.builder()
                    .personName(!membername.isBlank() ? membername : personName)
                    .phone(extractPhone(person))
                    // positionName 兼容：优先用旧接口字段，没有则用 membertype
                    .positionName(!positionName.isBlank() ? positionName : membertype)
                    // positionGroupName 兼容：优先用旧接口字段，没有则用 depname
                    .positionGroupName(!positionGroupName.isBlank() ? positionGroupName : depname)
                    .memberType(membertype)
                    .depname(depname)
                    .area(area)
                    .build();
            persons.add(info);
        }
        return persons;
    }

    /**
     * AI一步完成人员匹配+格式化输出（type=1）
     * <p>
     * 将知识库 answer 和今日全部值班人员交给 AI，由 AI 智能判断 answer 中提到的岗位
     * 对应哪些值班人员（解决 positionGroupName='值班领导 / 驻站领导' 与 answer 中 '值班领导' 不匹配的问题），
     * 并一步生成格式化结果。
     *
     * @param emergencyAnswer 知识库返回的答案文本
     * @param allDutyPersons  今日全部值班人员列表
     * @return AI格式化后的输出文本
     */
    private String aiMatchAndFormat(String emergencyAnswer,
                                    List<EmergencyPlanScreenResult.DutyPersonInfo> allDutyPersons) {
        if (emergencyAnswer == null || emergencyAnswer.isBlank()) {
            return "";
        }

        // 构建值班人员列表文本
        StringBuilder personList = new StringBuilder();
        if (allDutyPersons != null && !allDutyPersons.isEmpty()) {
            for (int i = 0; i < allDutyPersons.size(); i++) {
                EmergencyPlanScreenResult.DutyPersonInfo p = allDutyPersons.get(i);
                personList.append(i + 1).append(". 姓名: ").append(p.getPersonName());
                if (p.getPhone() != null && !p.getPhone().isBlank()) {
                    personList.append("，电话: ").append(p.getPhone());
                }
                // 部门名称（新接口 depname 或旧接口 positionGroupName）
                String depName = p.getDepname();
                if ((depName == null || depName.isBlank()) && p.getPositionGroupName() != null) {
                    depName = p.getPositionGroupName();
                }
                if (depName != null && !depName.isBlank()) {
                    personList.append("，部门: ").append(depName);
                }
                if (p.getArea() != null && !p.getArea().isBlank()) {
                    personList.append("，区域: ").append(p.getArea());
                }
                personList.append("\n");
            }
        }

        String prompt = """
                你是铁路车站应急预案格式化助手。请根据以下应急预案内容，智能匹配今日值班人员并格式化输出各部门应急措施。

                【应急预案内容】
                %s

                【今日值班人员列表】
                %s

                == 部门分类与岗位映射规则 ==
                请严格按以下部门顺序组织输出，将今日值班人员智能匹配到对应部门。
                注意：值班人员列表中每人只有"部门"和"区域"（仅部分人员有，如"东站驻站"）两个字段，没有任何岗位信息，人员与部门的对应关系完全由"部门"字段决定。
                - 同一部门下可能有多个值班人员，全部列出
                - 应急预案中未涉及的部门不需要输出
                - 一个人员只能归入一个部门，按下列规则顺序匹配，取第一个命中的部门
                - 人员归入哪个部门只依据"部门"字段的关键词匹配

                部门顺序与映射规则（按"部门"字段匹配）：
                1.领导：对应"部门"含"领导班子"的人员
                2.指挥中心：对应"部门"含"生产调度指挥中心"的人员
                3.行政办公室：对应"部门"含"办公室"或"司机班"的人员
                4.党群工作科：对应"部门"含"党群"或"舆情"或"融媒体"的人员
                5.劳动人事科：对应"部门"含"劳动人事"或"劳人"的人员
                6.客运业务科：对应"部门"为"客运科"的人员（必须完全等于"客运科"，不匹配"西安东站"）
                7.技术科：对应"部门"含"技术"的人员
                8.安全科：对应"部门"含"安全"的人员
                9.设备科：对应"部门"含"设备科"且不含"设备综合"的人员
                10.西安东客运：对应"部门"含"西安东"且含"客运"的人员；"部门"为"西安东站"但无法区分客运/行车/售票业务的人员也归入此部门
                11.西安东行车：对应"部门"含"西安东"且含"行车"的人员
                12.西安东售票：对应"部门"含"西安东"且含"售票"的人员
                13.西安东上水：对应"部门"含"上水"的人员
                14.设备综合车间：对应"部门"含"设备综合"的人员

                == 岗位标注规则 ==
                只有以下情况在姓名前追加岗位名称，其余人员直接列姓名，不要编造岗位名称：
                - 领导班子成员："区域"含"驻站"的标注为"驻站领导"，其余标注为"值班领导"
                - "部门"含"司机班"的标注为"司机班"

                == 匹配注意事项 ==
                - 应急预案中提到的"值主任""值班主任"等岗位名称只用于理解措施内容，不用于人员归属——人员归属只看"部门"字段
                - 匹配时保持应急预案中的原岗位名称不变，只在后面追加【人员姓名，电话：xxx】
                - 同一部门多个人员用"、"分隔，如：领导【值班领导：李本金，电话：xxx、驻站领导：杨炻，电话：xxx】

                == 输出要求 ==
                使用 Markdown 格式输出，标题用 `##`，子标题用 `###`，要点用无序列表 `-`，段落间用空行分隔。

                请严格按以下部门顺序输出，应急预案中未涉及的部门跳过不输出：

                ## 一、各部门应急措施

                ### 1.领导【值班领导：姓名，电话：xxx、驻站领导：姓名，电话：xxx】
                - ①按顺序要做什么
                - ②做什么

                ### 2.指挥中心【姓名，电话：xxx】
                - ①做什么

                ### 3.行政办公室【姓名，电话：xxx】
                - ①做什么

                ### 4.党群工作科【姓名，电话：xxx】
                - ①做什么

                ### 5.劳动人事科【姓名，电话：xxx】
                - ①做什么

                ### 6.客运业务科【姓名，电话：xxx】
                - ①做什么

                ### 7.技术科【姓名，电话：xxx】
                - ①做什么

                ### 8.安全科【姓名，电话：xxx】
                - ①做什么

                ### 9.设备科【姓名，电话：xxx】
                - ①做什么

                ### 10.西安东客运【姓名，电话：xxx】
                - ①做什么

                ### 11.西安东行车【姓名，电话：xxx】
                - ①做什么

                ### 12.西安东售票【姓名，电话：xxx】
                - ①做什么

                ### 13.西安东上水【姓名，电话：xxx】
                - ①做什么

                ### 14.设备综合车间【姓名，电话：xxx】
                - ①做什么

                ---

                ## 二、结束应急处置后上报流程

                ### 1.领导【值班领导：姓名，电话：xxx、驻站领导：姓名，电话：xxx】
                - 上报什么内容

                ### 2.指挥中心【姓名，电话：xxx】
                - 上报什么内容

                ### 3.行政办公室【姓名，电话：xxx】
                - 上报什么内容

                ### 4.党群工作科【姓名，电话：xxx】
                - 上报什么内容

                ### 5.劳动人事科【姓名，电话：xxx】
                - 上报什么内容

                ### 6.客运业务科【姓名，电话：xxx】
                - 上报什么内容

                ### 7.技术科【姓名，电话：xxx】
                - 上报什么内容

                ### 8.安全科【姓名，电话：xxx】
                - 上报什么内容

                ### 9.设备科【姓名，电话：xxx】
                - 上报什么内容

                ### 10.西安东客运【姓名，电话：xxx】
                - 上报什么内容

                ### 11.西安东行车【姓名，电话：xxx】
                - 上报什么内容

                ### 12.西安东售票【姓名，电话：xxx】
                - 上报什么内容

                ### 13.西安东上水【姓名，电话：xxx】
                - 上报什么内容

                ### 14.设备综合车间【姓名，电话：xxx】
                - 上报什么内容

                规则补充：
                1. 如果应急预案中没有明确提到某个部门，则跳过该部门不输出（包括"一"和"二"部分）。
                2. 每条措施必须具体、可落地，按处置顺序排列。
                3. 每个部门后面带上匹配到的今日值班人员姓名和电话（格式：【姓名，电话：xxx】），如果该部门无对应值班人员则不带括号。
                4. "二、结束应急处置后上报流程"部分也必须按部门分条输出，每个部门后面带上对应值班人员姓名和电话，人员信息必须与"一"部分同一部门完全一致，不得省略。
                5. 直接输出 Markdown 正文，不要用 ```markdown 代码块包裹，不要输出任何解释说明，不要胡编乱造。
                """.formatted(emergencyAnswer,
                personList.length() > 0 ? personList.toString() : "无");

        try {
            String aiAnswer = chatClientBuilder.build().prompt()
                    .user(prompt)
                    .call()
                    .content();
            if (aiAnswer != null && !aiAnswer.isBlank()) {
                String result = aiAnswer.trim();
                if (result.startsWith("```")) {
                    result = result.replaceAll("^```(\\w*)?\\n?", "")
                            .replaceAll("\\n?```$", "")
                            .trim();
                }
                log.debug("AI匹配+格式化应急预案完成, 长度={}", result.length());
                return result;
            }
        } catch (Exception e) {
            log.warn("AI匹配+格式化应急预案失败，返回answer原文: {}", e.getMessage());
        }
        return emergencyAnswer;
    }

    /**
     * 从 KnowledgeResponse 的 reference.chunks 中提取所有分块的 content 文本
     * <p>
     * reference 结构：
     * <pre>
     * { "chunks": [ {"content": "...", "document_keyword": "..."}, ... ] }
     * </pre>
     * 将所有 content 按 "---分段---" 连接成一段完整文本，供 AI 总结。
     *
     * @param emergencyResp 知识库响应
     * @return 拼接后的 chunks content 文本，无 chunks 时返回空串
     */
    private String extractChunksContent(KnowledgeResponse emergencyResp) {
        try {
            if (emergencyResp == null || emergencyResp.getData() == null
                    || emergencyResp.getData().getReference() == null) {
                return "";
            }
            JsonNode reference = emergencyResp.getData().getReference();
            JsonNode chunks = reference.get("chunks");
            if (chunks == null || !chunks.isArray() || chunks.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode chunk : chunks) {
                JsonNode contentNode = chunk.get("content");
                if (contentNode != null && !contentNode.asText().isBlank()) {
                    if (!sb.isEmpty()) {
                        sb.append("\n---\n");
                    }
                    sb.append(contentNode.asText());
                }
            }
            log.debug("提取chunks content完成, 分块数={}, 总长度={}",
                    chunks.size(), sb.length());
            return sb.toString();
        } catch (Exception e) {
            log.warn("提取chunks content失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * AI基于 reference.chunks 原文总结+人员匹配+格式化输出（type=3）
     * <p>
     * 中的原始分块 content 文本，让 AI 从原始检索内容中自行总结提炼应急措施，再匹配值班人员并格式化输出。
     * 如果 chunks content 为空，回退到使用 answer。
     *
     * @param chunksContent  从 reference.chunks 提取的原始分块文本
     * @param fallbackAnswer 知识库 answer（chunks 为空时回退使用）
     * @param allDutyPersons 今日全部值班人员列表
     * @return AI总结+格式化后的输出文本
     */
    private String aiChunksMatchAndFormat(String chunksContent, String fallbackAnswer,
                                          List<EmergencyPlanScreenResult.DutyPersonInfo> allDutyPersons,DispatchRequest request) {
        // chunks content 为空时回退到 answer
        String sourceContent = (chunksContent != null && !chunksContent.isBlank())
                ? chunksContent : fallbackAnswer;
        if (sourceContent == null || sourceContent.isBlank()) {
            return "";
        }

        // 构建值班人员列表文本（与 aiMatchAndFormat 相同的逻辑）
        StringBuilder personList = new StringBuilder();
        if (allDutyPersons != null && !allDutyPersons.isEmpty()) {
            for (int i = 0; i < allDutyPersons.size(); i++) {
                EmergencyPlanScreenResult.DutyPersonInfo p = allDutyPersons.get(i);
                personList.append(i + 1).append(". 姓名: ").append(p.getPersonName());
                if (p.getPhone() != null && !p.getPhone().isBlank()) {
                    personList.append("，电话: ").append(p.getPhone());
                }
                // 部门名称（新接口 depname 或旧接口 positionGroupName）
                String depName = p.getDepname();
                if ((depName == null || depName.isBlank()) && p.getPositionGroupName() != null) {
                    depName = p.getPositionGroupName();
                }
                if (depName != null && !depName.isBlank()) {
                    personList.append("，部门: ").append(depName);
                }
                if (p.getArea() != null && !p.getArea().isBlank()) {
                    personList.append("，区域: ").append(p.getArea());
                }
                personList.append("\n");
            }
        }

        String sourceLabel = (chunksContent != null && !chunksContent.isBlank())
                ? "知识库检索到的原始文档分块内容" : "知识库总结答案";

        String prompt = """
                你是铁路车站应急预案格式化助手。请根据以下%s，总结提炼%s（各部门应急措施），智能匹配今日值班人员并格式化输出。

                【%s】
                %s

                【今日值班人员列表】
                %s

                == 部门分类与岗位映射规则 ==
                请严格按以下部门顺序组织输出，将今日值班人员智能匹配到对应部门。
                注意：值班人员列表中每人只有"部门"和"区域"（仅部分人员有，如"东站驻站"）两个字段，没有任何岗位信息，人员与部门的对应关系完全由"部门"字段决定。
                - 同一部门下可能有多个值班人员，全部列出
                - 应急预案中未涉及的部门不需要输出
                - 一个人员只能归入一个部门，按下列规则顺序匹配，取第一个命中的部门
                - 人员归入哪个部门只依据"部门"字段的关键词匹配

                部门顺序与映射规则（按"部门"字段匹配）：
                1.领导：对应"部门"含"领导班子"的人员
                2.指挥中心：对应"部门"含"生产调度指挥中心"的人员
                3.行政办公室：对应"部门"含"办公室"或"司机班"的人员
                4.党群工作科：对应"部门"含"党群"或"舆情"或"融媒体"的人员
                5.劳动人事科：对应"部门"含"劳动人事"或"劳人"的人员
                6.客运业务科：对应"部门"为"客运科"的人员（必须完全等于"客运科"，不匹配"西安东站"）
                7.技术科：对应"部门"含"技术"的人员
                8.安全科：对应"部门"含"安全"的人员
                9.设备科：对应"部门"含"设备科"且不含"设备综合"的人员
                10.西安东客运：对应"部门"含"西安东"且含"客运"的人员；"部门"为"西安东站"但无法区分客运/行车/售票业务的人员也归入此部门
                11.西安东行车：对应"部门"含"西安东"且含"行车"的人员
                12.西安东售票：对应"部门"含"西安东"且含"售票"的人员
                13.西安东上水：对应"部门"含"上水"的人员
                14.设备综合车间：对应"部门"含"设备综合"的人员

                == 岗位标注规则 ==
                只有以下情况在姓名前追加岗位名称，其余人员直接列姓名，不要编造岗位名称：
                - 领导班子成员："区域"含"驻站"的标注为"驻站领导"，其余标注为"值班领导"
                - "部门"含"司机班"的标注为"司机班"

                == 匹配注意事项 ==
                - 应急预案中提到的"值主任""值班主任"等岗位名称只用于理解措施内容，不用于人员归属——人员归属只看"部门"字段
                - 按岗位标注规则追加岗位名称，其余部门不追加岗位名称只追加值班人员，例如：安全科【人员姓名，电话：xxx】
                - 同一部门多个人员用"、"分隔，如：领导【值班领导：李本金，电话：xxx、驻站领导：杨炻，电话：xxx】

                == 输出要求 ==
                使用 Markdown 格式输出，标题用 `##`，子标题用 `###`，要点用无序列表 `-`，段落间用空行分隔。

                == 部门输出规则（非常重要，必须严格遵守） ==
                1. 只输出应急预案内容中明确提及的部门，未提及的部门绝对不输出——不要输出"跳过""未提及"等任何说明文字，直接省略该部门的整个小节。
                2. 部门序号按实际输出的部门从1开始连续编号，不要保留原始部门列表中的序号。部门里的要做的措施都用①②这样顺序排序。
                   例如：如果只有领导、安全科、西安东客运三个部门有内容，则编号为 ### 1.领导 ①做什么 ②做什么、### 2.安全科 ①做什么、### 3.西安东客运 ①做什么。
                3. "一、各部门应急措施"和"二、结束应急处置后上报流程"两个部分的部门列表和顺序必须一致，序号同样连续编排，且同一部门在两部分中的人员信息必须完全一致，不得省略。
                4. 每条措施必须具体、可落地，按处置顺序排列。
                5. 每个部门后面带上匹配到的今日值班人员姓名和电话（格式：【姓名，电话：xxx】），如果该部门无对应值班人员则该部门后面不带上【姓名,电话】。
                6. 直接输出 Markdown 正文，不要用 ```markdown 代码块包裹，不要输出任何解释说明，不要胡编乱造。

                输出格式示例（假设只有领导、指挥中心、行政办公室、党群工作科、技术科、安全科、西安东客运、西安东行车八个部门有内容）：

                ## 一、各部门应急措施

                ### 1.领导【值班领导：姓名，电话：xxx、驻站领导：姓名，电话：xxx】
                - ①按顺序要做什么
                - ②做什么

                ### 2.指挥中心【姓名，电话：xxx】
                - ①做什么

                ### 3.行政办公室【姓名，电话：xxx】
                - ①做什么

                ### 4.党群工作科【姓名，电话：xxx】
                - ①做什么

                ### 5.技术科【姓名，电话：xxx】
                - ①做什么

                ### 6.安全科【姓名，电话：xxx】
                - ①做什么

                ### 7.西安东客运【姓名，电话：xxx】
                - ①做什么

                ### 8.西安东行车【姓名，电话：xxx】
                - ①做什么

                ---

                ## 二、结束应急处置后上报流程

                ### 1.领导【值班领导：姓名，电话：xxx、驻站领导：姓名，电话：xxx】
                - 上报什么内容

                ### 2.指挥中心【姓名，电话：xxx】
                - 上报什么内容
                
                ### 3.行政办公室【姓名，电话：xxx】
                - 上报什么内容

                ### 4.党群工作科【姓名，电话：xxx】
                - 上报什么内容

                ### 5.技术科【姓名，电话：xxx】
                - 上报什么内容

                ### 6.安全科【姓名，电话：xxx】
                - 上报什么内容

                ### 7.西安东客运【姓名，电话：xxx】
                - 上报什么内容

                ### 8.西安东行车【姓名，电话：xxx】
                - 上报什么内容


                注意：以上仅为格式示例，实际输出的部门由应急预案内容决定，按上述14条映射规则中的顺序依次筛选输出。
                """.formatted(sourceLabel, request.getQuestion(),sourceLabel, sourceContent,
                personList.length() > 0 ? personList.toString() : "无");

        try {
            String aiAnswer = chatClientBuilder.build().prompt()
                    .user(prompt)
                    .call()
                    .content();
            if (aiAnswer != null && !aiAnswer.isBlank()) {
                String result = aiAnswer.trim();
                if (result.startsWith("```")) {
                    result = result.replaceAll("^```(\\w*)?\\n?", "")
                            .replaceAll("\\n?```$", "")
                            .trim();
                }
                log.debug("AI(chunks)总结+格式化应急预案完成, 长度={}", result.length());
                return result;
            }
        } catch (Exception e) {
            log.warn("AI(chunks)总结+格式化应急预案失败，返回answer原文: {}", e.getMessage());
        }
        return fallbackAnswer != null ? fallbackAnswer : "";
    }

    /**
     * 构建大屏应急预案的 remake 标识（question前30字 + type）
     */
    private String buildScreenRemake(String question, int type) {
        return "大屏应急预案(" + (question != null
                ? question.substring(0, Math.min(30, question.length()))
                : "") + ")";
    }

    /**
     * type=2: 关键词匹配 + AI格式化
     * AI提炼应急预案答案，格式化为各部门应急措施和处置后上报流程
     * <p>
     * 输出格式：
     * <pre>
     * 一、各部门应急措施：
     * 1.值班领导【李四，电话：13800138000】：按顺序要做什么
     * 2.指挥中心【王五，电话：13900139000】：做什么
     * 二、结束应急处置后上报流程：
     * ……
     * </pre>
     * 如果 enrichedAnswer 中明确提到了部门/岗位，按部门分条输出；
     * 如果没有明确提到部门，则提炼总结需要做什么。
     *
     * @param enrichedAnswer 已填充了值班人员信息的答案文本
     * @param dutyPersons    匹配到的值班人员列表
     * @return AI格式化后的输出文本
     */
    private String aiFormatEmergencyPlan(
            String enrichedAnswer, List<EmergencyPlanScreenResult.DutyPersonInfo> dutyPersons) {
        if (enrichedAnswer == null || enrichedAnswer.isBlank()) {
            return "";
        }

        // 构建值班人员信息摘要供AI参考
        StringBuilder personInfo = new StringBuilder();
        if (dutyPersons != null && !dutyPersons.isEmpty()) {
            for (EmergencyPlanScreenResult.DutyPersonInfo p : dutyPersons) {
                personInfo.append("- ").append(p.getPositionName() != null ? p.getPositionName() : "");
                if (p.getPositionGroupName() != null && !p.getPositionGroupName().isBlank()
                        && !p.getPositionGroupName().equals(p.getPositionName())) {
                    personInfo.append("(").append(p.getPositionGroupName()).append(")");
                }
                personInfo.append("：").append(p.getPersonName());
                if (p.getPhone() != null && !p.getPhone().isBlank()) {
                    personInfo.append("，电话：").append(p.getPhone());
                }
                personInfo.append("\n");
            }
        }

        String prompt = """
                你是应急预案格式化助手。请根据以下应急预案内容，提炼并格式化输出各部门应急措施。

                【应急预案内容】
                %s

                【今日值班人员信息】
                %s

                == 输出要求 ==
                ## 一、各部门应急措施

                ### 1.领导【值班领导：姓名，电话：xxx、驻站领导：姓名，电话：xxx】

                - ①按顺序要做什么
                - ②做什么

                ### 2.指挥中心【姓名，电话：xxx】

                - ①做什么

                ### 3.客运【姓名，电话：xxx】

                - ①做什么
                
                ### 4.行车【姓名，电话：xxx】
                ---

                ## 二、结束应急处置后上报流程

                ### 1.领导【值班领导：姓名，电话：xxx、驻站领导：姓名，电话：xxx】

                - 上报什么内容

                ### 2.指挥中心【姓名，电话：xxx】

                - 上报什么内容
                   ……
                   
                2. 如果应急预案中没有明确提到部门，则提炼总结需要做什么，仍按上述格式输出。
                3. 每条措施必须具体、可落地，按处置顺序排列，如果有部门也需要带上今日值班人员姓名和电话。
                4. 输出的所有内容中每个部门后面带上匹配到的今日值班人员姓名和电话（格式：【姓名，电话：xxx】），如果该部门无对应值班人员则无需带上。
                5. 最后必须有"二、结束应急处置后上报流程："部分，总结处置后的上报要求，如果有部门也需要带上今日值班人员姓名和电话。
                6. 只输出格式化后的文本，不要输出markdown代码块、不要输出任何解释说明、不要胡编乱造。
                """.formatted(enrichedAnswer,
                personInfo.length() > 0 ? personInfo.toString() : "无");

        try {
            String aiAnswer = chatClientBuilder.build().prompt()
                    .user(prompt)
                    .call()
                    .content();
            if (aiAnswer != null && !aiAnswer.isBlank()) {
                // 去除可能的markdown代码块包裹
                String result = aiAnswer.trim();
                if (result.startsWith("```")) {
                    result = result.replaceAll("^```(\\w*)?\\n?", "")
                            .replaceAll("\\n?```$", "")
                            .trim();
                }
                log.debug("AI格式化应急预案完成, 长度={}", result.length());
                return result;
            }
        } catch (Exception e) {
            log.warn("AI格式化应急预案失败，返回enrichedAnswer原文: {}", e.getMessage());
        }
        return enrichedAnswer;
    }

    /**
     * 落库到大屏应急预案分析记录（失败不影响返回结果）
     */
    private void saveScreenResult(EmergencyPlanScreenResult result, List<Map<String, Object>> allStaff, int type) {
        try {
            AiAnalysisResult record = new AiAnalysisResult();
            record.setType(SCREEN_RESULT_TYPE);
            record.setOData(objectMapper.writeValueAsString(allStaff));
            record.setResultData(objectMapper.writeValueAsString(result));
            record.setRemake(buildScreenRemake(result.getQuestion(), type));
            record.setCreateTime(LocalDateTime.now());
            aiAnalysisResultService.save(record);
            log.info("大屏应急预案结果已落库: id={}, type={}", record.getId(), type);
        } catch (Exception e) {
            log.error("大屏应急预案结果落库失败", e);
        }
    }

    /**
     * 安全转字符串
     */
    private String strVal(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    /**
     * 依次取 map 中第一个非空的字段值，用于新旧接口字段回退
     * <p>如 mapStr(map, "membername", "personName")：优先取新接口字段 membername，为空时回退旧接口字段 personName
     */
    private String mapStr(Map<String, Object> map, String primary, String fallback) {
        String val = strVal(map.get(primary));
        return !val.isBlank() ? val : strVal(map.get(fallback));
    }

    /**
     * 从 KnowledgeResponse 的 reference.doc_aggs 中提取来源文件名，构建来源后缀文本
     * <p>
     * 格式：\n\n---\n\n来源文件：《doc1》、《doc2》……
     *
     * @param emergencyResp 知识库响应
     * @return 来源后缀文本，无来源时返回空串
     */
    private String buildSourceSuffix(KnowledgeResponse emergencyResp) {
        try {
            if (emergencyResp == null || emergencyResp.getData() == null
                    || emergencyResp.getData().getReference() == null) {
                return "";
            }
            JsonNode reference = emergencyResp.getData().getReference();
            JsonNode docAggs = reference.get("doc_aggs");
            if (docAggs == null || !docAggs.isArray() || docAggs.isEmpty()) {
                return "";
            }
            // 收集所有 doc_name
            List<String> docNames = new ArrayList<>();
            for (JsonNode docAgg : docAggs) {
                JsonNode nameNode = docAgg.get("doc_name");
                if (nameNode != null && !nameNode.asText().isBlank()) {
                    docNames.add(nameNode.asText());
                }
            }
            if (docNames.isEmpty()) {
                return "";
            }
            // 拼接：《doc1》、《doc2》……
            StringBuilder sb = new StringBuilder("\n\n---\n\n来源文件：");
            for (int i = 0; i < docNames.size(); i++) {
                if (i > 0) {
                    sb.append("、");
                }
                sb.append("《").append(docNames.get(i)).append("》");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("提取来源文件失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 调用应急预案知识库，返回结构化响应
     */
    private KnowledgeResponse queryEmergencyPlan(String question) {
        // retrievalChunks=false + summarize=false：使用Ragflow聊天助手总结，answer带引用坐标（10~40秒）
        KnowledgeResponse response = mcpxClient.opsKnowledge(new KnowledgeRequest(question, false, true, false,null));
        if (response == null || Boolean.FALSE.equals(response.getSuccess())) {
            log.warn("应急预案知识库调用失败: {}", response != null ? response.getError() : "无响应");
        }
        return response;
    }

    /**
     * 获取今日值班领导列表（positionGroupName 包含"值班领导"）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getTodayLeaders() {
        RosterPageRequest rosterReq = new RosterPageRequest();
        rosterReq.setPage(1);
        rosterReq.setLimit(100);
        rosterReq.fillDefaultDate();

        AjaxResultVO rosterResult = rosterService.queryPage(rosterReq);
        Object dataObj = rosterResult.get("data");
        if (!(dataObj instanceof List)) {
            log.warn("值班人员接口返回 data 不是列表");
            return List.of();
        }
        List<Map<String, Object>> allStaff = (List<Map<String, Object>>) dataObj;

        // 筛选值班领导：新接口字段为 depname(部门)/membertype(类型)，领导班子的成员（含副主任）均为领导岗
        List<Map<String, Object>> leaders = allStaff.stream()
                .filter(m -> {
                    String depname = strVal(m.get("depname"));
                    String membertype = strVal(m.get("membertype"));
                    return depname.contains(LEADER_KEYWORD) || membertype.contains(LEADER_KEYWORD);
                })
                .toList();

        log.debug("今日值班总人数={}, 值班领导人数={}", allStaff.size(), leaders.size());
        return leaders;
    }

    /**
     * AI 根据应急预案答案筛选最佳值班领导
     */
    private Map<String, Object> selectBestLeader(String emergencyAnswer, List<Map<String, Object>> leaders) {
        if (leaders == null || leaders.isEmpty()) {
            log.warn("今日无值班领导，跳过AI筛选");
            return null;
        }
        if (leaders.size() == 1) {
            return leaders.get(0);
        }

        // 构建值班领导列表文本（新接口字段：membername/depname/membertype，兼容旧接口字段）
        StringBuilder leaderListText = new StringBuilder();
        for (int i = 0; i < leaders.size(); i++) {
            Map<String, Object> leader = leaders.get(i);
            leaderListText.append(i + 1).append(". ")
                    .append("姓名: ").append(mapStr(leader, "membername", "personName"))
                    .append(", 部门: ").append(mapStr(leader, "depname", "positionGroupName"))
                    .append(", 类型: ").append(mapStr(leader, "membertype", "positionName"))
                    .append("\n");
        }

        String prompt = """
                你是应急调度系统，请根据以下应急预案内容，从今日值班领导中选派最合适的人选处理该应急事件。
                
                【应急预案内容】
                %s
                
                【可选值班领导列表】
                %s
                
                请从以上列表中选择最合适的一位，只返回该人员的序号（数字），不要返回任何其他内容。
                """.formatted(emergencyAnswer, leaderListText);

        try {
            String aiAnswer = chatClientBuilder.build().prompt()
                    .user(prompt)
                    .call()
                    .content();

            // 解析AI返回的序号
            if (aiAnswer != null) {
                aiAnswer = aiAnswer.trim();
                // 尝试直接解析为序号
                try {
                    int selectedNo = Integer.parseInt(aiAnswer);
                    if (selectedNo >= 1 && selectedNo <= leaders.size()) {
                        Map<String, Object> selected = leaders.get(selectedNo - 1);
                        log.debug("AI筛选值班领导: 序号={}, 姓名={}", selectedNo, mapStr(selected, "membername", "personName"));
                        return selected;
                    }
                } catch (NumberFormatException ignored) {
                    // AI可能返回了文字描述，fallback到第一个
                }
            }
        } catch (Exception e) {
            log.warn("AI筛选值班领导失败，使用默认选择", e);
        }

        // fallback: 返回第一个
        log.debug("AI筛选fallback，默认选择第一个值班领导: {}", mapStr(leaders.get(0), "membername", "personName"));
        return leaders.get(0);
    }

    /**
     * 知识库信息记录（id + name）
     */
    private record ChatInfo(String id, String name) {}
}
