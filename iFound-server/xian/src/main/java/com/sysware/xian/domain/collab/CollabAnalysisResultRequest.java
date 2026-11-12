package com.sysware.xian.domain.collab;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * 场景1-③子Agent回传AI分析结果请求体
 */
@Data
@Schema(description = "子Agent回传AI分析结果请求体")
public class CollabAnalysisResultRequest {

    @Schema(description = "任务ID", example = "1946063744774477696", requiredMode = REQUIRED)
    private String id;

    @Schema(description = "分析结果分类：hint-提示(仅存档)，collaboration-协同方案(置待采纳)，normal-正常/误报(自动完结)", example = "collaboration", requiredMode = REQUIRED)
    private String taskType;

    @Schema(description = "决策建议内容", example = "建议开启TX22区域空调2台", requiredMode = REQUIRED)
    private String decisionContent;

    @Schema(description = "决策数据JSON", example = "[{\"deviceId\":\"1226586210521059328\"}]", requiredMode = NOT_REQUIRED)
    private String decisionData;

    @Schema(description = "分析思考过程摘要", requiredMode = NOT_REQUIRED)
    private String thinkingOverview;

    @Schema(description = "问题等级：高/中/低", example = "高", requiredMode = NOT_REQUIRED)
    private String level;

    @Schema(description = "预期成效", example = "20分钟后降低温度", requiredMode = NOT_REQUIRED)
    private String expectedEffect;
}
