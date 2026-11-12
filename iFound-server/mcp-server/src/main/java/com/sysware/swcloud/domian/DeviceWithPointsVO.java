package com.sysware.swcloud.domian;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description: 设备信息，包含对应的点位信息
 * @Author: LiYanRu
 * @date: 2026-07-02
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceWithPointsVO {
    /**
     * 设备 ID。
     */
    private String deviceId;
    /**
     * 设备名称。
     */
    private String deviceName;
    /**
     * 所属区域 ID。
     */
    private String subareaId;
    /**
     * 系统类型。
     */
    private String systemType;
    /**
     * 所属组织 ID。
     */
    private String orgId;
    /**
     * 区域级联 ID。
     */
    private String subareaCid;
    /**
     * 告警数量或告警标记。
     */
    private Integer alarm;
    /**
     * 故障数量或故障标记。
     */
    private Integer fault;
    /**
     * 火警数量或火警标记。
     */
    private Integer fire;
    /**
     * 点位数据列表。
     */
    private List<PointInfoVO> data;

    /**
     * 设备绑定点位的精简信息。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PointInfoVO {
        /**
         * 点位名称。
         */
        private String dataName;

        /**
         * 点位当前值。
         */
        private String dataValue;

        /**
         * 点位唯一标识。
         */
        private String dataKey;

        /**
         * 点位类型。
         */
        private String dataType;
    }
}
