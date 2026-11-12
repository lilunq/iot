package com.sysware.xian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.domain.AiAnalysisResult;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import com.sysware.xian.swcloud.domain.KnowledgeRequest;
import com.sysware.xian.swcloud.domain.KnowledgeResponse;
import com.sysware.xian.swcloud.domain.ProblemAnalysisRequest;
import com.sysware.xian.swcloud.domain.ProblemAnalysisResult;
import com.sysware.xian.swcloud.domain.ProblemPageRequest;
import com.sysware.xian.swcloud.domain.ProblemStatItem;
import com.sysware.xian.swcloud.fegin.MCPXClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Description: 问题库AI分析服务
 * 拉取周期内问题数据 → Java统计三维度数量 → 结合应急预案知识库 → AI生成下月工作重点/检查重点
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemAnalysisService {

    /** 知识库提问取问题数量最多的前N个类别 */
    private static final int TOP_CATEGORY_LIMIT = 5;
    /** 单条问题概述送入AI的最大长度，防止提示词过长 */
    private static final int OVERVIEW_MAX_LEN = 200;
    /** 分析记录持久化类型标识 */
    public static final String RESULT_TYPE = "problem_analysis";

    private final ProblemService problemService;
    private final MCPXClient mcpxClient;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final AiAnalysisResultService aiAnalysisResultService;

    private static final String SYSTEM_PROMPT = """
            你是铁路车站安全监控分析专家，负责根据车站问题库数据编写安全监控通报中的"下月工作重点"和"检查重点"。

            == 写作要求 ==
            1. workFocus（下月工作重点）：3-6条，每条以"一是""二是""三是"开头，围绕问题数量最多的类别和区域，
               结合应急预案知识库提供的管控措施，给出全过程、可落地的监督防控举措，文风参照铁路安全通报（如：
               "一是紧盯人身安全风险，凡站内车辆上水、异物清理等上道作业，严格执行双人防护制度……"）。
            2. inspectionFocus（检查重点）：4-6条，按"岗位/类别：具体检查内容"格式分条（如：
               "劳动安全：全覆盖检查劳保佩戴、扶梯扶握、横越线路、上水防护、登高安全带等人身防护要求。"），
               检查内容必须从问题明细中的具体违规场景和违反的规章条款提炼，具体到作业环节，杜绝空泛表述。
            3. reportText：将上述内容组织成完整通报文本，分"一、下月工作重点"与"二、检查重点"两个小节，
               小节标题与内容之间、条目之间一律用换行符\\n分隔，禁止分号或句号连成整段：
               工作重点逐条按"1.""2."换行编号分行书写（每条以\\n结尾），检查重点逐条按"1. ""2. "换行编号（每条以\\n结尾）。
            4. 所有结论必须以提供的统计数量、问题明细、违反规章为依据，可结合应急预案知识库答复补充管控措施，不得编造数据。

            == 输出格式 ==
            严格按照以下JSON格式输出，不要输出markdown代码块，不要输出任何其他内容：
            {
              "workFocus": ["一是……", "二是……"],
              "inspectionFocus": ["劳动安全：……", "消防管理：……"],
              "reportText": "一、下月工作重点\\n1.……\\n2.……\\n二、检查重点\\n1.……\\n2.……"
            }
            """;

    /**
     * 执行问题库AI分析并持久化结果。
     * 由 ScheduledService 每日凌晨2点调用；每月1号凌晨执行时分析上月完整周期（上月1号~上月末），其余日期分析本月1号~当日。
     */
    public void scheduledAnalyze() {
        log.info("定时问题库AI分析任务开始");
        try {
            ProblemAnalysisRequest request = new ProblemAnalysisRequest();
            LocalDate today = LocalDate.now();
            if (today.getDayOfMonth() == 1) {
                // 月初补存上月完整周期
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");
                LocalDate lastMonthStart = today.minusMonths(1).withDayOfMonth(1);
                LocalDate lastMonthEnd = today.minusDays(1);
                request.setDateStart(lastMonthStart.format(fmt));
                request.setDateEnd(lastMonthEnd.format(fmt));
            }
            analyze(request, true);
            log.info("定时问题库AI分析任务完成");
        } catch (Exception e) {
            log.error("定时问题库AI分析任务失败", e);
        }
    }

    /**
     * 问题库AI分析主入口（持久化）
     *
     * @param request 分析请求（日期缺省为本月1号~今日）
     * @return 统计结果 + AI分析的工作重点/检查重点
     */
    public ProblemAnalysisResult analyze(ProblemAnalysisRequest request) {
        return analyze(request, true);
    }

    /**
     * 问题库AI分析主入口
     *
     * @param request 分析请求（日期缺省为本月1号~今日）
     * @param persist 是否将本次分析持久化到 ai_analysis_result 表
     * @return 统计结果 + AI分析的工作重点/检查重点
     */
    public ProblemAnalysisResult analyze(ProblemAnalysisRequest request, boolean persist) {
        // 1. 填充日期默认值（本月1号~今日）
        request.fillDefaultDate();
        log.debug("问题库AI分析: dateStart={}, dateEnd={}", request.getDateStart(), request.getDateEnd());

        // 2. 拉取周期内问题列表
        List<Map<String, Object>> problems = fetchProblems(request.getDateStart(), request.getDateEnd());

        // 3. Java精确统计三个维度数量（降序），字段名为 /stationWtjc/list 返回的原始字段
        List<ProblemStatItem> levelStats = countBy(problems, "grade");
        List<ProblemStatItem> categoryStats = countBy(problems, "property");
        List<ProblemStatItem> areaStats = countBy(problems, "duty_ks_name");

        ProblemAnalysisResult.ProblemAnalysisResultBuilder builder = ProblemAnalysisResult.builder()
                .dateStart(request.getDateStart())
                .dateEnd(request.getDateEnd())
                .totalCount(problems.size())
                .levelStats(levelStats)
                .categoryStats(categoryStats)
                .areaStats(areaStats);

        ProblemAnalysisResult result;
        if (problems.isEmpty()) {
            result = builder
                    .workFocus(List.of())
                    .inspectionFocus(List.of())
                    .reportText("统计周期内无问题记录，无需生成工作重点。")
                    .build();
        } else {
            // 4. 按TOP问题类别合并提问应急预案知识库
            String knowledgeAnswer = queryKnowledgeByTopCategories(categoryStats);
            builder.knowledgeAnswer(knowledgeAnswer);

            // 5. AI分析生成下月工作重点/检查重点
            Map<String, Object> aiResult = aiAnalyze(request, problems, levelStats, categoryStats, areaStats, knowledgeAnswer);
            result = builder.workFocus(toStringList(aiResult.get("workFocus")))
                    .inspectionFocus(toStringList(aiResult.get("inspectionFocus")))
                    .reportText(aiResult.get("reportText") != null ? aiResult.get("reportText").toString() : "")
                    .build();
        }

        // 6. 持久化分析记录
        if (persist) {
            saveRecord(request, problems, result);
        }
        return result;
    }

    /**
     * 持久化分析记录到 ai_analysis_result 表（失败不影响分析结果返回）
     */
    private void saveRecord(ProblemAnalysisRequest request, List<Map<String, Object>> problems, ProblemAnalysisResult result) {
        try {
            AiAnalysisResult record = new AiAnalysisResult();
            record.setType(RESULT_TYPE);
            record.setOData(objectMapper.writeValueAsString(problems));
            record.setResultData(objectMapper.writeValueAsString(result));
            record.setRemake("问题库AI分析(" + request.getDateStart() + "~" + request.getDateEnd() + ")");
            record.setCreateTime(LocalDateTime.now());
            aiAnalysisResultService.save(record);
            log.info("问题库AI分析结果已持久化: id={}, 问题数={}", record.getId(), problems.size());
        } catch (Exception e) {
            log.error("问题库AI分析结果持久化失败", e);
        }
    }

    /**
     * 拉取周期内问题列表（日期段接口一次查询，数据原样透传上游 /stationWtjc/list 字段；失败自动回退逐天查询）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchProblems(String dateStart, String dateEnd) {
        AjaxResultVO pageResult = problemService.queryPage(
                ProblemPageRequest.ofRange(dateStart, dateEnd));
        if (pageResult == null || !pageResult.isOk()) {
            throw new RuntimeException("查询问题库失败: " + (pageResult != null ? pageResult.getMsg() : "无响应"));
        }
        Object dataObj = pageResult.get("data");
        if (!(dataObj instanceof List)) {
            return List.of();
        }
        return (List<Map<String, Object>>) dataObj;
    }

    /**
     * 按指定字段统计数量（降序，空值归为"未分类"）
     */
    private List<ProblemStatItem> countBy(List<Map<String, Object>> problems, String field) {
        Map<String, Long> counts = problems.stream()
                .collect(Collectors.groupingBy(
                        p -> {
                            Object v = p.get(field);
                            return v != null && !v.toString().isBlank() ? v.toString() : "未分类";
                        },
                        LinkedHashMap::new,
                        Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new ProblemStatItem(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * 按TOP问题类别合并提问应急预案知识库（失败不阻断AI分析）
     */
    private String queryKnowledgeByTopCategories(List<ProblemStatItem> categoryStats) {
        String topCategories = categoryStats.stream()
                .limit(TOP_CATEGORY_LIMIT)
                .map(ProblemStatItem::getName)
                .filter(name -> !"未分类".equals(name))
                .collect(Collectors.joining("、"));
        if (topCategories.isBlank()) {
            return "";
        }
        String question = "针对车站" + topCategories + "类安全问题，有哪些管控措施、作业标准和应急处置要求？";
        log.info("应急预案知识库提问: question={}", question);
        try {
            // retrievalChunks=true + summarize=true：大模型直接总结检索块（速度快，answer不含引用坐标），直接取answer
            KnowledgeResponse response = mcpxClient.opsKnowledge(new KnowledgeRequest(question, false, true, true,null));
            if (response == null || Boolean.FALSE.equals(response.getSuccess())) {
                log.warn("应急预案知识库调用失败: {}", response != null ? response.getError() : "无响应");
                return "";
            }
            String answer = response.answerText();
            if (answer.isBlank()) {
                // summarize=true时answer不应为空，为空说明总结失败或检索无内容，打印检索分块便于排查
                String referenceStr = response.getData() != null && response.getData().getReference() != null
                        ? response.getData().getReference().toString()
                        : "";
                log.warn("应急预案知识库answer为空，reference: {}",
                        referenceStr.length() > 500 ? referenceStr.substring(0, 500) + "……" : referenceStr);
            } else {
                log.info("应急预案知识库答复: 长度={}, answer={}", answer.length(), answer);
            }
            return answer;
        } catch (Exception e) {
            log.warn("调用应急预案知识库失败，AI分析将不含知识库依据: question={}", question, e);
            return "";
        }
    }

    /**
     * 调用AI分析生成下月工作重点/检查重点
     */
    private Map<String, Object> aiAnalyze(ProblemAnalysisRequest request,
                                          List<Map<String, Object>> problems,
                                          List<ProblemStatItem> levelStats,
                                          List<ProblemStatItem> categoryStats,
                                          List<ProblemStatItem> areaStats,
                                          String knowledgeAnswer) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("统计周期：").append(request.getDateStart()).append(" 至 ").append(request.getDateEnd())
                .append("，问题总数：").append(problems.size()).append("\n\n");

        prompt.append("【问题级别统计】\n").append(formatStats(levelStats));
        prompt.append("【问题类别统计】\n").append(formatStats(categoryStats));
        prompt.append("【问题区域统计】\n").append(formatStats(areaStats));

        if (knowledgeAnswer != null && !knowledgeAnswer.isBlank()) {
            prompt.append("【应急预案知识库答复（TOP问题类别的管控措施）】\n").append(knowledgeAnswer).append("\n\n");
        }

        prompt.append("【问题明细】\n");
        for (Map<String, Object> p : problems) {
            prompt.append("- 日期:").append(p.get("create_time"))
                    .append(" | 级别:").append(p.get("grade"))
                    .append(" | 类别:").append(p.get("property"))
                    .append(" | 区域:").append(p.get("duty_ks_name"))
                    .append("\n  概述及违反规章:").append(truncate(p.get("checks")))
                    .append("\n");
        }
        prompt.append("\n请根据以上数据生成下月工作重点与检查重点，输出JSON。");

        ChatClient chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        String answer = chatClient.prompt()
                .user(prompt.toString())
                .call()
                .content();
        log.debug("问题库AI分析完成");

        return parseAiResult(answer);
    }

    /**
     * 统计结果格式化为提示词文本
     */
    private String formatStats(List<ProblemStatItem> stats) {
        StringBuilder sb = new StringBuilder();
        for (ProblemStatItem item : stats) {
            sb.append(item.getName()).append(": ").append(item.getCount()).append("条\n");
        }
        return sb.append("\n").toString();
    }

    /**
     * 截断过长的明细文本
     */
    private String truncate(Object value) {
        if (value == null) return "";
        String text = value.toString();
        return text.length() > OVERVIEW_MAX_LEN ? text.substring(0, OVERVIEW_MAX_LEN) + "……" : text;
    }

    /**
     * 解析AI返回的JSON（解析失败时原文放入reportText兜底）
     */
    private Map<String, Object> parseAiResult(String answer) {
        try {
            String json = answer;
            if (answer.contains("```json")) {
                json = answer.substring(answer.indexOf("```json") + 7, answer.lastIndexOf("```"));
            } else if (answer.contains("```")) {
                json = answer.substring(answer.indexOf("```") + 3, answer.lastIndexOf("```"));
            }
            json = json.trim();
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            log.error("解析问题库AI分析结果失败，原文兜底到reportText: {}", e.getMessage());
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("workFocus", new ArrayList<>());
            fallback.put("inspectionFocus", new ArrayList<>());
            fallback.put("reportText", answer != null ? answer : "");
            return fallback;
        }
    }

    /**
     * Object安全转字符串列表
     */
    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }
}
