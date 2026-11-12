package com.sysware.xian.domain.collab;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * 用户采纳/驳回请求体（场景1-④/场景2-②共用）
 */
@Data
@Schema(description = "用户采纳/驳回请求体")
public class CollabAdoptRequest {

    @Schema(description = "任务ID", example = "1946063744774477696", requiredMode = REQUIRED)
    private String id;

    @Schema(description = "采纳状态：1-采纳，2-驳回", example = "1", requiredMode = REQUIRED)
    private String adoptStatus;

    @Schema(description = "操作人姓名（采纳人/驳回人）", example = "李四", requiredMode = REQUIRED)
    private String adoptUser;
}
