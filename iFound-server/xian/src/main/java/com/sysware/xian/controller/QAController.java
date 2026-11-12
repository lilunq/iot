package com.sysware.xian.controller;

import io.modelcontextprotocol.client.McpSyncClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 问答接口
 *
 * @author sysware
 */
@Slf4j
@RestController
@RequestMapping("/qa")
@RequiredArgsConstructor
@Tag(name = "大模型问答接口", description = "用户提问和控制命令")
public class QAController {

    private final List<McpSyncClient> mcpSyncClients;
    private final ChatClient.Builder chatClientBuilder;

    /**
     * 诊断接口：查看MCP连接状态和可用工具
     */
    @GetMapping("/tools")
    @Operation(summary = "查看MCP工具列表", description = "诊断MCP连接和工具注册状态")
    public Map<String, Object> listTools() {
        Map<String, Object> result = new HashMap<>();
        result.put("mcpClientCount", mcpSyncClients.size());

        List<Map<String, Object>> clientDetails = new ArrayList<>();
        for (int i = 0; i < mcpSyncClients.size(); i++) {
            McpSyncClient client = mcpSyncClients.get(i);
            Map<String, Object> detail = new HashMap<>();
            detail.put("clientIndex", i);
            try {
                var tools = client.listTools();
                List<Map<String, String>> toolList = new ArrayList<>();
                tools.tools().forEach(tool -> {
                    Map<String, String> t = new HashMap<>();
                    t.put("name", tool.name());
                    t.put("description", tool.description());
                    toolList.add(t);
                });
                detail.put("toolCount", toolList.size());
                detail.put("tools", toolList);
                detail.put("status", "connected");
            } catch (Exception e) {
                detail.put("status", "error");
                detail.put("error", e.getMessage());
            }
            clientDetails.add(detail);
        }
        result.put("clients", clientDetails);

        // 检查 SyncMcpToolCallbackProvider 转换后的 ToolCallback
        try {
            List<ToolCallback>  callbacks = SyncMcpToolCallbackProvider.syncToolCallbacks(mcpSyncClients);
            List<String> callbackNames = new ArrayList<>();
            for (ToolCallback cb : callbacks) {
                callbackNames.add(cb.getToolDefinition().name());
            }
            result.put("toolCallbackCount", callbacks.size());
            result.put("toolCallbackNames", callbackNames);
        } catch (Exception e) {
            result.put("toolCallbackError", e.getMessage());
        }

        return result;
    }

    /**
     * 手动重连MCP服务（会话丢失时调用）
     */
    @PostMapping("/reconnect")
    @Operation(summary = "重连MCP服务", description = "当MCP会话丢失时手动重新建立连接")
    public Map<String, Object> reconnect() {
        log.info("手动重连MCP服务...");
        List<String> results = new ArrayList<>();
        for (int i = 0; i < mcpSyncClients.size(); i++) {
            try {
                mcpSyncClients.get(i).initialize();
                var tools = mcpSyncClients.get(i).listTools();
                results.add("client[" + i + "]: 重连成功, 工具数=" + tools.tools().size());
                log.info("MCP client[{}] 重连成功, 工具数={}", i, tools.tools().size());
            } catch (Exception e) {
                results.add("client[" + i + "]: 重连失败 - " + e.getMessage());
                log.error("MCP client[{}] 重连失败", i, e);
            }
        }
        return Map.of("success", true, "results", results);
    }

    /**
     * 用户提问
     */
    @PostMapping("/ask")
    @Operation(summary = "用户提问", description = "用户向AI提问")
    public Map<String, Object> ask(@RequestBody AskRequest request) {
        log.info("用户提问: userId={}, question={}", request.getUserId(), request.getQuestion());

        // 记录可用工具数量
        List<ToolCallback>  callbacks = SyncMcpToolCallbackProvider.syncToolCallbacks(mcpSyncClients);
        log.info("MCP工具数量: {}, 工具名: {}", callbacks.size(),
                callbacks.stream().map(cb -> cb.getToolDefinition().name()).toList());

        ChatClient chatClient = chatClientBuilder
                .defaultSystem("你是IoT系统的智能助手，可以根据用户需求调用MCP工具查询数据并分析。不要凭空编造不存在的东西。")
                .build();

        String answer = chatClient.prompt()
                .user(request.getQuestion())
                .toolCallbacks(callbacks)
                .call()
                .content();

        return Map.of(
                "success", true,
                "answer", answer,
                "toolCount", callbacks.size()
        );
    }



    // DTO类
    @lombok.Data
    public static class AskRequest {
        private String userId;
        private String question;
    }

}