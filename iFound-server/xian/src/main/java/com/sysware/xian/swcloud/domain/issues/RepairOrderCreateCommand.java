package com.sysware.xian.swcloud.domain.issues;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description: 直接创建维修工单（无AI诊断） 请求命令
 * @Author: LiYanRu
 * @date: 2026-08-11
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RepairOrderCreateCommand {

    /**
     * 处理类型
     */
    @Schema(title = "processType", description = "处理类型")
    private String processType;

    /**
     * 故障原因
     */
    @Schema(title = "faultReason", description = "故障原因", example = "故障原因")
    private String faultReason;

    /**
     * 处理建议
     */
    @Schema(title = "handlingAdvice", description = "处理建议", example = "处理建议")
    private String handlingAdvice;

    /**
     * 排查步骤/计划措施
     */
    @Schema(title = "plannedMeasures", description = "排查步骤/计划措施", example = "排查步骤")
    private String plannedMeasures;

    /**
     * 问题等级,可用值:LOW,MEDIUM,HIGH,CRITICAL
     */
    @Schema(title = "issueLevel", description = "问题等级,可用值:LOW,MEDIUM,HIGH,CRITICAL", example = "CRITICAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String issueLevel;

    /**
     * 问题标题
     */
    @Schema(title = "issueTitle", description = "问题标题", example = "故障", requiredMode = Schema.RequiredMode.REQUIRED)
    private String issueTitle;

    /**
     * 问题内容
     */
    @Schema(title = "issueContent", description = "问题内容", example = "设备故障", requiredMode = Schema.RequiredMode.REQUIRED)
    private String issueContent;

    /**
     * 关联资产ID
     */
    @Schema(title = "assetId", description = "关联资产ID", example = "1203022680048013312", requiredMode = Schema.RequiredMode.REQUIRED)
    private String assetId;

    /**
     * 发现时间 (格式: yyyy-MM-dd HH:mm:ss)
     */
    @Schema(title = "foundTime", description = "发现时间 (格式: yyyy-MM-dd HH:mm:ss)", example = "2026-06-14 00:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private String foundTime;

    /**
     * 上报人ID
     */
    @Schema(title = "userId", description = "上报人ID", example = "9", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    /**
     * 上报人姓名
     */
    @Schema(title = "userName", description = "上报人姓名", example = "开发", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userName;

    /**
     * 上报人组织CID
     */
    @Schema(title = "orgCid", description = "上报人组织CID", example = "0.001.001.001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orgCid;
}
