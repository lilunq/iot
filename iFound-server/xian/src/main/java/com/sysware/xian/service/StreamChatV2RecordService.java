package com.sysware.xian.service;

import com.sysware.xian.domain.StreamChatV2Record;
import com.sysware.xian.mapper.StreamChatV2RecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * StreamChatV2 记录异步落库服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamChatV2RecordService {

    private final StreamChatV2RecordMapper recordMapper;

    @Async("streamChatRecordExecutor")
    public void saveAsync(
            LocalDateTime requestTime,
            LocalDateTime responseTime,
            String message,
            String userName,
            String userId,
            String agentId,
            String requestSessionId,
            String responseSessionId,
            String messageCompletePayload,
            String actionContent,
            String errorMessage
    ) {
        try {
            StreamChatV2Record record = new StreamChatV2Record();
            record.setRequestTime(requestTime);
            record.setResponseTime(responseTime);
            record.setMessage(message);
            record.setUserName(userName);
            record.setUserId(userId);
            record.setAgentId(agentId == null || agentId.isBlank() ? "commander" : agentId);
            record.setRequestSessionId(requestSessionId);
            record.setResponseSessionId(responseSessionId);
            record.setMessageCompletePayload(messageCompletePayload);
            record.setActionContent(actionContent);
            record.setErrorMessage(errorMessage);
            recordMapper.insert(record);
        } catch (Exception e) {
            log.error("StreamChatV2 调用记录异步落库失败: sessionId={}", responseSessionId, e);
        }
    }
}
