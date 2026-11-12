package com.sysware.xian.domain.collab;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * 场景1-①系统Agent创建决策任务请求体
 */
@Data
@Schema(description = "系统Agent创建决策任务请求体")
public class CollabCreateTaskRequest {

    @Schema(description = "目标子Agent ID", example = "energy-agent", requiredMode = REQUIRED)
    private String targetAgentId;

    @Schema(description = "目标子Agent名称", example = "能源管理Agent", requiredMode = NOT_REQUIRED)
    private String targetAgentName;

    @Schema(description = "业务域", example = "环境能耗", requiredMode = REQUIRED)
    private String businessDomain;

    @Schema(description = "问题描述", example = "候车大厅西北区温度过高", requiredMode = REQUIRED)
    private String problemDesc;

    @Schema(description = "任务类型：hint-提示，collaboration-协同方案，normal-正常/误报", example = "collaboration", requiredMode = REQUIRED)
    private String taskType;

    @Schema(description = "决策内容", example = "", requiredMode = NOT_REQUIRED)
    private String decisionContent;

    @Schema(description = "决策数据JSON", example = "[{\"deviceId\":\"1226586210521059328\"}]", requiredMode = NOT_REQUIRED)
    private String decisionData;
}
