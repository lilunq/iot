package com.sysware.xian.swcloud.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * @Description: 设备列表查询参数实体类
 * @Author: LiYanru
 * @date: 2026-06-18
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(name = "ScadaPageQueryDto", description = "设备列表查询参数实体类")
public class ScadaPageQueryDto {

    /**
     * 页码
     */
    @Schema(name = "page", description = "页码", example = "1", requiredMode = REQUIRED)
    private String page;

    /**
     * 每页条数
     */
    @Schema(name = "limit", description = "每页条数", example = "10")
    private String limit;

    /**
     * 设备ID
     */
    @JsonProperty("device_id")
    @Schema(name = "device_id", description = "设备ID", example = "1203022680400334848")
    private String deviceId;

    /**
     * 组织ID，固定为3
     */
    @JsonProperty("org_id")
    @Schema(name = "org_id", description = "组织ID，固定为3", example = "3", requiredMode = REQUIRED)
    private String orgId;

    /**
     * 设备名称，模糊查询
     */
    @JsonProperty("device_name")
    @Schema(name = "device_name", description = "设备名称，模糊查询", example = "照明")
    private String deviceName;

    /**
     * 区域CID，右模糊查询
     */
    @JsonProperty("subarea_cid")
    @Schema(name = "subarea_cid", description = "区域CID，右模糊查询", example = "0.001.001.001-0")
    private String subareaCid;

    /**
     * 设备类型
     */
    @JsonProperty("device_type")
    @Schema(name = "device_type", description = "设备类型，右模糊查询", example = "1.001")
    private String deviceType;

    /**
     * 系统类型
     */
    @JsonProperty("system_type")
    @Schema(name = "system_type", description = "系统类型", example = "1.001")
    private String systemType;

    /**
     * 有该参数代表只查询故障的设备
     */
    @JsonProperty("fault_state")
    @Schema(name = "fault_state", description = "有该参数代表只查询故障的设备")
    private String faultState;

    /**
     * 有该参数代表只查询告警的设备
     */
    @JsonProperty("alarm_state")
    @Schema(name = "alarm_state", description = "有该参数代表只查询告警的设备")
    private String alarmState;

    /**
     * 是否添加告警和故障设备的变更时间
     */
    @JsonProperty("add_change_state_time")
    @Schema(name = "add_change_state_time", description = "是否添加告警和故障设备的变更时间")
    @Builder.Default
    private int addChangeStateTime = 0;
}
