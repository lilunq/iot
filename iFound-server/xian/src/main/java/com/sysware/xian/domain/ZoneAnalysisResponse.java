package com.sysware.xian.domain;

import lombok.Data;
import java.util.List;

/**
 * 分区态势分析响应（含派发任务）
 */
@Data
public class ZoneAnalysisResponse {
    private String zoneAnalysis;
    private List<AnomalyAction> anomalyActions;
    private String suggestion;
    private String envScore;
    private String envStatus;
    private List<DispatchTaskItem> dispatchTasks;

    @Data
    public static class AnomalyAction {
        private String title;
        private String detail;
        private String type; // "env" or "device"
        private List<String> buttons;
    }

    /**
     * AI生成的派发任务项
     */
    @Data
    public static class DispatchTaskItem {
        private String id;
        private String businessDomain;       // 业务域：设备/能耗/环境
        private String level;                // 等级：高/中/低
        private String problemDesc;          // 问题描述
        private String thinkingOverview;     // 思考过程概览
        private String problemSummary;       // 问题总结
        private String decisionContent;      // 决策内容
        private String decisionData;         // 决策数据：维修工单为纯设备ID字符串；设备控制为JSON命令字符串
        private String expectedEffect;       // 预期成效
        private String targetAgentId;        // 要派发的子AgentId
        private String targetAgentName;      // 目标Agent名称
        private String taskType;             // 任务类型: hint-提示, collaboration-协同方案
    }
}
