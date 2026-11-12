package com.sysware.xian.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * StreamChatV2 调用记录。
 */
@Data
@TableName("stream_chat_v2_record")
public class StreamChatV2Record {

    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDateTime requestTime;
    private LocalDateTime responseTime;
    private String message;
    private String userName;
    private String userId;
    private String agentId;
    private String requestSessionId;
    private String responseSessionId;
    private String messageCompletePayload;
    private String actionContent;
    private String errorMessage;
}
