package com.sysware.core.report.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 报表设备事件精简对象
 * <p>
 * 源数据字段为 snake_case（happen_time 等），通过 {@link JsonNaming} 映射到 camelCase 属性。
 *
 * @Author: LiYanRu
 * @date: 2026-07-21
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ReportDeviceEvent {
    /**
     * 发生时间
     */
    private String happenTime;
    /**
     * 接收时间
     */
    private String receiveTime;
    /**
     * 设备名称
     */
    private String deviceName;
    /**
     * 事件信息
     */
    private String eventInfo;
    /**
     * 区域 cid
     */
    private String subareaCid;
    /**
     * 事件等级
     */
    private String eventLevel;
}
