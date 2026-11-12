package com.sysware.xian.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式聊天sse返回的实体类V2
 *
 * @Author: LiYanRu
 * @date: 2026-07-10
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StreamChatResponseV2<T> {
    /**
     * 消息类型
     */
    private String type;
    /**
     * 消息内容
     */
    private T payload;
    /**
     * 长sessionId
     */
    private String sessionId;
    /**
     * 报表名称（仅查看报表指令返回）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String reportName;
    /**
     * 语音播报内容（部分指令返回，供前端TTS朗读，可与payload不同）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String speakPayload;
}
