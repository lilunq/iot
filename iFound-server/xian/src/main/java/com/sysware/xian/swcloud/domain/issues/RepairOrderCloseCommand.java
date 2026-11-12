package com.sysware.xian.swcloud.domain.issues;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description: 强制关闭设备所有工单 请求命令
 * @Author: LiYanRu
 * @date: 2026-08-11
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RepairOrderCloseCommand {

    /**
     * 关闭原因
     */
    @Schema(title = "closeReason", description = "关闭原因", example = "已恢复")
    private String closeReason;

    /**
     * 资产使用状态,可用值:IN_USE(在用)、IDLE(闲置)、SCRAP(报废)等
     */
    @Schema(title = "assetUsageStatus", description = "资产使用状态,可用值:IN_USE、IDLE、SCRAP等", example = "IN_USE")
    private String assetUsageStatus;
}
