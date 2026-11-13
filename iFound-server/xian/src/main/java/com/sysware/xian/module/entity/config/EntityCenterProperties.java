package com.sysware.xian.module.entity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "entity-center")
public class EntityCenterProperties {
    private Elasticsearch elasticsearch = new Elasticsearch();
    private Resolver resolver = new Resolver();

    @Data
    public static class Elasticsearch {
        /** 默认关闭，避免新模块影响现有服务启动。 */
        private boolean enabled = false;
        private String uri = "http://127.0.0.1:9200";
        private String username = "";
        private String password = "";
        private boolean adminApiEnabled = false;
        private boolean syncApiEnabled = false;
    }

    @Data
    public static class Resolver {
        private int defaultLimit = 5;
        private int maxLimit = 20;
        private double minimumScore = 3.0;
        private double minimumScoreGap = 1.0;
    }
}
