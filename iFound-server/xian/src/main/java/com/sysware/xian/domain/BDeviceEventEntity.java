package com.sysware.xian.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 设备事件实体（对应 MQ 广播的设备告警事项）
 * <p>
 * JSON 字段为 snake_case，通过 {@link JsonProperty} 映射到 camelCase 属性。
 */
@Data
public class BDeviceEventEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    private String id;

    /**
     * org_id
     */
    @JsonProperty("org_id")
    private String orgId;

    /**
     * 单位分区
     */
    @JsonProperty("subarea_cid")
    private String subareaCid;

    /**
     * 所属系统类型
     */
    @JsonProperty("system_type")
    private String systemType;

    /**
     * 设备id
     */
    @JsonProperty("device_id")
    private String deviceId;

    /**
     * device_name
     */
    @JsonProperty("device_name")
    private String deviceName;

    /**
     * 事件级别：火警，故障，告警，通知
     */
    @JsonProperty("event_level")
    private Integer eventLevel;

    /**
     * 事件子类型：是时间触发对应的状态，非0表示异常，0代表恢复
     */
    @JsonProperty("event_state")
    private Integer eventState;

    /**
     * location
     */
    private String location;

    /**
     * event_info
     */
    @JsonProperty("event_info")
    private String eventInfo;

    /**
     * 发生时间：YYYYMMDDHHMMSS
     */
    @JsonProperty("happen_time")
    private String happenTime;

    /**
     * receive_time
     */
    @JsonProperty("receive_time")
    private String receiveTime;

    /**
     * process_state
     */
    @JsonProperty("process_state")
    private Integer processState;

    /**
     * 事件类型：对应的是设备事件类型定义表id
     */
    @JsonProperty("event_type")
    private String eventType;

    /**
     * 数据类型，遥测、遥信、rtu、火警、、、
     */
    @JsonProperty("data_type")
    private Integer dataType;

    /**
     * 数据类型对应的数据key
     */
    @JsonProperty("data_key")
    private String dataKey;

    /**
     * 事项确认状态 0：无需确认  1：未确认   2：已确认
     */
    @JsonProperty("confirm_state")
    private Integer confirmState;

    /**
     * 报警级别
     */
    @JsonProperty("warning_level")
    private Integer warningLevel;
}
