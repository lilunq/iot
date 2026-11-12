package com.sysware.xian.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备告警处理请求
 */
@Data
@Schema(description = "设备告警处理请求")
public class DeviceAlarmRequest {
    @Schema(description = "事件ID")
    private String id;

    @JsonProperty("org_id")
    @Schema(description = "组织ID")
    private String orgId;

    @JsonProperty("subarea_cid")
    @Schema(description = "分区CID")
    private String subareaCid;

    @JsonProperty("system_type")
    @Schema(description = "系统类型")
    private String systemType;

    @JsonProperty("device_id")
    @Schema(description = "设备ID")
    private String deviceId;

    @JsonProperty("device_name")
    @Schema(description = "设备名称")
    private String deviceName;

    @JsonProperty("event_level")
    @Schema(description = "事件等级")
    private Integer eventLevel;

    @JsonProperty("event_state")
    @Schema(description = "事件状态 0恢复1异常")
    private Integer eventState;

    @Schema(description = "位置")
    private String location;

    @JsonProperty("event_info")
    @Schema(description = "事件信息")
    private String eventInfo;

    @JsonProperty("happen_time")
    @Schema(description = "发生时间")
    private String happenTime;

    @JsonProperty("receive_time")
    @Schema(description = "接收时间")
    private String receiveTime;

    @JsonProperty("process_state")
    @Schema(description = "处理状态 0未处理1已处理")
    private Integer processState;

    @JsonProperty("event_type")
    @Schema(description = "事件类型")
    private String eventType;

    @JsonProperty("data_type")
    @Schema(description = "数据类型")
    private Integer dataType;

    @JsonProperty("data_key")
    @Schema(description = "数据key")
    private String dataKey;

    @JsonProperty("confirm_state")
    @Schema(description = "确认状态")
    private String confirmState;

    @JsonProperty("warning_level")
    @Schema(description = "告警等级")
    private String warningLevel;
}
