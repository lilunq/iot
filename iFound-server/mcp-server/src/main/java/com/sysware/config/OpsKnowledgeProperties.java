package com.sysware.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @Description: 应急预案知识库查询配置
 * @Author: LiYanRu
 * @date: 2026-07-16
 **/
@Data
@Component
@ConfigurationProperties(prefix = "spring.ops-knowledge")
public class OpsKnowledgeProperties {

    /**
     * 应急预案知识库API地址
     */
    private String url;

    /**
     * 绑定的RagFlow中的chatId
     */
    private String chatId;
}
