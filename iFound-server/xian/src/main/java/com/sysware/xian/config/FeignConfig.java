package com.sysware.xian.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Feign 请求拦截器配置
 * 为调用 mcp-server 的请求自动添加认证头
 */
@Configuration
public class FeignConfig {

    @Value("${ai.mcp-token:mcp-server-secret-token-2026}")
    private String mcpToken;

    @Bean
    public RequestInterceptor mcpAuthInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                template.header("X-MCP-Token", mcpToken);
            }
        };
    }

    /**
     * Feign 全局超时配置：连接超时10秒，读取超时2分钟
     * 应急预案知识库问答可能较慢，默认60秒不够
     */
    @Bean
    public feign.Request.Options requestOptions() {
        return new feign.Request.Options(10, TimeUnit.SECONDS, 120, TimeUnit.SECONDS, true);
    }
}
