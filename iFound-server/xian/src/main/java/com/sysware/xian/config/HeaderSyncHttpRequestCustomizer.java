package com.sysware.xian.config;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;

/**
 * MCP HTTP请求头定制器
 * 用于在MCP客户端请求中添加认证头信息
 *
 * @author sysware
 */
public class HeaderSyncHttpRequestCustomizer implements McpSyncHttpClientRequestCustomizer {

    private final Map<String, String> headers;

    public HeaderSyncHttpRequestCustomizer(Map<String, String> headers) {
        this.headers = headers;
    }

    @Override
    public void customize(HttpRequest.Builder builder, String method, URI endpoint, String body, McpTransportContext context) {
        // 遍历所有配置的请求头并添加到请求中
        headers.forEach(builder::header);
    }
}