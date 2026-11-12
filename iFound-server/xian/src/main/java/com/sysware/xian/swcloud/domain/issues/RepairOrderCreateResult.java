package com.sysware.xian.swcloud.domain.issues;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description: 直接创建维修工单 返回结果
 * @Author: LiYanRu
 * @date: 2026-08-11
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RepairOrderCreateResult {

    /**
     * 问题ID
     */
    @Schema(title = "issueId", description = "问题ID", example = "7492830700874240000")
    private String issueId;

    /**
     * 工单ID
     */
    @Schema(title = "orderId", description = "工单ID", example = "7492830700903600128")
    private String orderId;

    /**
     * 工单编号
     */
    @Schema(title = "orderNo", description = "工单编号", example = "REP-ORD-20260811-671897")
    private String orderNo;
}
