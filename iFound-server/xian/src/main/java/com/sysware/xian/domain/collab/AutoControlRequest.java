package com.sysware.xian.domain.collab;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * 自动控制开关设置请求体
 */
@Data
@Schema(description = "自动控制开关设置请求体")
public class AutoControlRequest {

    @Schema(description = "是否启用自动控制", example = "true", requiredMode = REQUIRED)
    private Boolean enabled;

    @Schema(description = "操作人姓名（开启自动采纳时作为任务的采纳人）", example = "李四", requiredMode = NOT_REQUIRED)
    private String username;
}
