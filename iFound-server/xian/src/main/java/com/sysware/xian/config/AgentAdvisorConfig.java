package com.sysware.xian.config;

import com.alibaba.cloud.ai.graph.advisors.SkillPromptAugmentAdvisor;
import com.alibaba.cloud.ai.graph.agent.hook.skills.ReadSkillTool;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentController 使用的技能和对话记忆组件。
 *
 * Skill Advisor 与 read_skill 必须共享同一个 SkillRegistry，否则提示词中展示的技能
 * 可能无法被 read_skill 正确读取。
 */
@Configuration
public class AgentAdvisorConfig {

    @Bean
    public SkillRegistry skillRegistry(@Value("${agent.skills-directory:../skill}") String skillsDirectory) {
        return FileSystemSkillRegistry.builder()
                .projectSkillsDirectory(skillsDirectory)
                .build();
    }

    @Bean
    public SkillPromptAugmentAdvisor skillPromptAugmentAdvisor(SkillRegistry skillRegistry) {
        return SkillPromptAugmentAdvisor.builder()
                .skillRegistry(skillRegistry)
                .build();
    }

    @Bean("readSkillToolCallback")
    public ToolCallback readSkillToolCallback(SkillRegistry skillRegistry) {
        return ReadSkillTool.createReadSkillToolCallback(skillRegistry, null);
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor() {
        return MessageChatMemoryAdvisor.builder(
                MessageWindowChatMemory.builder().build()
        ).build();
    }
}
