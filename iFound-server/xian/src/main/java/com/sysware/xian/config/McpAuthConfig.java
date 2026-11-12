package com.sysware.xian.config;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP客户端认证配置类
 * 为MCP客户端请求添加认证头信息
 *
 * @author sysware
 */
@Configuration
public class McpAuthConfig {

    /**
     * MCP认证Token
     * 可通过配置文件或环境变量配置
     */
    @Value("${ai.mcp-token:mcp-server-secret-token-2026}")
    private String mcpToken;

    /**
     * 配置MCP HTTP请求头定制器
     * 自动在所有MCP请求中添加认证头
     *
     * @return MCP请求头定制器
     */
    @Bean
    public McpSyncHttpClientRequestCustomizer mcpSyncHttpClientRequestCustomizer() {
        Map<String, String> headers = new HashMap<>();
        // 添加MCP认证Token头
        headers.put("X-MCP-Token", mcpToken);

        return new HeaderSyncHttpRequestCustomizer(headers);
    }
}