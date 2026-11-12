package com.sysware.xian.swcloud.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description: 知识库问答请求参数
 * @Author: LiYanRu
 * @date: 2026-07-16
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRequest {
    @Schema(title = "question", description = "提问问题", requiredMode = Schema.RequiredMode.REQUIRED, example = "如何排查变频器故障")
    private String question;

    @Schema(title = "stream", description = "是否流式返回，默认false", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "false")
    private Boolean stream;

    @Schema(title = "retrievalChunks", description = "是否直接查询关联数据块，默认false。false时使用Ragflow聊天助手总结（10~40秒，answer带引用坐标）；true时不总结，answer为空串（1秒内），需配合summarize使用", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "false")
    private Boolean retrievalChunks;

    @Schema(title = "summarize", description = "是否使用大模型对检索块进行总结，默认false，仅当retrievalChunks=true时生效。比聊天助手略快，但总结结果不含引用数据块坐标", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "false")
    private Boolean summarize;

    @Schema(title = "chatId", description = "知识库chatId，可选。传入时使用该chatId，不传则使用配置文件中的默认chatId", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "9e8e17147e8b11f191580bb256f6c5db")
    private String chatId;

    public KnowledgeRequest(String question) {
        this.question = question;
    }
}
