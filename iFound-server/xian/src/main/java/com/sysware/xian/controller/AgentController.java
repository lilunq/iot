package com.sysware.xian.controller;

import com.alibaba.cloud.ai.graph.advisors.SkillPromptAugmentAdvisor;
import io.modelcontextprotocol.client.McpSyncClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI Agent 接口
 * <p>
 * 接收用户自然语言指令，通过 spring-ai-alibaba 的技能系统（Skill）和 MCP 工具完成分析与执行。
 * 集成三大核心能力：
 * <ol>
 *   <li>上下文记忆（MessageChatMemoryAdvisor）：通过 conversationId 跨请求保持对话上下文</li>
 *   <li>技能系统（SkillPromptAugmentAdvisor + read_skill）：从 skills 目录加载 SKILL.md，
 *       LLM 先看到技能列表，按需调用 read_skill 读取完整指令，再按指令逐步调用 MCP 工具</li>
 *   <li>MCP 工具（SyncMcpToolCallbackProvider）：自动从 mcp-server 获取所有工具（设备查询、控制等）</li>
 * </ol>
 *
 * @author sysware
 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Tag(name = "AI Agent接口", description = "带上下文记忆的智能Agent，支持Skill技能和MCP工具调用")
public class AgentController {

    private final ChatClient.Builder chatClientBuilder;
    private final List<McpSyncClient> mcpSyncClients;
    private final SkillPromptAugmentAdvisor skillAdvisor;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final ToolCallback readSkillToolCallback;

    /**
     * 智能Agent对话接口
     * <p>
     * 接收用户指令，自动分析意图，按需加载技能并通过MCP工具执行。
     * 支持多轮对话（通过 conversationId 保持上下文）。
     *
     * @param request 包含 instruction（用户指令）和可选的 conversationId（会话ID）
     * @return AI回复内容、会话ID、工具调用信息
     */
    @PostMapping("/chat")
    @Operation(summary = "智能Agent对话", description = "接收用户指令，通过Skill技能和MCP工具分析并执行，支持上下文记忆")
    public Map<String, Object> chat(@RequestBody AgentRequest request) {
        String instruction = request.getInstruction();
        String conversationId = (request.getConversationId() != null && !request.getConversationId().isBlank())
                ? request.getConversationId()
                : UUID.randomUUID().toString();

        log.info("Agent对话: conversationId={}, instruction={}", conversationId, instruction);

        // 1. 合并 MCP 工具 + read_skill 工具
        List<ToolCallback> allTools = new ArrayList<>(SyncMcpToolCallbackProvider.syncToolCallbacks(mcpSyncClients));
        allTools.add(readSkillToolCallback);
        log.info("可用工具数: {}, 工具名: {}", allTools.size(),
                allTools.stream().map(t -> t.getToolDefinition().name()).toList());

        // 2. 构建 ChatClient 并调用
        //    - memoryAdvisor: 通过 conversationId 保持多轮对话上下文
        //    - skillAdvisor: 将技能列表注入系统提示词，LLM 可调用 read_skill 读取技能指令
        //    - allTools: MCP 工具（设备查询/控制等）+ read_skill 工具
        String answer = chatClientBuilder.build().prompt()
                .system("你是西安东站智慧运维平台的AI助手。你可以使用MCP工具查询设备数据、控制设备，" +
                        "也可以通过 read_skill 工具读取技能指令来执行复杂操作流程。" +
                        "收到用户指令后，先判断是否需要使用技能（如设备查询与控制流程、照明控制流程），" +
                        "如果需要，调用 read_skill 工具读取对应技能的完整指令，严格按技能指令逐步调用MCP工具执行。" +
                        "不要凭空编造数据，所有数据必须来自工具实际返回结果。")
                .user(instruction)
                .advisors(memoryAdvisor, skillAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolCallbacks(allTools.toArray(new ToolCallback[0]))
                .call()
                .content();

        return Map.of(
                "success", true,
                "conversationId", conversationId,
                "answer", answer != null ? answer : "",
                "toolCount", allTools.size()
        );
    }

    /**
     * 查看当前已加载的技能列表
     */
    @GetMapping("/skills")
    @Operation(summary = "查看技能列表", description = "查看当前已加载的所有技能")
    public Map<String, Object> listSkills() {
        var registry = skillAdvisor.getSkillRegistry();
        var skills = registry.listAll();
        return Map.of(
                "skillCount", skills.size(),
                "skills", skills.stream().map(s -> Map.of(
                        "name", s.getName(),
                        "description", s.getDescription(),
                        "skillPath", s.getSkillPath() != null ? s.getSkillPath() : ""
                )).toList()
        );
    }

    // ========== DTO ==========

    @lombok.Data
    public static class AgentRequest {
        /** 用户自然语言指令 */
        private String instruction;
        /** 会话ID，传入相同值可保持多轮对话上下文；不传则自动生成 */
        private String conversationId;
    }
}
