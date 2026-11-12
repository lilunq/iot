package com.sysware.xian.domain.collab;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * 场景2-①子Agent上报问题+决策请求体
 */
@Data
@Schema(description = "子Agent上报问题+决策请求体")
public class CollabReportRequest {

    @Schema(description = "固定为commander", example = "commander", requiredMode = REQUIRED)
    private String creatorAgentId;

    @Schema(description = "固定系統Agent", example = "系統Agent", requiredMode = NOT_REQUIRED)
    private String creatorAgentName;

    @Schema(description = "业务域", example = "能耗", requiredMode = REQUIRED)
    private String businessDomain;

    @Schema(description = "分类：hint-提示(存档展示)，collaboration-协同方案(置待采纳)，normal-正常/误报(入库即完结)", example = "collaboration", requiredMode = REQUIRED)
    private String taskType;

    @Schema(description = "问题描述", example = "TX22区域能耗超阈值30%", requiredMode = REQUIRED)
    private String problemDesc;

    @Schema(description = "决策内容", example = "建议关闭TX22区域空调2台", requiredMode = NOT_REQUIRED)
    private String decisionContent;

    @Schema(description = "决策数据JSON", example = "[{\"deviceId\":\"T001\"}]", requiredMode = NOT_REQUIRED)
    private String decisionData;

    @Schema(description = "分析思考过程摘要", requiredMode = NOT_REQUIRED)
    private String thinkingOverview;

    @Schema(description = "问题等级：高/中/低", example = "高", requiredMode = NOT_REQUIRED)
    private String level;

    @Schema(description = "预期成效", example = "区域能耗回落至阈值内", requiredMode = NOT_REQUIRED)
    private String expectedEffect;

    @Schema(description = "目标Agent ID（建议由哪个Agent处理）", example = "energy-agent", requiredMode = NOT_REQUIRED)
    private String targetAgentId;

    @Schema(description = "目标Agent名称", example = "能源管理Agent", requiredMode = NOT_REQUIRED)
    private String targetAgentName;
}
