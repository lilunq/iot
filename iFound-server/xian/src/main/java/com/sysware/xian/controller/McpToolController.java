package com.sysware.xian.controller;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ai.tool.ToolCallback;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP工具管理接口
 *
 * @author sysware
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@Tag(name = "MCP工具管理", description = "获取MCP所有工具及完整信息")
public class McpToolController {

    private final List<McpSyncClient> mcpSyncClients;

    /**
     * 获取所有MCP工具完整信息
     */
    @GetMapping("/tools")
    @Operation(summary = "获取MCP所有工具", description = "获取所有MCP客户端的工具列表及完整参数信息")
    public Map<String, Object> getAllTools() {
        Map<String, Object> result = new HashMap<>();
        result.put("clientCount", mcpSyncClients.size());

        List<Map<String, Object>> clients = new ArrayList<>();
        int totalToolCount = 0;

        for (int i = 0; i < mcpSyncClients.size(); i++) {
            McpSyncClient client = mcpSyncClients.get(i);
            Map<String, Object> clientInfo = new HashMap<>();
            clientInfo.put("index", i);

            try {
                McpSchema.ListToolsResult toolsResult = client.listTools();
                List<Map<String, Object>> toolList = new ArrayList<>();

                for (McpSchema.Tool tool : toolsResult.tools()) {
                    Map<String, Object> toolInfo = new HashMap<>();
                    toolInfo.put("name", tool.name());
                    toolInfo.put("description", tool.description());
                    toolInfo.put("inputSchema", tool.inputSchema());
                    toolList.add(toolInfo);
                    totalToolCount++;
                }

                clientInfo.put("status", "connected");
                clientInfo.put("toolCount", toolList.size());
                clientInfo.put("tools", toolList);
            } catch (Exception e) {
                clientInfo.put("status", "error");
                clientInfo.put("error", e.getMessage());
                log.error("获取MCP client[{}] 工具列表失败", i, e);
            }

            clients.add(clientInfo);
        }

        result.put("totalToolCount", totalToolCount);
        result.put("clients", clients);

        // 同时返回 ToolCallback 信息
        try {
            ToolCallback[] callbacks = SyncMcpToolCallbackProvider.syncToolCallbacks(mcpSyncClients).toArray(new ToolCallback[0]);
            List<Map<String, Object>> callbackList = new ArrayList<>();

            for (ToolCallback cb : callbacks) {
                Map<String, Object> cbInfo = new HashMap<>();
                cbInfo.put("name", cb.getToolDefinition().name());
                cbInfo.put("description", cb.getToolDefinition().description());
                cbInfo.put("inputSchema", cb.getToolDefinition().inputSchema());
                callbackList.add(cbInfo);
            }

            result.put("toolCallbacks", callbackList);
        } catch (Exception e) {
            result.put("toolCallbackError", e.getMessage());
            log.error("获取ToolCallback信息失败", e);
        }

        return result;
    }
}
