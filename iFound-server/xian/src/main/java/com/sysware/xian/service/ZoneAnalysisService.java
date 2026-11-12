package com.sysware.xian.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.domain.AgentConfig;
import com.sysware.xian.domain.AllZoneAnalysisRequest;
import com.sysware.xian.domain.CommonAnalysisRequest;
import com.sysware.xian.domain.CommonAnalysisResponse;
import com.sysware.xian.domain.DeviceAlarmRequest;
import com.sysware.xian.domain.DeviceAlarmResponse;
import com.sysware.xian.domain.ZoneAnalysisRequest;
import com.sysware.xian.domain.ZoneAnalysisResponse;
import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分区态势分析服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneAnalysisService {

    private final List<McpSyncClient> mcpSyncClients;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final AgentConfigService agentConfigService;

    private String deviceRulesText = "";

    @PostConstruct
    public void loadDeviceRules() {
        try (InputStream is = new ClassPathResource("device-rules.yml").getInputStream()) {
            deviceRulesText = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.debug("设备联动决策规则 device-rules.yml 加载成功");
        } catch (Exception e) {
            log.warn("加载 device-rules.yml 失败，决策将仅依赖AI自主判断: {}", e.getMessage());
            deviceRulesText = "";
        }
    }

    /**
     * 动态构建系统提示，从AgentConfigService获取可用的Agent列表及其业务域
     * 针对单条异常数据进行分析，只生成1个派发任务
     */
    private String buildSystemPrompt() {
        List<AgentConfig> agents = agentConfigService.listAvailable();
        StringBuilder agentRules = new StringBuilder();
        agentRules.append("Agent匹配规则（根据业务域匹配对应的Agent）：\n");
        for (AgentConfig agent : agents) {
            if (agent.getBusinessDomains() != null && !agent.getBusinessDomains().isEmpty()) {
                agentRules.append("- 业务域[")
                        .append(agent.getBusinessDomains())
                        .append("] → ")
                        .append(agent.getAgentName())
                        .append("(")
                        .append(agent.getAgentId())
                        .append(")\n");
            }
        }

        return """
                你是IoT系统的智能助手，负责针对分区中的单条异常数据进行分析并生成派发任务。
                请根据提供的单条异常数据进行分析，并严格按照以下JSON格式返回结果，不要胡编乱造，所有数据都有据可依，不要输出任何其他内容：
                {
                  "zoneAnalysis": "针对该条异常的分析总结，100字以内，描述问题及影响",
                  "suggestion": "处理建议，50字以内，给出具体处理措施",
                  "dispatchTasks": [
                    {
                      "businessDomain": "维修工单、能耗、环境、客流 (设备故障/告警则需要派发给设备运维Agent属于维修工单，能耗阈值/异常则需要派发给能源管理Agent属于能耗，温度过高/二氧化碳过高则需要派发给能源管理Agent属于环境，客流拥堵则需要派发给车站运营Agent属于客流)",
                      "level": "高 或 中 或 低",
                      "problemDesc": "问题描述，如'空调kt2(id:123)故障，转速为0'",
                      "thinkingOverview": "思考过程概览，50字以内",
                      "problemSummary": "问题总结，50字以内",
                      "decisionContent": "决策内容，必须使用设备名称而非设备ID。如'给设备空调kt2(id:123)派单维修'、'给环境监测调节'、'给拥堵区域增派人手'",
                      "decisionData": "决策数据，直接填写设备ID纯字符串如123；不涉及设备时为空字符串",
                      "expectedEffect": "预期成效，如'恢复设备正常运行，降低能耗5%%'",
                      "targetAgentId": "要派发的子AgentId",
                      "targetAgentName": "目标Agent名称",
                      "taskType": "任务类型: hint-提示, collaboration-协同方案"
                    }
                  ],
                  "envScore": "环境评分，如'90分'。仅当本条异常为环境异常时据此扣分，否则为100分",
                  "envStatus": "环境状态，'正常'或'注意'。仅当本条为环境异常时为'注意'"
                }
                重要：dispatchTasks 数组只生成1个任务项，即针对当前这条异常给出最合适的处置任务，不要生成多个。
                %s
                匹配说明：
                - 根据业务域(businessDomain)匹配对应的Agent
                - 一般性提示/正常情况 → taskType为hint，无需派发
                - 需要跨Agent协同处理的问题 → taskType为collaboration，需要派发
                """.formatted(agentRules.toString(), deviceRulesText);
    }

    /**
     * 分析分区态势数据
     */
    public ZoneAnalysisResponse analyze(ZoneAnalysisRequest request) {
        log.debug("开始分析分区态势: ZoneName={}, anomalyType={}",
                request.getZoneName(), request.getAnomalyType());

        // 构建 AI 提示词
        String prompt = buildAnalysisPrompt(request);

        // 动态构建系统提示，包含从AgentConfigService获取的Agent匹配规则
        String systemPrompt = buildSystemPrompt();

        // 调用 AI
        ChatClient chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .build();

        List<ToolCallback> callbacks = SyncMcpToolCallbackProvider.syncToolCallbacks(mcpSyncClients);

        String answer = chatClient.prompt()
                .user(prompt)
                .toolCallbacks(callbacks)
                .call()
                .content();

        log.debug("AI分析数据: {}", request);
        log.debug("AI分析提示词: {}", prompt);
        log.debug("AI分析结果: {}", answer);

        // 解析 JSON 结果
        return parseResponse(answer);
    }

    /**
     * 构建单条异常分析提示词（按 anomalyType 拼装对应异常描述）
     */
    private String buildAnalysisPrompt(ZoneAnalysisRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下分区单条异常数据：\n\n");
        sb.append("分区名称: ").append(request.getZoneName()).append("\n");
        sb.append("分区ID: ").append(request.getSubareaCid()).append("\n");
        sb.append("异常类型: ").append(request.getAnomalyType()).append("\n\n");

        String anomalyType = request.getAnomalyType();
        if ("env".equalsIgnoreCase(anomalyType) && request.getEnvAlarm() != null) {
            Map<String, Object> alarm = request.getEnvAlarm();
            sb.append("环境告警:\n");
            sb.append("  指标: ").append(alarm.get("item")).append("\n");
            sb.append("  当前值: ").append(alarm.get("value")).append("\n");
            sb.append("  状态/说明: ").append(alarm.getOrDefault("status", alarm.get("message"))).append("\n");
        } else if ("device".equalsIgnoreCase(anomalyType) && request.getDevice() != null) {
            ZoneAnalysisRequest.DeviceItem device = request.getDevice();
            sb.append("异常设备:\n");
            sb.append("  设备名称: ").append(device.getName()).append("\n");
            sb.append("  设备ID: ").append(device.getId()).append("\n");
            sb.append("  设备类型: ").append(device.getType()).append("\n");
            sb.append("  状态: ").append(device.getStatus()).append("\n");
            sb.append("  位置: ").append(device.getLocation()).append("\n");
            if (device.getCurrentData() != null) {
                sb.append("  实时数据:\n");
                for (Map<String, String> data : device.getCurrentData()) {
                    sb.append("    ").append(data.get("label")).append(": ").append(data.get("value")).append("\n");
                }
            }
        } else if ("energy".equalsIgnoreCase(anomalyType) && request.getEnergy() != null) {
            Map<String, Object> energy = request.getEnergy();
            sb.append("能耗异常:\n");
            sb.append("  说明: ").append(energy.get("message")).append("\n");
            sb.append("  当前值: ").append(energy.get("value")).append("\n");
            sb.append("  状态: ").append(energy.get("status")).append("\n");
        } else if ("keliu".equalsIgnoreCase(anomalyType) && request.getKeliu() != null) {
            Map<String, Object> keliu = request.getKeliu();
            sb.append("客流异常:\n");
            sb.append("  说明: ").append(keliu.get("message")).append("\n");
            sb.append("  当前值: ").append(keliu.get("value")).append("\n");
            sb.append("  状态: ").append(keliu.get("status")).append("\n");
        }

        // 补充描述
        if (request.getDesc() != null && !request.getDesc().isEmpty()) {
            sb.append("\n异常摘要: ").append(String.join("，", request.getDesc())).append("\n");
        }

        sb.append("\n请针对该条异常按照系统提示中的JSON格式返回分析结果，dispatchTasks只生成1个任务项。");
        return sb.toString();
    }

    /**
     * 解析 AI 返回的 JSON
     */
    private ZoneAnalysisResponse parseResponse(String answer) {
        try {
            // 提取 JSON 部分（AI 可能包裹在 markdown 代码块中）
            String json = answer;
            if (answer.contains("```json")) {
                json = answer.substring(answer.indexOf("```json") + 7, answer.lastIndexOf("```"));
            } else if (answer.contains("```")) {
                json = answer.substring(answer.indexOf("```") + 3, answer.lastIndexOf("```"));
            }
            json = json.trim();

            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});

            ZoneAnalysisResponse response = new ZoneAnalysisResponse();
            response.setZoneAnalysis((String) map.get("zoneAnalysis"));
            response.setSuggestion((String) map.get("suggestion"));
            response.setEnvScore((String) map.get("envScore"));
            response.setEnvStatus((String) map.get("envStatus"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> actions = (List<Map<String, Object>>) map.get("anomalyActions");
            if (actions != null) {
                List<ZoneAnalysisResponse.AnomalyAction> actionList = new ArrayList<>();
                for (Map<String, Object> action : actions) {
                    ZoneAnalysisResponse.AnomalyAction a = new ZoneAnalysisResponse.AnomalyAction();
                    a.setTitle((String) action.get("title"));
                    a.setDetail((String) action.get("detail"));
                    a.setType((String) action.get("type"));
                    @SuppressWarnings("unchecked")
                    List<String> buttons = (List<String>) action.get("buttons");
                    a.setButtons(buttons);
                    actionList.add(a);
                }
                response.setAnomalyActions(actionList);
            }

            // 解析派发任务
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) map.get("dispatchTasks");
            if (tasks != null) {
                List<ZoneAnalysisResponse.DispatchTaskItem> taskList = new ArrayList<>();
                for (Map<String, Object> task : tasks) {
                    ZoneAnalysisResponse.DispatchTaskItem t = new ZoneAnalysisResponse.DispatchTaskItem();
                    t.setBusinessDomain((String) task.get("businessDomain"));
                    t.setLevel((String) task.get("level"));
                    t.setProblemDesc((String) task.get("problemDesc"));
                    t.setThinkingOverview((String) task.get("thinkingOverview"));
                    t.setProblemSummary((String) task.get("problemSummary"));
                    t.setDecisionContent((String) task.get("decisionContent"));
                    Object decisionDataObj = task.get("decisionData");
                    if (decisionDataObj != null) {
                        if (decisionDataObj instanceof String) {
                            // AI 返回的是字符串，去掉外层多余引号
                            String ddStr = ((String) decisionDataObj).trim();
                            if (ddStr.startsWith("\"") && ddStr.endsWith("\"") && ddStr.length() > 2) {
                                ddStr = ddStr.substring(1, ddStr.length() - 1);
                            }
                            t.setDecisionData(ddStr);
                        } else {
                            // AI 返回的是数组/对象，序列化为字符串
                            try {
                                t.setDecisionData(objectMapper.writeValueAsString(decisionDataObj));
                            } catch (Exception e) {
                                log.warn("序列化 decisionData 失败: {}", e.getMessage());
                            }
                        }
                    }
                    t.setExpectedEffect((String) task.get("expectedEffect"));
                    t.setTargetAgentId((String) task.get("targetAgentId"));
                    t.setTargetAgentName((String) task.get("targetAgentName"));
                    t.setTaskType((String) task.get("taskType"));
                    taskList.add(t);
                }
                response.setDispatchTasks(taskList);
            }

            return response;
        } catch (Exception e) {
            log.error("解析AI分析结果失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 分析设备告警数据
     */
    public DeviceAlarmResponse analyzeDeviceAlarm(DeviceAlarmRequest request) {
        log.debug("开始分析设备告警: deviceName={}, eventLevel={}, happenTime={}",
                request.getDeviceName(), request.getEventLevel(), request.getHappenTime());

        // 构建 AI 提示词
        String prompt = buildDeviceAlarmPrompt(request);

        // 动态构建系统提示，包含从AgentConfigService获取的Agent匹配规则
        String systemPrompt = buildDeviceAlarmSystemPrompt();

        // 调用 AI
        ChatClient chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .build();

        List<ToolCallback> callbacks = SyncMcpToolCallbackProvider.syncToolCallbacks(mcpSyncClients);

        String answer = chatClient.prompt()
                .user(prompt)
                .toolCallbacks(callbacks)
                .call()
                .content();

        log.debug("设备告警AI分析数据: {}", request);
        log.debug("设备告警AI分析提示词: {}", prompt);
        log.debug("设备告警AI分析结果: {}", answer);

        // 解析 JSON 结果
        return parseDeviceAlarmResponse(answer);
    }

    /**
     * 构建设备告警分析的系统提示
     */
    private String buildDeviceAlarmSystemPrompt() {
        List<AgentConfig> agents = agentConfigService.listAvailable();
        StringBuilder agentRules = new StringBuilder();
        agentRules.append("Agent匹配规则（根据业务域匹配对应的Agent）：\n");
        for (AgentConfig agent : agents) {
            if (agent.getBusinessDomains() != null && !agent.getBusinessDomains().isEmpty()) {
                agentRules.append("- 业务域[")
                        .append(agent.getBusinessDomains())
                        .append("] → ")
                        .append(agent.getAgentName())
                        .append("(")
                        .append(agent.getAgentId())
                        .append(")\n");
            }
        }

        return """
                你是IoT系统的智能助手，负责分析设备告警数据并返回结构化结果。
                
                请根据提供的设备告警信息进行分析，并严格按照以下JSON格式返回结果，不要胡编乱造，所有数据都有据可依，不要输出任何其他内容：
                {
                  "alarmAnalysis": "告警分析总结，100字以内，描述该设备告警情况及影响",
                  "suggestion": "处理建议，50字以内，给出具体处理措施。",
                  "dispatchTasks": [
                    {
                       "businessDomain": "维修工单、能耗、环境、客流 (设备故障/告警则需要派发给设备运维Agent属于维修工单，能耗阈值/异常则需要派发给能源管理Agent属于能耗，温度过高/二氧化碳过高则需要派发给能源管理Agent属于环境，客流拥堵则需要派发给车站运营Agent属于客流)",
                      "level": "高 或 中 或 低",
                      "problemDesc": "问题描述，如'设备XXX发生告警，告警内容...'",
                      "thinkingOverview": "思考过程概览，50字以内",
                      "problemSummary": "问题总结，50字以内",
                      "decisionContent": "决策内容，必须使用设备名称而非设备ID。如'给设备空调kt2(id:123)派单维修'、'给环境监测调节'、'给拥堵区域增派人手'", 
                      "decisionData": "决策数据，直接填写设备ID纯字符串如123；不涉及设备时为空字符串",
                      "expectedEffect": "预期成效，如'恢复设备正常运行'",
                      "targetAgentId": "要派发的子AgentId",
                      "targetAgentName": "目标Agent名称",
                      "taskType": "任务类型: hint-提示, collaboration-协同方案"
                    }
                  ]
                }
                %s
                匹配说明：
                - 根据业务域(businessDomain)匹配对应的Agent
                - 一般性提示/正常情况 → taskType为hint，无需派发
                - 需要跨Agent协同处理的问题 → taskType为collaboration，需要派发
                - 事件等级3为故障，2为告警，请根据等级调整任务level
                """.formatted(agentRules.toString(), deviceRulesText);
    }

    /**
     * 构建设备告警分析提示词
     */
    private String buildDeviceAlarmPrompt(DeviceAlarmRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下设备告警数据：\n\n");
        sb.append("设备名称: ").append(request.getDeviceName()).append("\n");
        sb.append("设备ID: ").append(request.getDeviceId()).append("\n");
        sb.append("分区ID: ").append(request.getSubareaCid()).append("\n");
        sb.append("系统类型: ").append(request.getSystemType()).append("\n");
        sb.append("事件等级: ").append(request.getEventLevel()).append(" (3=故障, 2=告警)\n");
        sb.append("事件状态: ").append(request.getEventState()).append(" (0=恢复, 1=异常)\n");
        sb.append("处理状态: ").append(request.getProcessState()).append(" (0=未处理, 1=已处理)\n");
        sb.append("发生时间: ").append(request.getHappenTime()).append("\n");
//        sb.append("接收时间: ").append(request.getReceiveTime()).append("\n");

        if (request.getEventInfo() != null) {
            sb.append("事件信息: ").append(request.getEventInfo()).append("\n");
        }
        if (request.getLocation() != null) {
            sb.append("位置: ").append(request.getLocation()).append("\n");
        }
//        if (request.getEventType() != null) {
//            sb.append("事件类型: ").append(request.getEventType()).append("\n");
//        }
//        if (request.getDataKey() != null) {
//            sb.append("数据Key: ").append(request.getDataKey()).append("\n");
//        }
//        if (request.getWarningLevel() != null) {
//            sb.append("告警等级: ").append(request.getWarningLevel()).append("\n");
//        }

        sb.append("\n请按照系统提示中的JSON格式返回分析结果。");
        return sb.toString();
    }

    /**
     * 解析设备告警 AI 返回的 JSON
     */
    private DeviceAlarmResponse parseDeviceAlarmResponse(String answer) {
        try {
            // 提取 JSON 部分
            String json = answer;
            if (answer.contains("```json")) {
                json = answer.substring(answer.indexOf("```json") + 7, answer.lastIndexOf("```"));
            } else if (answer.contains("```")) {
                json = answer.substring(answer.indexOf("```") + 3, answer.lastIndexOf("```"));
            }
            json = json.trim();

            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});

            DeviceAlarmResponse response = new DeviceAlarmResponse();
            response.setAlarmAnalysis((String) map.get("alarmAnalysis"));
            response.setSuggestion((String) map.get("suggestion"));

            // 解析派发任务
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) map.get("dispatchTasks");
            if (tasks != null) {
                List<DeviceAlarmResponse.DispatchTaskItem> taskList = new ArrayList<>();
                for (Map<String, Object> task : tasks) {
                    DeviceAlarmResponse.DispatchTaskItem t = new DeviceAlarmResponse.DispatchTaskItem();
                    t.setBusinessDomain((String) task.get("businessDomain"));
                    t.setLevel((String) task.get("level"));
                    t.setProblemDesc((String) task.get("problemDesc"));
                    t.setThinkingOverview((String) task.get("thinkingOverview"));
                    t.setProblemSummary((String) task.get("problemSummary"));
                    t.setDecisionContent((String) task.get("decisionContent"));
                    Object decisionDataObj = task.get("decisionData");
                    if (decisionDataObj != null) {
                        if (decisionDataObj instanceof String) {
                            // AI 返回的是字符串，去掉外层多余引号
                            String ddStr = ((String) decisionDataObj).trim();
                            if (ddStr.startsWith("\"") && ddStr.endsWith("\"") && ddStr.length() > 2) {
                                ddStr = ddStr.substring(1, ddStr.length() - 1);
                            }
                            t.setDecisionData(ddStr);
                        } else {
                            // AI 返回的是数组/对象，序列化为字符串
                            try {
                                t.setDecisionData(objectMapper.writeValueAsString(decisionDataObj));
                            } catch (Exception e) {
                                log.warn("序列化 decisionData 失败: {}", e.getMessage());
                            }
                        }
                    }
                    t.setExpectedEffect((String) task.get("expectedEffect"));
                    t.setTargetAgentId((String) task.get("targetAgentId"));
                    t.setTargetAgentName((String) task.get("targetAgentName"));
                    t.setTaskType((String) task.get("taskType"));
                    taskList.add(t);
                }
                response.setDispatchTasks(taskList);
            }

            return response;
        } catch (Exception e) {
            log.error("解析设备告警AI分析结果失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 综合分析所有分区态势，生成300字以内的总结文本
     */
    public String analyzeAllZones(AllZoneAnalysisRequest request) {
        log.debug("开始综合分析 {} 个分区", request.getZones() != null ? request.getZones().size() : 0);

        String prompt = buildAllZonesPrompt(request);

        String systemPrompt = """
                你是IoT系统的智能助手，负责综合分析所有分区的态势数据。
                请根据各分区运行数据生成一段综合分析文本，要求：
                1. 300字以内
                2. 先总结整体运行状况
                3. 重点描述异常分区的问题和建议
                4. 最后给出优先级处理建议
                5. 只返回纯文本，不要JSON格式，不要markdown标记
                """;

        ChatClient chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .build();

        List<ToolCallback> callbacks = SyncMcpToolCallbackProvider.syncToolCallbacks(mcpSyncClients);

        String answer = chatClient.prompt()
                .user(prompt)
                .toolCallbacks(callbacks)
                .call()
                .content();

        log.debug("AI综合分析结果: {}", answer);
        return answer;
    }

    /**
     * 构建全分区综合分析提示词
     */
    private String buildAllZonesPrompt(AllZoneAnalysisRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("请综合分析以下 ").append(request.getZones().size()).append(" 个分区的态势数据：\n\n");

        for (AllZoneAnalysisRequest.ZoneSummary zone : request.getZones()) {
            sb.append("【").append(zone.getName()).append("】\n");
            sb.append("状态: ").append(zone.getStatus()).append("\n");
            sb.append("环境评分: ").append(zone.getEnvScore()).append(", 环境状态: ").append(zone.getEnvStatus()).append("\n");
            sb.append("设备状况: ").append(zone.getDeviceStatus()).append("\n");
            sb.append("客流密度: ").append(zone.getPassengerDensity()).append(", 状态: ").append(zone.getPassengerStatus()).append("\n");
            sb.append("今日能耗: ").append(zone.getEnergyToday()).append(", 状态: ").append(zone.getEnergyStatus()).append("\n");

            if (zone.getDesc() != null && !zone.getDesc().isEmpty()) {
                sb.append("异常摘要: ").append(String.join("，", zone.getDesc())).append("\n");
            }

            if (zone.getEnvAlarms() != null && !zone.getEnvAlarms().isEmpty()) {
                sb.append("环境告警:\n");
                for (AllZoneAnalysisRequest.ZoneSummary.EnvAlarm e : zone.getEnvAlarms()) {
                    sb.append("  - ").append(e.getItem()).append(": ").append(e.getValue())
                            .append(" (").append(e.getStatus()).append(")\n");
                }
            }

            if (zone.getAnomalyDevices() != null && !zone.getAnomalyDevices().isEmpty()) {
                sb.append("异常设备:\n");
                for (AllZoneAnalysisRequest.ZoneSummary.DeviceItem d : zone.getAnomalyDevices()) {
                    sb.append("  - ").append(d.getName()).append(" (").append(d.getType()).append(")")
                            .append(" 状态: ").append(d.getStatus())
                            .append(" 位置: ").append(d.getLocation()).append("\n");
                }
            }

            sb.append("建议: ").append(zone.getSuggestion()).append("\n\n");
        }

        sb.append("\n请生成300字以内的综合分析文本。");
        return sb.toString();
    }

    /**
     * 通用数据分析
     * 智能识别输入内容，自动判断是区域分析、设备告警还是通用问答
     */
    public CommonAnalysisResponse analyzeCommon(CommonAnalysisRequest request) {
        log.info("开始通用数据分析: content={}", request.getContent());

        // 动态构建系统提示，包含从AgentConfigService获取的Agent匹配规则
        String systemPrompt = buildCommonAnalysisSystemPrompt();

        // 调用 AI
        ChatClient chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .build();

        List<ToolCallback> callbacks = SyncMcpToolCallbackProvider.syncToolCallbacks(mcpSyncClients);

        String answer = chatClient.prompt()
                .user("请分析以下内容：\n\n" + request.getContent())
                .toolCallbacks(callbacks)
                .call()
                .content();

        log.info("通用数据分析结果: {}", answer);

        // 解析 JSON 结果
        return parseCommonResponse(answer);
    }

    /**
     * 构建通用数据分析的系统提示
     */
    private String buildCommonAnalysisSystemPrompt() {
        List<AgentConfig> agents = agentConfigService.listAvailable();
        StringBuilder agentRules = new StringBuilder();
        agentRules.append("Agent匹配规则（根据业务域匹配对应的Agent）：\n");
        for (AgentConfig agent : agents) {
            if (agent.getBusinessDomains() != null && !agent.getBusinessDomains().isEmpty()) {
                agentRules.append("- 业务域[")
                        .append(agent.getBusinessDomains())
                        .append("] → ")
                        .append(agent.getAgentName())
                        .append("(")
                        .append(agent.getAgentId())
                        .append(")\n");
            }
        }

        return """
                你是IoT系统的智能助手，负责智能识别用户输入并进行分析。
                请根据用户输入的内容进行智能识别，判断数据类型，并严格按照以下JSON格式返回结果，不要胡编乱造，所有数据都有据可依，不要输出任何其他内容：
                {
                  "dataType": "识别到的数据类型：zone-区域分析, device-设备告警, energy-能耗分析, environment-环境分析, general-通用问答",
                  "analysis": "分析结果，300字以内。根据识别到的数据类型进行相应分析",
                  "suggestion": "处理建议，100字以内。有异常时给出处理建议，正常时给出维持建议",
                  "dispatchTasks": [
                    {
                       "businessDomain": "维修工单、能耗、环境、客流 (设备故障/告警则需要派发给设备运维Agent属于维修工单，能耗阈值/异常则需要派发给能源管理Agent属于能耗，温度过高/二氧化碳过高则需要派发给能源管理Agent属于环境，客流拥堵则需要派发给车站运营Agent属于客流)",
                      "level": "高 或 中 或 低",
                      "problemDesc": "问题描述",
                      "thinkingOverview": "思考过程概览，50字以内",
                      "problemSummary": "问题总结，50字以内",
                      "decisionContent": "决策内容，必须使用设备名称而非设备ID。如'给故障设备空调kt2(id:123)派单维修'、'给环境监测调节'、'给拥堵区域增派人手'",
                      "decisionData": "决策数据，直接填写设备ID纯字符串如123；不涉及设备时为空字符串",
                      "expectedEffect": "预期成效",
                      "targetAgentId": "要派发的子AgentId",
                      "targetAgentName": "目标Agent名称",
                      "taskType": "任务类型: hint-提示, collaboration-协同方案"
                    }
                  ]
                }
                %s
                匹配说明：
                - 根据业务域(businessDomain)匹配对应的Agent
                - 一般性提示/正常情况 → taskType为hint，无需派发
                - 需要跨Agent协同处理的问题 → taskType为collaboration，需要派发
                - 如果用户输入不包含具体区域或设备信息，dataType设为general，dispatchTasks可为空数组
                """.formatted(agentRules.toString(), deviceRulesText);
    }

    /**
     * 解析通用 AI 返回的 JSON
     */
    private CommonAnalysisResponse parseCommonResponse(String answer) {
        try {
            // 提取 JSON 部分
            String json = answer;
            if (answer.contains("```json")) {
                json = answer.substring(answer.indexOf("```json") + 7, answer.lastIndexOf("```"));
            } else if (answer.contains("```")) {
                json = answer.substring(answer.indexOf("```") + 3, answer.lastIndexOf("```"));
            }
            json = json.trim();

            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});

            CommonAnalysisResponse response = new CommonAnalysisResponse();
            response.setAnalysis((String) map.get("analysis"));
            response.setSuggestion((String) map.get("suggestion"));
            response.setDataType((String) map.get("dataType"));

            // 解析派发任务
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) map.get("dispatchTasks");
            if (tasks != null) {
                List<ZoneAnalysisResponse.DispatchTaskItem> taskList = new ArrayList<>();
                for (Map<String, Object> task : tasks) {
                    ZoneAnalysisResponse.DispatchTaskItem t = new ZoneAnalysisResponse.DispatchTaskItem();
                    t.setBusinessDomain((String) task.get("businessDomain"));
                    t.setLevel((String) task.get("level"));
                    t.setProblemDesc((String) task.get("problemDesc"));
                    t.setThinkingOverview((String) task.get("thinkingOverview"));
                    t.setProblemSummary((String) task.get("problemSummary"));
                    t.setDecisionContent((String) task.get("decisionContent"));
                    Object decisionDataObj = task.get("decisionData");
                    if (decisionDataObj != null) {
                        if (decisionDataObj instanceof String) {
                            // AI 返回的是字符串，去掉外层多余引号
                            String ddStr = ((String) decisionDataObj).trim();
                            if (ddStr.startsWith("\"") && ddStr.endsWith("\"") && ddStr.length() > 2) {
                                ddStr = ddStr.substring(1, ddStr.length() - 1);
                            }
                            t.setDecisionData(ddStr);
                        } else {
                            // AI 返回的是数组/对象，序列化为字符串
                            try {
                                t.setDecisionData(objectMapper.writeValueAsString(decisionDataObj));
                            } catch (Exception e) {
                                log.warn("序列化 decisionData 失败: {}", e.getMessage());
                            }
                        }
                    }
                    t.setExpectedEffect((String) task.get("expectedEffect"));
                    t.setTargetAgentId((String) task.get("targetAgentId"));
                    t.setTargetAgentName((String) task.get("targetAgentName"));
                    t.setTaskType((String) task.get("taskType"));
                    taskList.add(t);
                }
                response.setDispatchTasks(taskList);
            }

            return response;
        } catch (Exception e) {
            log.error("解析通用数据分析结果失败: {}", e.getMessage(), e);
            return null;
        }
    }
}
