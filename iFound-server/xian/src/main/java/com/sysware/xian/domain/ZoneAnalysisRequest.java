package com.sysware.xian.domain;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 分区态势分析请求（单条异常逐个分析）
 * 每次只传入一条异常数据，由 anomalyType 标识类型
 */
@Data
public class ZoneAnalysisRequest {
    /** 异常类型：env-环境告警, device-设备故障/告警, energy-能耗异常, keliu-客流异常 */
    private String anomalyType;
    private String subareaCid;
    private String zoneName;

    /** 单条环境告警：{item, value, status/message} */
    private Map<String, Object> envAlarm;

    /** 单条异常设备 */
    private DeviceItem device;

    /** 能耗数据：{message, value, status} */
    private Map<String, Object> energy;

    /** 客流数据：{message, value, status} */
    private Map<String, Object> keliu;

    /** 补充描述 */
    private List<String> desc;

    @Data
    public static class DeviceItem {
        private String id;
        private String name;
        private String type;
        private String status;
        private List<Map<String, String>> currentData;
        private String location;
    }
}
