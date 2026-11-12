package com.sysware.xian.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 采纳结果回调请求体（系统Agent采纳/驳回后回调子Agent的adoptFeedbackUrl时发送）
 */
@Data
@Schema(description = "采纳结果回调请求体（系统Agent → 子Agent）")
public class AdoptNotifyRequest {

    @Schema(description = "任务ID，子Agent据此关联本地任务", example = "1946063744774477696")
    private String id;

    @Schema(description = "采纳状态：1-采纳（子Agent需执行决策），2-驳回（不执行）", example = "1")
    private String adoptStatus;

    @Schema(description = "操作人姓名（采纳人/驳回人）", example = "李四")
    private String adoptUser;

    @Schema(description = "最终决策内容（含用户采纳时的修订）", example = "给设备派单维修，预计2小时内完成")
    private String decisionContent;

    @Schema(description = "最终决策数据（含用户采纳时的修订），JSON字符串", example = "[{\"deviceId\":\"1226586210521059328\"}]")
    private String decisionData;
}
