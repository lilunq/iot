package com.sysware.swcloud.domian;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description: 知识库问答响应（RagFlow原始返回解析后的结构化对象）
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeResponse {

    @Schema(title = "success", description = "知识库调用是否成功", example = "true")
    private Boolean success;

    @Schema(title = "data", description = "知识库答复数据（答案+检索分块）")
    private KnowledgeData data;

    @Schema(title = "error", description = "调用失败时的错误信息，成功时为null", example = "调用知识库失败: 连接超时")
    private String error;

    /**
     * 构造失败响应
     */
    public static KnowledgeResponse error(String msg) {
        KnowledgeResponse resp = new KnowledgeResponse();
        resp.setSuccess(false);
        resp.setError(msg);
        return resp;
    }

    /**
     * 安全获取答案文本（无答案时返回空串）
     */
    public String answerText() {
        return data != null && data.getAnswer() != null ? data.getAnswer() : "";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KnowledgeData {

        @Schema(title = "answer", description = "知识库回答内容，未检索到相关内容时为空串", example = "针对劳动安全问题的管控措施如下……")
        private String answer;

        @Schema(title = "reference", description = "检索到的知识分块原始JSON（retrievalChunks=true时返回，含chunks数组）")
        private JsonNode reference;
    }
}
