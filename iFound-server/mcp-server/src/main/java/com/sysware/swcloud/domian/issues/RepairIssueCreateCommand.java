package com.sysware.swcloud.domian.issues;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * @Description: 设备维修问题 创建命令
 * @Author: LiYanRu
 * @date: 2026-06-09
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class RepairIssueCreateCommand {
    /**
     * 问题等级,可用值:LOW,MEDIUM,HIGH,CRITICAL
     */
    @Schema(title = "issueLevel", description = "问题等级,可用值:LOW,MEDIUM,HIGH,CRITICAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String issueLevel;

    /**
     * 问题名称
     */
    @Schema(title = "issueTitle", description = "问题名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String issueTitle;

    /**
     * 问题详细描述
     */
    @Schema(title = "issueContent", description = "问题详细描述", requiredMode = Schema.RequiredMode.REQUIRED)
    private String issueContent;

    /**
     * 关联资产ID
     */
    @Schema(title = "assetId", description = "关联资产ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String assetId;

    /**
     * 发现问题时间 (格式由下游服务定义，如 yyyy-MM-dd HH:mm:ss)
     */
    @Schema(title = "foundTime", description = "发现问题时间 (格式由下游服务定义，如 yyyy-MM-dd HH:mm:ss)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String foundTime;

    /**
     * 上报人ID
     */
    @Schema(title = "userId", description = "上报人ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    /**
     * 上报人姓名
     */
    @Schema(title = "userName", description = "上报人姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userName;

    /**
     * 上报人组织CID
     */
    @Schema(title = "orgCid", description = "上报人组织CID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orgCid;
}
