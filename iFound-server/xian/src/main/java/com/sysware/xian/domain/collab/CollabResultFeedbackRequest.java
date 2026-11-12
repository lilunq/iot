package com.sysware.xian.domain.collab;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * 子Agent执行结果反馈请求体（场景1-⑤/场景2-③共用）
 */
@Data
@Schema(description = "子Agent执行结果反馈请求体")
public class CollabResultFeedbackRequest {

    @Schema(description = "任务ID", example = "1946063744774477696", requiredMode = REQUIRED)
    private String id;

    @Schema(description = "执行结果状态：1-成功，2-失败", example = "1", requiredMode = REQUIRED)
    private Integer resultExecuteStatus;

    @Schema(description = "结果描述", example = "工单已派发给张三，维修完成", requiredMode = NOT_REQUIRED)
    private String resultDesc;

    @Schema(description = "执行人姓名或执行Agent标识", example = "ops-agent", requiredMode = NOT_REQUIRED)
    private String resultExecuteUser;

    @Schema(description = "执行结果原始数据JSON", requiredMode = NOT_REQUIRED)
    private String resultData;
}
