package com.sysware.xian.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.domain.AiAnalysisResult;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import com.sysware.xian.swcloud.domain.AiAnalysisPageResult;
import com.sysware.xian.swcloud.domain.ScadaInfoQueryDto;
import com.sysware.xian.swcloud.fegin.AdminClient;
import com.sysware.xian.swcloud.fegin.MCPXClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 环境分析服务
 * 获取环境设备数据，按标准分析评分，输出不合规设备及整改建议
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvAnalysisService {

    /** 环境分析结果持久化类型标识 */
    public static final String RESULT_TYPE = "env_analysis";

    private final ChatClient.Builder chatClientBuilder;
    private final MCPXClient mcpClient;
    private final AdminClient adminClient;
    private final ObjectMapper objectMapper;
    private final AiAnalysisResultService aiAnalysisResultService;

    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    // ===== 缓存 =====
    private final Map<String, Object> analysisCache = new ConcurrentHashMap<>();
    private volatile boolean analysisRunning = false;
    private volatile long lastAnalysisTime = 0;

    // ========== 环境标准（按优先级组织） ==========

    private static final String ENV_STANDARDS = """
            【第一优先级 — 有位置且有指标依据来源】
            候车室 | 夏季温度 | 26-28℃ | 《铁路旅客运输服务质量规范》
            候车室 | 冬季温度 | 18-20℃ | 《铁路旅客运输服务质量规范》
            候车室 | 湿度 | 40%-60% | 《公共场所卫生指标及限值要求》GB/T37488-2019
            候车室 | 广播音量 | 65-80dB | 以"语音清晰、音量适宜"为原则
            候车室 | 照明照度 | ≥150lx | 《铁路旅客运输服务质量规范》
            候车室 | PM2.5 | ≤75μg/m³ | 《公共场所卫生指标及限值要求》GB/T37488-2019
            候车室 | PM10 | ≤0.15mg/m³ | 《公共场所卫生指标及限值要求》GB/T37488-2019
            卫生间 | 硫化氢 H₂S | ≤0.01mg/m³ | 《城市公共厕所卫生标准》GB/T17217-1998
            卫生间 | 氨 NH₃ | ≤0.3mg/m³ | 《城市公共厕所卫生标准》GB/T17217-1998
            售票处/检票口/服务台 | 照明照度 | ≥200lx | 《铁路旅客运输服务质量规范》
            站台 | 照明照度 | ≥75lx | 《铁路旅客运输服务质量规范》
            行包/安检区域 | 照明照度 | ≥300lx | 《铁路旅客运输服务质量规范》

            【第二优先级 — 有位置但无明确来源】
            公共区域 | 噪音 | 30-90dB
            公共区域 | 异常气味 | 无异常气味
            公共区域 | 环境卫生 | 地面无垃圾，玻璃无灰尘

            【第三优先级 — 通用运营指标（与传感器无关）】
            列车异常 | 停运晚点占比 ＜5%
            厕所保洁 | 100人定量清理
            站内拥堵 | 排队时长小于5分钟
            """;

    private static final String SYSTEM_PROMPT = """
            你是车站环境质量分析专家。请根据以下环境标准，对提供的环境设备数据进行逐台分析评分。
            
            == 环境标准（按优先级排列）==
            %s
            
            == 分析规则 ==
            1. 先用设备名称/位置匹配【第一优先级】中对应位置的标准（如设备名称含"候车室"匹配候车室标准）
            2. 再用数据点名称（dataShortName）匹配具体指标（如"温度"匹配温度标准）
            3. 无法匹配位置时使用【第二优先级】通用标准
            4. 【第三优先级】为运营指标，传感器数据无法直接评估，仅作参考标记
            
            == 输出要求 ==
            严格按照以下JSON格式输出，不要输出markdown代码块，不要输出任何其他内容：
            {
              "batchScore": "本轮评分（满分100分，根据异常数量和严重程度扣分）",
              "deviceResults": [
                {
                  "deviceName": "设备名称",
                  "location": "设备位置",
                  "status": "正常 或 异常",
                  "violations": [
                    {
                      "indicator": "指标名称（如温度、湿度）",
                      "value": "当前测量值",
                      "unit": "单位",
                      "standard": "标准值（如26-28℃）",
                      "source": "依据来源（如《铁路旅客运输服务质量规范》候车室温度标准）",
                      "suggestion": "整改建议（参考标准中的管控措施）"
                    }
                  ]
                }
              ],
              "batchSuggestions": ["整体建议列表，按优先级排列"]
            }
            
            注意：
            - 每个设备单独评估，所有设备（正常和异常）都必须在 deviceResults 中列出，不得遗漏任何设备
            - 正常设备：status 设为"正常"，violations 中记录该设备每个数据点的实测值，每条只含 indicator（指标名）、value（值）、unit（单位）三个字段，不填 standard、source、suggestion
            - 异常设备：status 设为"异常"，violations 中记录超标数据点，每条含 indicator、value、unit、standard（标准值）、source（依据来源）、suggestion（整改建议）六个字段
            - 建议应具体可执行
            """;

    /**
     * 环境分析并缓存（默认 org_id=3, parent_id=1）
     * 由 ScheduledService 每小时调用一次
     */
    public void scheduledEnvAnalysis() {
        log.info("定时环境分析任务开始");
        triggerAnalysis("3", "1");
    }

    /**
     * 触发异步环境分析（后台执行）
     */
    public void triggerAnalysis(String orgId, String parentId) {
        if (analysisRunning) {
            log.info("环境分析已在运行中，跳过");
            return;
        }
        analysisRunning = true;
        executor.execute(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("org_id", orgId);
                params.put("parent_id", parentId);
                AjaxResultVO areaResult = adminClient.getSubareaList(params);
                List<Map<String, Object>> subareas = areaResult.getData();
                if (subareas == null || subareas.isEmpty()) {
                    log.warn("未找到子区域，跳过定时分析");
                    return;
                }

                Map<String, Object> areaResults = new HashMap<>();
                for (Map<String, Object> subarea : subareas) {
                    String name = (String) subarea.get("name");
                    String cascadeId = (String) subarea.get("cascade_id");
                    if (cascadeId == null) continue;
                    try {
                        String resultJson = analyzeEnv(orgId, cascadeId);
                        areaResults.put(cascadeId, Map.of("subareaName", name, "result", resultJson));
                        log.debug("区域[{}]分析完成并缓存", name);
                    } catch (Exception e) {
                        log.error("区域[{}]分析失败", name, e);
                    }
                }

                // 落库到 ai_analysis_result
                saveEnvAnalysisRecord(orgId, areaResults);
                // 同时更新内存缓存（供快速访问）
                analysisCache.put(orgId, areaResults);
                lastAnalysisTime = System.currentTimeMillis();
                log.debug("环境分析完成，共 {} 个区域，已落库并缓存", areaResults.size());
            } catch (Exception e) {
                log.error("后台环境分析失败", e);
            } finally {
                analysisRunning = false;
            }
        });
    }

    /**
     * 从数据库获取最新一条环境分析结果
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCachedResult(String orgId) {
        AiAnalysisPageResult page = aiAnalysisResultService.pageQuery(RESULT_TYPE, 1, 1, null);
        if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
            return null;
        }
        String resultDataRaw = page.getRecords().get(0).getResultDataRaw();
        if (resultDataRaw == null || resultDataRaw.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> results = objectMapper.readValue(resultDataRaw, new TypeReference<Map<String, Object>>() {});
            return Map.of(
                    "status", "ready",
                    "results", results,
                    "lastAnalysisTime", page.getRecords().get(0).getCreateTime()
            );
        } catch (Exception e) {
            log.error("从数据库获取环境分析结果失败", e);
            return null;
        }
    }

    /**
     * 持久化环境分析结果到 ai_analysis_result 表（失败不影响缓存）
     */
    private void saveEnvAnalysisRecord(String orgId, Map<String, Object> areaResults) {
        try {
            AiAnalysisResult record = new AiAnalysisResult();
            record.setType(RESULT_TYPE);
            record.setResultData(objectMapper.writeValueAsString(areaResults));
            record.setRemake("环境分析");
            record.setCreateTime(LocalDateTime.now());
            aiAnalysisResultService.save(record);
            log.info("环境分析结果已持久化: id={}, 区域数={}", record.getId(), areaResults.size());
        } catch (Exception e) {
            log.error("环境分析结果持久化失败", e);
        }
    }

    public boolean isAnalysisRunning() {
        return analysisRunning;
    }

    /**
     * 获取最新一次环境分析中各区域的设备统计
     *
     * @return 区域统计列表，每条含 subareaCid、subareaName、totalDevices、abnormalDevices；无记录返回空列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getEnvAnalysisStats() {
        AiAnalysisPageResult page = aiAnalysisResultService.pageQuery(RESULT_TYPE, 1, 1, null);
        if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
            return List.of();
        }
        String resultDataRaw = page.getRecords().get(0).getResultDataRaw();
        if (resultDataRaw == null || resultDataRaw.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> results = objectMapper.readValue(resultDataRaw, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> areas = new ArrayList<>();
            for (Map.Entry<String, Object> entry : results.entrySet()) {
                String cascadeId = entry.getKey();
                Object areaValue = entry.getValue();
                if (!(areaValue instanceof Map)) continue;
                Map<String, Object> areaData = (Map<String, Object>) areaValue;
                String subareaName = (String) areaData.get("subareaName");
                String resultJson = (String) areaData.get("result");
                if (resultJson == null || resultJson.isBlank()) continue;
                try {
                    Map<String, Object> result = objectMapper.readValue(resultJson, new TypeReference<Map<String, Object>>() {});
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("subareaCid", cascadeId);
                    stats.put("subareaName", subareaName);
                    stats.put("totalDevices", result.get("totalDevices"));
                    stats.put("abnormalDevices", result.get("abnormalDevices"));
                    areas.add(stats);
                } catch (Exception e) {
                    log.warn("解析区域[{}]的result失败: {}", cascadeId, e.getMessage());
                }
            }
            return areas;
        } catch (Exception e) {
            log.error("获取环境分析统计失败", e);
            return List.of();
        }
    }

    /**
     * 环境分析主入口
     *
     * @param orgId      组织ID
     * @param subareaCid 可选区域过滤
     * @return 汇总分析结果JSON字符串
     */
    public String analyzeEnv(String orgId, String subareaCid) {
        log.debug("开始环境分析: orgId={}, subareaCid={}", orgId, subareaCid);

        // 1. 全量拉取环境设备数据
        List<Map<String, Object>> allDevices = fetchAllDevices(orgId, subareaCid);
        log.debug("获取到环境设备总数: {}", allDevices.size());

        if (allDevices.isEmpty()) {
            return buildEmptyResult();
        }

        // 2. 分批
        int batchSize = 10;
        List<List<Map<String, Object>>> batches = partition(allDevices, batchSize);
        log.debug("分批数量: {}批（每批{}台）", batches.size(), batchSize);

        // 3. 多线程AI分析
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        for (int i = 0; i < batches.size(); i++) {
            int batchIndex = i;
            List<Map<String, Object>> batch = batches.get(i);
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return analyzeBatch(batch, batchIndex + 1, batches.size());
                } catch (Exception e) {
                    log.error("第{}批AI分析失败", batchIndex + 1, e);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("batchScore", 0);
                    errorResult.put("deviceResults", new ArrayList<>());
                    errorResult.put("batchSuggestions", List.of("第" + (batchIndex + 1) + "批分析失败: " + e.getMessage()));
                    return errorResult;
                }
            }, executor));
        }

        // 4. 汇总
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<Map<String, Object>> allDeviceResults = new ArrayList<>();
        List<String> allSuggestions = new ArrayList<>();
        double totalScoreSum = 0;
        int validBatchCount = 0;
        int abnormalCount = 0;

        for (CompletableFuture<Map<String, Object>> future : futures) {
            try {
                Map<String, Object> batchResult = future.get();
                Object scoreObj = batchResult.get("batchScore");
                if (scoreObj != null) {
                    try {
                        totalScoreSum += Double.parseDouble(scoreObj.toString());
                        validBatchCount++;
                    } catch (NumberFormatException ignored) {
                    }
                }

                Object deviceResults = batchResult.get("deviceResults");
                if (deviceResults instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) deviceResults;
                    for (Map<String, Object> dr : results) {
                        allDeviceResults.add(dr);
                        if ("异常".equals(dr.get("status"))) {
                            abnormalCount++;
                        }
                    }
                }

                Object suggestions = batchResult.get("batchSuggestions");
                if (suggestions instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> sugs = (List<String>) suggestions;
                    allSuggestions.addAll(sugs);
                }
            } catch (Exception e) {
                log.error("获取批量分析结果失败", e);
            }
        }

        double avgScore = validBatchCount > 0 ? totalScoreSum / validBatchCount : 0;

        // 5. 构建最终结果
        Map<String, Object> result = new HashMap<>();
        result.put("totalScore", Math.round(avgScore * 10.0) / 10.0);
        result.put("totalDevices", allDevices.size());
        result.put("abnormalDevices", abnormalCount);
        result.put("deviceResults", allDeviceResults);
        result.put("suggestions", allSuggestions);

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("序列化最终结果失败", e);
            return buildEmptyResult();
        }
    }

    /**
     * 全量拉取环境设备数据（支持分页）
     */
    private List<Map<String, Object>> fetchAllDevices(String orgId, String subareaCid) {
        List<Map<String, Object>> allDevices = new ArrayList<>();
        int page = 1;
        int limit = 1000;
        long totalCount = 0;

        do {
            ScadaInfoQueryDto queryDto = ScadaInfoQueryDto.builder()
                    .page(page)
                    .limit(limit)
                    .orgId(Long.parseLong(orgId))
                    .systemType("0.005")
                    .subareaCid(subareaCid)
                    .build();

            try {
                com.sysware.xian.swcloud.domain.AjaxResultVO response = mcpClient.StateListPageV2(queryDto);
                if (response != null && response.isOk()) {
                    Object dataObj = response.get("data");
                    Object countObj = response.get("count");
                    if (dataObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> devices = (List<Map<String, Object>>) dataObj;
                        allDevices.addAll(devices);
                    }
                    if (countObj instanceof Number) {
                        totalCount = ((Number) countObj).longValue();
                    }
                } else {
                    log.warn("获取环境设备列表失败: page={}, response={}", page, response);
                    break;
                }
            } catch (Exception e) {
                log.error("获取环境设备列表异常: page={}", page, e);
                break;
            }

            page++;
        } while ((page - 1) * limit < totalCount);

        return allDevices;
    }

    /**
     * 单批AI分析
     */
    private Map<String, Object> analyzeBatch(List<Map<String, Object>> devices, int batchIndex, int totalBatches) {
        log.debug("开始分析第{}/{}批，设备数: {}", batchIndex, totalBatches, devices.size());

        // 构建提示词
        StringBuilder prompt = new StringBuilder();
        prompt.append("请分析以下第").append(batchIndex).append("批环境设备数据");
        prompt.append("（共").append(totalBatches).append("批，当前第").append(batchIndex).append("批/共").append(devices.size()).append("台设备）：\n\n");
        prompt.append("设备列表：\n");

        try {
            prompt.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(devices));
        } catch (Exception e) {
            prompt.append(devices.toString());
        }

        prompt.append("\n\n请根据环境标准逐设备分析，输出JSON格式结果。");
        prompt.append("输出时必须在 violations 的 source 字段中标注采用了哪条标准和依据来源。");

        // 构建系统提示
        String systemPrompt = SYSTEM_PROMPT.formatted(ENV_STANDARDS);

        // 调用AI
        ChatClient chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .build();

        String answer = chatClient.prompt()
                .user(prompt.toString())
                .call()
                .content();

        log.debug("第{}批AI分析完成", batchIndex);

        // 解析JSON
        return parseBatchResult(answer);
    }

    /**
     * 解析单批AI返回的JSON
     */
    private Map<String, Object> parseBatchResult(String answer) {
        try {
            String json = answer;
            if (answer.contains("```json")) {
                json = answer.substring(answer.indexOf("```json") + 7, answer.lastIndexOf("```"));
            } else if (answer.contains("```")) {
                json = answer.substring(answer.indexOf("```") + 3, answer.lastIndexOf("```"));
            }
            json = json.trim();

            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("解析AI分析结果失败: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("batchScore", 0);
            errorResult.put("deviceResults", new ArrayList<>());
            errorResult.put("batchSuggestions", List.of("AI分析结果解析失败"));
            return errorResult;
        }
    }

    /**
     * 空结果
     */
    private String buildEmptyResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalScore", 0);
        result.put("totalDevices", 0);
        result.put("abnormalDevices", 0);
        result.put("deviceResults", new ArrayList<>());
        result.put("suggestions", new ArrayList<>());
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 手动分批
     */
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
