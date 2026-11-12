package com.sysware.xian.service;

import com.alibaba.cloud.ai.graph.advisors.SkillPromptAugmentAdvisor;
import com.alibaba.cloud.ai.graph.agent.hook.skills.ReadSkillTool;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.sysware.xian.domain.AiAnalysisResult;
import com.sysware.xian.swcloud.domain.AiAnalysisPageResult;
import com.sysware.xian.util.MarkdownToPdfConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 生成报表的对话接口
 *
 * @Author: LiYanRu
 * @date: 2026-07-21
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {
    /** 运行报告分析记录类型标识（存入 ai_analysis_result.type） */
    public static final String RESULT_TYPE_REPORT = "report";
    /** 定时生成运行报告使用的固定指令 */
    private static final String REPORT_PROMPT = "生成运行报告";

    private final ChatClient.Builder builder;
    /** MCP 客户端管理器（支持自动重连） */
    private final McpClientProvider mcpClientProvider;
    private final StreamChatV2RecordService streamChatV2RecordService;
    /** AI 分析结果落库与分页查询服务 */
    private final AiAnalysisResultService aiAnalysisResultService;

    @Value("${agent.skills-directory}")
    private String reportSkillsDirectory;

    public String reportChat(String input) {
        return reportChat(input, null, null, null);
    }

    public String reportChat(String input, String userName, String userId, String sessionId) {
        LocalDateTime requestTime = LocalDateTime.now();

        // 1. 从配置目录加载报表技能
        log.debug("[报表] 技能目录配置: {}", reportSkillsDirectory);
        SkillRegistry registry = FileSystemSkillRegistry.builder()
                .projectSkillsDirectory(reportSkillsDirectory)
                .userSkillsDirectory("")
                .build();
        log.debug("[报表] 加载 skill 数量: {}, 列表: {}", registry.size(),
                registry.listAll().stream().map(s -> s.getName()).collect(Collectors.toList()));

        SkillPromptAugmentAdvisor skillAdvisor = SkillPromptAugmentAdvisor.builder()
                .skillRegistry(registry)
                .build();

        // 2. MCP 工具+ read_skill
        List<ToolCallback> allTools = new ArrayList<>(mcpClientProvider.getTools());
        allTools.add(ReadSkillTool.createReadSkillToolCallback(registry, null));
        log.debug("[报表] 加载 MCP tool 数量: {}, 列表: {}",
                allTools.size(),
                allTools.stream().map(t -> t.getToolMetadata()).collect(Collectors.toList()));

        // 3. 单次 prompt 挂 advisor + 全部 tools
        log.debug("[报表] 开始调用 AI, prompt={}", input);
        long start = System.currentTimeMillis();
        ChatClient chatClient = builder.build();
        String content = chatClient.prompt()
                .user(input)
                .advisors(skillAdvisor)
                .toolCallbacks(allTools.toArray(new ToolCallback[0]))
                .call()
                .content();
        long cost = System.currentTimeMillis() - start;
        log.debug("报表生成完成, 耗时={}ms, 内容长度={}, 内容={}",
                cost,
                content != null ? content.length() : 0,
                content != null ? content:"");

        // 4. 异步保存报表记录到 stream_chat_v2_record
        LocalDateTime completeTime = LocalDateTime.now();
        try {
            streamChatV2RecordService.saveAsync(
                    requestTime,
                    completeTime,
                    input,
                    userName,
                    userId,
                    "commander",
                    sessionId,
                    sessionId,
                    content,
                    null,
                    null
            );
        } catch (Exception e) {
            log.error("报表记录异步落库失败", e);
        }

        //String toHtml = MarkdownToPdfConverter.mdToHtml(content);
        return content;
    }

    /**
     * 生成运行报告并落库到 ai_analysis_result（type=report）。
     * 由 ScheduledService 每日凌晨1点调用；语音"生成运行报告"直接复用最新一条，避免实时生成的长耗时。
     */
    public void scheduledGenerateReport() {
        log.info("定时运行报告生成任务开始");
        try {
            String content = reportChat(REPORT_PROMPT);
            saveReportRecord(content);
            log.info("定时运行报告生成任务完成");
        } catch (Exception e) {
            log.error("定时运行报告生成任务失败", e);
        }
    }

    /**
     * 持久化运行报告到 ai_analysis_result 表（失败不影响任务）。
     * result_data 存 AI 输出的 Markdown 正文；o_data（本次 MCP 工具返回内容）难以从 ChatClient 内部获取，暂置空。
     *
     * @param content AI 生成的报告 Markdown 正文
     */
    public void saveReportRecord(String content) {
        try {
            AiAnalysisResult record = new AiAnalysisResult();
            record.setType(RESULT_TYPE_REPORT);
            record.setResultData(content);
            record.setRemake("运行报告");
            record.setCreateTime(LocalDateTime.now());
            aiAnalysisResultService.save(record);
            log.info("运行报告已持久化: id={}, 内容长度={}", record.getId(), content != null ? content.length() : 0);
        } catch (Exception e) {
            log.error("运行报告持久化失败", e);
        }
    }

    /**
     * 获取最新一次已落库的运行报告 Markdown 正文（复用 pageQuery，取 type=report 倒序第一条）。
     *
     * @return 最新运行报告内容；无记录时返回 null
     */
    public String getLatestReportContent() {
        AiAnalysisPageResult page = aiAnalysisResultService.pageQuery(RESULT_TYPE_REPORT, 1, 1, null);
        if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
            return null;
        }
        return page.getRecords().get(0).getResultDataRaw();
    }
}
