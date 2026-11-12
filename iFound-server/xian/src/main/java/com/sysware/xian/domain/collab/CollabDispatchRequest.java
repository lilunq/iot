package com.sysware.xian.domain.collab;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * 场景1-②派发任务请求体
 */
@Data
@Schema(description = "派发任务请求体")
public class CollabDispatchRequest {

    @Schema(description = "任务ID（创建任务时返回）", example = "1946063744774477696", requiredMode = REQUIRED)
    private String id;

    @Schema(description = "派发人姓名", example = "张三", requiredMode = NOT_REQUIRED)
    private String dispatchedUser;
}
