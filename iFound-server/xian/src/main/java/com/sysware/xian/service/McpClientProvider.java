package com.sysware.xian.service;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MCP 客户端管理器，支持在连接断开后自动重建客户端（自动重连）。
 * 当工具调用抛出 MCP 连接异常时，标记客户端为失效状态，下次调用时重建。
 */
@Slf4j
@Component
public class McpClientProvider {

    private final String mcpUrl;
    private final String mcpEndpoint;
    private final String mcpToken;
    private final Duration requestTimeout;

    private volatile List<McpSyncClient> clients = Collections.emptyList();
    private volatile boolean connected = false;

    public McpClientProvider(
            @Value("${ai.mcp-url:http://localhost:9001}") String mcpUrl,
            @Value("${ai.mcp-endpoint:/mcp}") String mcpEndpoint,
            @Value("${ai.mcp-token:}") String mcpToken,
            @Value("${spring.ai.mcp.client.request-timeout:20s}") Duration requestTimeout
    ) {
        this.mcpUrl = mcpUrl;
        this.mcpEndpoint = mcpEndpoint;
        this.mcpToken = mcpToken;
        this.requestTimeout = requestTimeout;
        connect();
    }

    /**
     * 建立 MCP 连接，创建同步客户端
     */
    private synchronized void connect() {
        try {
            closeClients();

            Map<String, String> headers = mcpToken != null && !mcpToken.isBlank()
                    ? Map.of("X-MCP-Token", mcpToken)
                    : Collections.emptyMap();

            HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(mcpUrl)
                    .endpoint(mcpEndpoint)
                    .customizeRequest(req -> headers.forEach(req::header))
                    .build();

            McpSyncClient client = McpClient.sync(transport)
                    .requestTimeout(requestTimeout)
                    .build();

            client.initialize();
            clients = List.of(client);
            connected = true;
            log.info("MCP 客户端连接成功, url={}{}", mcpUrl, mcpEndpoint);
        } catch (Exception e) {
            connected = false;
            log.error("MCP 客户端连接失败, url={}{}, error={}", mcpUrl, mcpEndpoint, e.getMessage());
        }
    }

    /**
     * 是否已连接
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * 获取 MCP 工具列表。若连接已断开则自动重连。
     */
    public List<ToolCallback> getTools() {
        if (!connected || clients.isEmpty()) {
            synchronized (this) {
                if (!connected || clients.isEmpty()) {
                    connect();
                }
            }
        }
        if (!connected || clients.isEmpty()) {
            log.warn("MCP 不可用，返回空工具列表");
            return Collections.emptyList();
        }
        try {
            return SyncMcpToolCallbackProvider.syncToolCallbacks(clients);
        } catch (Exception e) {
            log.warn("获取 MCP 工具失败，尝试重连: {}", e.getMessage());
            synchronized (this) {
                try {
                    closeClients();
                    connect();
                } catch (Exception ex) {
                    log.error("MCP 重连失败: {}", ex.getMessage());
                }
                if (connected) {
                    return SyncMcpToolCallbackProvider.syncToolCallbacks(clients);
                }
            }
            return Collections.emptyList();
        }
    }

    /**
     * 标记连接失败，下次 getTools() 时会自动重连
     */
    public void markDisconnected() {
        connected = false;
    }

    private void closeClients() {
        for (McpSyncClient client : clients) {
            try {
                client.close();
            } catch (Exception e) {
                log.debug("关闭 MCP 客户端失败: {}", e.getMessage());
            }
        }
        clients = Collections.emptyList();
    }

    @PreDestroy
    public void destroy() {
        closeClients();
    }
}
