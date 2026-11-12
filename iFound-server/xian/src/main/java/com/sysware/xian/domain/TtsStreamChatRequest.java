package com.sysware.xian.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "流式语音对话参数")
public class TtsStreamChatRequest {
    /** 语音操作用户名 */
    @Schema(title = "requesterName", description = "请求人姓名", example = "测试", required = true)
    private String requesterName;
    
    /** 输入内容 */
    @Schema(title = "message", description = "语音内容", example = "你好", required = true)
    private String message;
    
    /** 上一次的数据返回id */
    @Schema(title = "previousResponseId", description = "上一次的数据返回id", example = "123", required = false)
    private String previousResponseId;
    
    /** 会话ID */
    @Schema(title = "sessionKey", description = "会话ID，不传则创建新会话", example = "session-123", required = false)
    private String sessionKey;
    
    /** 用户ID */
    @Schema(title = "userId", description = "用户ID，用于会话隔离", example = "user-123", required = false)
    private String userId;
}