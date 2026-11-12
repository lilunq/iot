package com.sysware.controller;

import com.sysware.service.OpsKnowledgeService;
import com.sysware.swcloud.domian.KnowledgeRequest;
import com.sysware.swcloud.domian.KnowledgeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Description: 知识库查询接口（应急预案/机电设备运维规范等）
 * @Author: LiYanRu
 * @date: 2026-07-04
 **/
@RestController
@Tag(name = "知识库接口", description = "知识库问答接口（应急预案/机电设备运维规范）")
@RequestMapping("rag")
@RequiredArgsConstructor
public class RagFlowController {

    private static final String DEVICE_FAULT_CHAT_ID = "bbf9f49c7f2811f191580bb256f6c5db";

    private final OpsKnowledgeService opsKnowledgeService;

    /**
     * @param request 请求体，包含 question、stream、retrievalChunks、summarize、chatId 字段。chatId 可选，传入时使用该 chatId，不传则使用配置文件中的默认 chatId
     * @return 应急预案问答结果（结构化：success + data.answer + data.reference + error）
     * @Description: 应急预案知识库问答
     */
    @Operation(summary = "应急预案问答", description = "返回结构化对象：success调用状态、data.answer答案文本、data.reference检索分块、error错误信息")
    @PostMapping("/ops-knowledge")
    public KnowledgeResponse opsKnowledge(@RequestBody KnowledgeRequest request) {
        String question = request.getQuestion();
        if (question == null || question.isBlank()) {
            return KnowledgeResponse.error("问题不能为空");
        }
        try {
            String chatId = request.getChatId();
            if (chatId != null && !chatId.isBlank()) {
                return opsKnowledgeService.query(question, chatId,
                        request.getStream(), request.getRetrievalChunks(), request.getSummarize());
            }
            return opsKnowledgeService.query(question,
                    request.getStream(), request.getRetrievalChunks(), request.getSummarize());
        } catch (Exception e) {
            return KnowledgeResponse.error("调用知识库失败: " + e.getMessage());
        }
    }

    /**
     * @param request 请求体，包含 question、stream、retrievalChunks、summarize、chatId 字段。chatId 可选，传入时使用该 chatId，不传则使用默认的设备故障知识库 chatId
     * @return 机电设备运维规范问答结果（结构化：success + data.answer + data.reference + error）
     * @Description: 机电设备运维规范与故障处理知识库问答
     */
    @Operation(summary = "机电设备运维规范与故障处理问答", description = "返回结构化对象：success调用状态、data.answer答案文本、data.reference检索分块、error错误信息")
    @PostMapping("/device-fault")
    public KnowledgeResponse deviceFault(@RequestBody KnowledgeRequest request) {
        String question = request.getQuestion();
        if (question == null || question.isBlank()) {
            return KnowledgeResponse.error("问题不能为空");
        }
        try {
            String chatId = request.getChatId();
            if (chatId == null || chatId.isBlank()) {
                chatId = DEVICE_FAULT_CHAT_ID;
            }
            return opsKnowledgeService.query(question, chatId,
                    request.getStream(), request.getRetrievalChunks(), request.getSummarize());
        } catch (Exception e) {
            return KnowledgeResponse.error("调用知识库失败: " + e.getMessage());
        }
    }
}
