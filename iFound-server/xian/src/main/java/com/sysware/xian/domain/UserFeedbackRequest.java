package com.sysware.xian.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * 用户大屏回填请求体
 */
@Data
@Schema(description = "用户大屏回填请求体")
public class UserFeedbackRequest {

    @Schema(description = "任务ID", example = "1946063744774477696", requiredMode = REQUIRED)
    private String id;

    @Schema(description = "用户名，将写入result_execute_user（执行人）", example = "张三", requiredMode = REQUIRED)
    private String username;

    @Schema(description = "用户反馈，写入user_feedback", example = "现场已确认处理完毕", requiredMode = NOT_REQUIRED)
    private String userFeedback;

    @Schema(description = "执行结果状态：1-处理完成，2-处理失败，写入result_execute_status", example = "1", requiredMode = NOT_REQUIRED)
    private Integer resultExecuteStatus;
}
