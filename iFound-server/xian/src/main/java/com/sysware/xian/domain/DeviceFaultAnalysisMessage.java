package com.sysware.xian.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备故障AI分析请求消息体（queue.ai.device.fault.analysis，默认交换机直发）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceFaultAnalysisMessage {

    @Schema(title = "deviceId", description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceId;

    @Schema(title = "deviceName", description = "设备名称")
    private String deviceName;
}
