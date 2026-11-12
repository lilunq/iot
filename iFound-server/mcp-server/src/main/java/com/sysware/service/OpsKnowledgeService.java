package com.sysware.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.config.OpsKnowledgeProperties;
import com.sysware.swcloud.domian.KnowledgeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * @Description: 知识库查询服务（应急预案/机电设备运维规范等）
 * @Author: LiYanRu
 * @date: 2026-07-16
 **/
@Service
@Slf4j
@RequiredArgsConstructor
public class OpsKnowledgeService {

    private final OpsKnowledgeProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * 向应急预案知识库提问
     *
     * @param question 问题内容
     * @return 知识库结构化响应（answer + reference）
     */
    public KnowledgeResponse query(String question) {
        return query(question, properties.getChatId());
    }

    /**
     * 向应急预案知识库提问（完整参数）
     *
     * @param question        问题内容
     * @param stream          是否流式返回，null时默认false
     * @param retrievalChunks 是否返回检索到的知识分块，null时默认false
     * @param summarize       是否对检索结果进行总结，null时默认false
     * @return 知识库结构化响应（answer + reference）
     */
    public KnowledgeResponse query(String question, Boolean stream, Boolean retrievalChunks, Boolean summarize) {
        return query(question, properties.getChatId(), stream, retrievalChunks, summarize);
    }

    /**
     * 向指定chatId的知识库提问
     *
     * @param question 问题内容
     * @param chatId   知识库chatId
     * @return 知识库结构化响应（answer + reference）
     */
    public KnowledgeResponse query(String question, String chatId) {
        return query(question, chatId, null, false, false);
    }

    /**
     * 向指定chatId的知识库提问（完整参数）
     *
     * @param question        问题内容
     * @param chatId          知识库chatId
     * @param stream          是否流式返回，null时默认false
     * @param retrievalChunks 是否返回检索到的知识分块，null时默认false
     * @param summarize       是否对检索结果进行总结，null时默认false
     * @return 知识库结构化响应（answer + reference）
     */
    public KnowledgeResponse query(String question, String chatId, Boolean stream, Boolean retrievalChunks, Boolean summarize) {
        Map<String, Object> requestBody = Map.of(
                "chat_id", chatId,
                "question", question,
                "stream", Boolean.TRUE.equals(stream),
                "retrievalChunks", Boolean.TRUE.equals(retrievalChunks),
                "summarize", Boolean.TRUE.equals(summarize)
        );

        try {
            String body = restClient.post()
                    .uri(properties.getUrl())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                log.warn("知识库返回空body，question={}", question);
                return KnowledgeResponse.error("知识库返回为空");
            }

            KnowledgeResponse response = objectMapper.readValue(body, KnowledgeResponse.class);

            // 兼容处理：将 answer 为 "无" 的情况转为空字符串
            if (response.getData() != null && "无".equals(response.getData().getAnswer())) {
                response.getData().setAnswer("");
            }

            log.debug("知识库查询成功，question={}", question);
            return response;
        } catch (Exception e) {
            log.error("调用知识库接口失败，question={}", question, e);
            throw new RuntimeException("调用知识库失败: " + e.getMessage());
        }
    }
}
