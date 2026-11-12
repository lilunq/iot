package com.sysware.swcloud.domian.scada;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @Schema(description = "页码", name = "page", example = "1", requiredMode = REQUIRED)
    private String page;

    /**
     * 每页条数
     */
    @Schema(description = "每页条数", name = "limit", example = "10")
    private String limit;

    /**
     * 设备ID
     */
    @Schema(description = "设备ID", name = "deviceId")
    @JsonAlias("device_id")
    private String deviceId;

    /**
     * 组织ID，固定为3
     */
    @Schema(description = "组织ID，固定为3", name = "orgId", example = "3", requiredMode = REQUIRED)
    @JsonAlias("org_id")
    private String orgId;

    /**
     * 设备名称，模糊查询
     */
    @Schema(description = "设备名称，模糊查询", name = "deviceName")
    @JsonAlias("device_name")
    private String deviceName;

    /**
     * 区域CID，右模糊查询
     */
    @Schema(description = "区域CID，右模糊查询", name = "subareaCid", example = "0.001.001.001-0")
    @JsonAlias("subarea_cid")
    private String subareaCid;

    /**
     * 设备类型
     */
    @Schema(description = "设备类型，右模糊查询", name = "deviceType", example = "1.001")
    @JsonAlias("device_type")
    private String deviceType;

    /**
     * 系统类型
     */
    @Schema(description = "系统类型", name = "systemType")
    @JsonAlias("system_type")
    private String systemType;

    /**
     * 有该参数代表只查询故障的设备
     */
    @Schema(description = "有该参数代表只查询故障的设备", name = "faultState")
    @JsonAlias("fault_state")
    private String faultState;

    /**
     * 有该参数代表只查询告警的设备
     */
    @Schema(description = "有该参数代表只查询告警的设备", name = "alarmState")
    @JsonAlias("alarm_state")
    private String alarmState;

    /**
     * 是否添加告警和故障设备的变更时间
     */
    @Schema(description = "是否添加告警和故障设备的变更时间", name = "addChangeStateTime")
    @JsonAlias("add_change_state_time")
    private int addChangeStateTime = 0;
}