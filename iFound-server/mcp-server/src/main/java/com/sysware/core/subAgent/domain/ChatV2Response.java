package com.sysware.core.subAgent.domain;

import lombok.Data;

/**
 * chatV2 响应体（仅反序列化所需字段，对齐 hermes-proxy ChatResponseV2）
 *
 * @Author: LiYanRu
 * @date: 2026-07-23
 */
@Data
public class ChatV2Response {
    /**
     * 流式累计正文
     */
    private String streamMsg;
    /**
     * 最终消息（message.complete）
     */
    private String msg;
    /**
     * 会话长 ID
     */
    private String sessionId;
}
