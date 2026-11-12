package com.sysware.xian.domain;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 全分区综合分析请求
 */
@Data
public class AllZoneAnalysisRequest {
    private List<ZoneSummary> zones;

    @Data
    public static class ZoneSummary {
        private String name;
        private String subareaCid;
        private String status; // "正常" 或 "注意"
        private String envScore;
        private String envStatus;
        private String deviceStatus;
        private String passengerDensity;
        private String passengerStatus;
        private String energyToday;
        private String energyStatus;
        private String suggestion;
        private List<String> desc;
        private List<EnvAlarm> envAlarms;
        private List<DeviceItem> anomalyDevices;

        @Data
        public static class EnvAlarm {
            private String item;
            private String value;
            private String status;
        }

        @Data
        public static class DeviceItem {
            private String name;
            private String type;
            private String status;
            private String location;
        }
    }
}
