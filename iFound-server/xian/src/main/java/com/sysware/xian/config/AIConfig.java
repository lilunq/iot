package com.sysware.xian.config;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * AI配置类
 *
 * @author sysware
 */
@Configuration
public class AIConfig {

//    @Bean
    public ChatClient chatClient(ChatModel chatModel, List<McpSyncClient> mcpSyncClients) {
        return ChatClient.builder(chatModel)
//                .defaultSystem("你是IoT系统的智能助手，可以根据用户需求调用MCP工具查询数据并分析。")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(
                        MessageWindowChatMemory.builder().build()
                ).build())
                .defaultToolCallbacks(SyncMcpToolCallbackProvider.syncToolCallbacks(mcpSyncClients))
                .build();
    }
}