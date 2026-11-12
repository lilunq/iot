package com.sysware.core.report.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 报表设备状态精简对象
 * <p>
 * 源数据字段为 snake_case（device_name 等），通过 {@link JsonNaming} 映射到 camelCase 属性。
 *
 * @Author: LiYanRu
 * @date: 2026-07-21
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ReportDeviceState {
    /**
     * 设备名称
     */
    private String deviceName;
    /**
     * 设备别名
     */
    private String deviceAlias;

    /**
     * 错误标志，表示当前设备出错
     */
    private String fault;

    /**
     * 报警标志，表示当前设备报警
     */
    private String alarm;

    /**
     * 区域cid
     */
    private String subareaCid;
}
