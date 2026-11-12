package com.sysware.xian.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 语音对话记录实体
 */
@Data
@TableName("tts_conversation")
public class TtsConversation {
    @TableId(type = IdType.AUTO)
    private String id;
    private LocalDateTime requestTime;
    private String requestIp;
    private String requesterName;
    private String inputText;
    private String previousResponseId;
    private LocalDateTime responseTime;
    private String responseContent;
    private String responseId;
}
