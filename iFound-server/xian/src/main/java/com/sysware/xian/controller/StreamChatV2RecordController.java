package com.sysware.xian.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sysware.xian.domain.StreamChatV2Record;
import com.sysware.xian.mapper.StreamChatV2RecordMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "流式AI语音日志", description = "流式AI语音对话记录管理")
public class StreamChatV2RecordController {

    private final StreamChatV2RecordMapper streamChatV2RecordMapper;

    /**
     * 分页查询流式AI语音对话记录
     */
    @GetMapping("/stream-chat/records")
    @Operation(summary = "查询流式AI语音对话记录", description = "分页查询对话记录，支持时间段、用户名、agentId筛选")
    public Map<String, Object> queryRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String start_time,
            @RequestParam(required = false) String end_time,
            @RequestParam(required = false) String user_name,
            @RequestParam(required = false) String agent_id) {

        LambdaQueryWrapper<StreamChatV2Record> wrapper = new LambdaQueryWrapper<>();
        if (start_time != null && !start_time.isEmpty()) {
            wrapper.ge(StreamChatV2Record::getRequestTime, LocalDateTime.parse(start_time));
        }
        if (end_time != null && !end_time.isEmpty()) {
            wrapper.le(StreamChatV2Record::getRequestTime, LocalDateTime.parse(end_time));
        }
        if (user_name != null && !user_name.isEmpty()) {
            wrapper.like(StreamChatV2Record::getUserName, user_name);
        }
        if (agent_id != null && !agent_id.isEmpty()) {
            wrapper.eq(StreamChatV2Record::getAgentId, agent_id);
        }
        wrapper.orderByDesc(StreamChatV2Record::getRequestTime);

        Page<StreamChatV2Record> records = streamChatV2RecordMapper.selectPage(new Page<>(page, size), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "操作成功");
        result.put("data", records);
        return result;
    }

    /**
     * 删除单条对话记录
     */
    @DeleteMapping("/stream-chat/records/{id}")
    @Operation(summary = "删除流式AI语音对话记录", description = "根据ID删除对话记录")
    public Map<String, Object> deleteRecord(@PathVariable Long id) {
        int count = streamChatV2RecordMapper.deleteById(id);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", count > 0 ? "删除成功" : "删除失败");
        return result;
    }

    /**
     * 批量删除指定时间之前的记录
     */
    @DeleteMapping("/stream-chat/records/batch")
    @Operation(summary = "批量删除流式AI语音对话记录", description = "删除指定时间之前的记录，支持相对时间如7d/30d/90d或绝对时间")
    public Map<String, Object> deleteRecordsBefore(@RequestParam String before_time) {
        LocalDateTime cutoffTime;
        if (before_time.matches("\\d+d")) {
            int days = Integer.parseInt(before_time.replace("d", ""));
            cutoffTime = LocalDateTime.now().minusDays(days);
        } else {
            cutoffTime = LocalDateTime.parse(before_time);
        }

        LambdaQueryWrapper<StreamChatV2Record> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(StreamChatV2Record::getRequestTime, cutoffTime);
        int count = streamChatV2RecordMapper.delete(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "删除成功");
        result.put("data", count);
        return result;
    }
}