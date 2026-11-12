package com.sysware.xian.module.entity.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 通过 Spring Framework 6 RestClient 调用 Elasticsearch REST API，
 * 不额外引入与现有 Spring Boot 依赖管理可能冲突的 ES Java Client。
 */
@Configuration
@EnableConfigurationProperties(EntityCenterProperties.class)
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "enabled", havingValue = "true")
public class EntityElasticsearchConfig {

    @Bean(name = "entityElasticsearchRestClient")
    public RestClient entityElasticsearchRestClient(EntityCenterProperties properties) {
        EntityCenterProperties.Elasticsearch es = properties.getElasticsearch();
        RestClient.Builder builder = RestClient.builder().baseUrl(es.getUri());
        if (es.getUsername() != null && !es.getUsername().isBlank()) {
            builder.defaultHeaders(headers -> headers.setBasicAuth(
                    es.getUsername(), es.getPassword() == null ? "" : es.getPassword()));
        }
        return builder.build();
    }
}
