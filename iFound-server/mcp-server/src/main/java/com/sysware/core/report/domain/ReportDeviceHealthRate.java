package com.sysware.core.report.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 报表设备健康率统计对象
 *
 * @Author: LiYanRu
 * @date: 2026-07-31
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "设备健康率统计（设备总数/故障数/告警数/健康率）")
public class ReportDeviceHealthRate {
    /**
     * 设备总数
     */
    @Schema(description = "设备总数", example = "120")
    private Integer totalCount;

    /**
     * 故障设备数
     */
    @Schema(description = "故障设备数", example = "2")
    private Integer faultCount;

    /**
     * 告警设备数
     */
    @Schema(description = "告警设备数", example = "3")
    private Integer alarmCount;

    /**
     * 设备健康率：(总数 - 故障数 - 告警数) / 总数 * 100，保留 1 位小数
     */
    @Schema(description = "设备健康率（百分比，保留 1 位小数）", example = "95.8")
    private String healthRate;
}
