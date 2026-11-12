package com.sysware.xian.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 设备告警AI分析响应
 */
@Data
@Schema(description = "设备告警AI分析响应")
public class DeviceAlarmResponse {
    @Schema(description = "告警分析总结，100字以内")
    private String alarmAnalysis;

    @Schema(description = "处理建议，50字以内")
    private String suggestion;

    @Schema(description = "AI生成的派发任务列表")
    private List<DispatchTaskItem> dispatchTasks;

    @Data
    @Schema(description = "派发任务项")
    public static class DispatchTaskItem {
        @Schema(description = "任务ID（数据库生成）")
        private String id;

        @Schema(description = "业务域：设备控制/维修工单/告警处理等")
        private String businessDomain;

        @Schema(description = "等级：高/中/低")
        private String level;

        @Schema(description = "问题描述")
        private String problemDesc;

        @Schema(description = "思考过程概览，50字以内")
        private String thinkingOverview;

        @Schema(description = "问题总结，50字以内")
        private String problemSummary;

        @Schema(description = "决策内容")
        private String decisionContent;

        @Schema(description = "决策数据。维修工单为纯设备ID字符串如\"123\"")
        private String decisionData;

        @Schema(description = "预期成效")
        private String expectedEffect;

        @Schema(description = "目标AgentId")
        private String targetAgentId;

        @Schema(description = "目标Agent名称")
        private String targetAgentName;

        @Schema(description = "任务类型: hint-提示, collaboration-协同方案")
        private String taskType;
    }
}
