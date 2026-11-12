package com.sysware.swcloud.domian.scada;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description: 设备事件查询参数
 * @Author: LiYanRu
 * @date: 2026-06-17
 **/
@Schema(description = "设备事件查询参数")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ScadaEventQueryDto {
    /**
     * 页码
     */
    @Schema(title = "page", description = "页码", example = "1", required = true)
    private String page;
    /**
     * 页面大小
     */
    @Schema(title = "limit", description = "页面大小", example = "5", required = true)
    private String limit;
    /**
     * 单位id，固定为3，西安东站
     */
    @Schema(title = "orgId", description = "单位id，固定为3，西安东站", required = true, example = "3")
    @JsonAlias("org_id")
    private String orgId;
    /**
     * 事件范围查询的开始时间 格式为 yyyyMMddHHmmss例如：20260511101308
     */
    @Schema(title = "startTime", description = "事件范围查询的开始时间 格式为 yyyyMMddHHmmss例如：20260511101308", required = true, example = "20260511101308")
    @JsonAlias("start_time")
    private String startTime;
    /**
     * 系统类型
     */
    @Schema(title = "systemType", description = "系统类型")
    @JsonAlias("system_type")
    private String systemType;
    /**
     * 区域id
     */
    @Schema(title = "subareaCid", description = "区域id，查询整个站台id为0.001.001.001-0", required = true, example = "0.001.001.001-0")
    @JsonAlias("subarea_cid")
    private String subareaCid;
    /**
     * event_level 为事件等级：0 操作事项，1 通知事项，2 告警事项，3 故障事项，10 火警事项
     */
    @Schema(title = "eventLevel", description = "event_level 为事件等级：0 操作事项，1 通知事项，2 告警事项，3 故障事项，10 火警事项", example = "3")
    @JsonAlias("event_level")
    private String eventLevel;
    /**
     * 事项状态0恢复1异常
     */
    @Schema(title = "eventState", description = "事项状态0恢复1异常", example = "0")
    @JsonAlias("event_state")
    private String eventState;
    /**
     * 设备id
     */
    @Schema(title = "deviceId", description = "设备id")
    @JsonAlias("device_id")
    private String deviceId;
    /**
     * 处理状态 1处理 0未处理
     */
    @Schema(title = "processState", description = "处理状态，1=已处理/恢复，0=未处理/异常，非必填")
    @JsonAlias("process_state")
    private String processState;
    /**
     * 事件范围查询的结束时间 格式为 yyyyMMddHHmmss例如：20260511101308
     */
    @Schema(title = "endTime", description = "事件范围查询的结束时间 格式为 yyyyMMddHHmmss例如：20260511101308")
    @JsonAlias("end_time")
    private String endTime;
}