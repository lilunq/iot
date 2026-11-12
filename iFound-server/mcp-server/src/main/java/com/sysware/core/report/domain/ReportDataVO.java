package com.sysware.core.report.domain;

import com.sysware.swcloud.domian.ifim.CrowdedAreaVO;
import com.sysware.swcloud.domian.ifim.SecurityCheckCurrentVO;
import com.sysware.swcloud.domian.ifim.StationInOutTotalVO;
import com.sysware.swcloud.domian.ifim.StationRealtimeVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 报表聚合数据返回对象
 * <p>
 * 由 8 路远程接口并发查询后整合：设备健康率、区域态势、故障设备、严重事件、实时客流、当前安检人数、累计进出站客流、拥堵区域，
 * 每个数据模块均附带对应的统计总数，供 AI/前端直接使用。
 *
 * @Author: LiYanRu
 * @date: 2026-07-31
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "报表聚合数据（设备健康率/区域态势/故障设备/严重事件/实时客流/累计进出站客流/拥堵区域）")
public class ReportDataVO {

    // ==================== 模块一：区域态势 ====================
    /**
     * 区域态势列表（实时的设备/环境/能耗数据）
     */
    @Schema(description = "【区域态势】列表模块：每个区域的实时设备状态统计（deviceStateCount：total/fault_total/alarm_total）、"
            + "能耗（energy：status/message/value）、环境告警设备（environment）、区域标识（subarea_cid/subarea_name）"
            + "及异常标记（subarea_alter，1=异常 0=正常），异常区域排在前面")
    private List<Map<String, Object>> subareaState;

    // ==================== 模块二：设备健康率 ====================
    /**
     * 设备健康率统计，内含设备总数、故障数、告警数、健康率
     */
    @Schema(description = "【设备健康率】统计模块：设备总数、故障数、告警数、健康率（百分比）")
    private ReportDeviceHealthRate deviceHealthRate;

    // ==================== 模块三：故障设备 ====================
    /**
     * 故障设备列表（精简字段，最多 5 条）
     */
    @Schema(description = "【故障设备】列表模块：当前故障状态的设备精简列表（最多 5 条），含设备名称、别名、故障/告警标志、区域 cid")
    private List<ReportDeviceState> faultDevices;

    /**
     * 故障设备统计：符合条件的设备总数（分页 count，非列表条数）
     */
    @Schema(description = "【故障设备】统计：故障设备总数（分页总数，可能大于列表条数）", example = "12")
    private Integer faultDeviceCount;

    // ==================== 模块四：严重事件 ====================
    /**
     * 近 24 小时严重（3 级）未处理事件列表（精简字段，最多 2 条）
     */
    @Schema(description = "【设备事件】列表模块：近 24 小时严重等级（3 级）未处理事件精简列表（最多 2 条），"
            + "含发生时间、接收时间、设备名称、事件信息、区域 cid、事件等级")
    private List<ReportDeviceEvent> deviceEvents;

    /**
     * 严重事件统计：符合条件的事件总数（分页 count，非列表条数）
     */
    @Schema(description = "【设备事件】统计：严重事件总数（分页总数，可能大于列表条数）", example = "8")
    private Integer deviceEventCount;

    /**
     * 事件查询起始时间（当前时间 - 24 小时，格式 yyyyMMddHHmmss）
     */
    @Schema(description = "【设备事件】事件查询起始时间（当前时间前 24 小时），格式 yyyyMMddHHmmss", example = "20260730093000")
    private String deviceEventQueryStartTime;

    // ==================== 模块五：实时客流 ====================
    /**
     * 车站实时在站人数概览
     */
    @Schema(description = "【实时客流】统计模块：车站实时在站人数概览（在站/安检/候车/站台/检票/公共区/出站人数）")
    private StationRealtimeVO stationRealtime;

    // ==================== 模块五（补充）：当前安检人数 ====================
    /**
     * 当前安检人数统计（站内人数/已安检人数/未安检人数）
     */
    @Schema(description = "【当前安检人数】统计模块：站内人数（allCount）、已安检人数（checkedCount）、未安检人数（uncheckedCount）")
    private SecurityCheckCurrentVO securityCheckCurrent;

    // ==================== 模块六：累计进出站客流 ====================
    /**
     * 累计进出站人数（统计区间：2026-07-01 00:00:00 至当前）
     */
    @Schema(description = "【当日累计进出站客流】统计模块：指定时间段内的累计进站人数（entryCount）和累计出站人数（exitCount）")
    private StationInOutTotalVO stationInOutTotal;

    // ==================== 模块七：拥堵区域 ====================
    /**
     * 拥堵区域列表
     */
    @Schema(description = "【拥堵区域】列表模块：当前处于拥堵状态的区域实时客流列表")
    private List<CrowdedAreaVO> crowdedAreas;

    /**
     * 拥堵区域统计：拥堵区域数量
     */
    @Schema(description = "【拥堵区域】统计：拥堵区域数量", example = "2")
    private Integer crowdedAreaCount;
}
