package com.sysware.xian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sysware.xian.domain.AgentConfig;
import com.sysware.xian.domain.TtsConversation;
import com.sysware.xian.mapper.TtsConversationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语音对话记录服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsConversationService {

    private final TtsConversationMapper ttsConversationMapper;
    private final RestTemplate restTemplate;
    private final AgentConfigService agentConfigService;
    private final ChatModel chatModel;

    @Value("${ai.tts-api-url}")
    private String ttsApiUrl;

    /**
     * 缓存每个Agent的最新responseId，key为agentId，value为responseId
     */
    private final Map<String, String> agentResponseIdCache = new ConcurrentHashMap<>();

    /**
     * 调用TTS接口并存储记录
     */
    public Map<String, Object> callTtsAndSave(String input, String previousResponseId, String requesterName) {
        LocalDateTime requestTime = LocalDateTime.now();

        // 先插入记录，填入请求时间、请求人、input
        TtsConversation record = new TtsConversation();
        record.setRequestTime(requestTime);
        record.setRequesterName(requesterName);
        record.setInputText(input);
        record.setPreviousResponseId(previousResponseId);
        ttsConversationMapper.insert(record);

        // 构建请求参数
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("input", input);
        requestBody.put("previous_response_id", previousResponseId != null ? previousResponseId : "");

        // 调用外部TTS接口
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        Map<String, Object> response = null;

        try {
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    ttsApiUrl, HttpMethod.POST, entity, Map.class);
            response = responseEntity.getBody();
        } catch (Exception e) {
            log.error("调用TTS接口失败: {}", e.getMessage(), e);
            response = new HashMap<>();
            response.put("error", "调用TTS接口失败: " + e.getMessage());
        }

        LocalDateTime responseTime = LocalDateTime.now();
        // 更新该条记录的响应结果和时间
        record.setResponseTime(responseTime);
        record.setResponseContent(response != null ? response.toString() : "");
        record.setResponseId(response != null ? (String) response.get("id") : null);
        ttsConversationMapper.updateById(record);

        return response;
    }

    /**
     * 保存无权限控制记录（先插入请求记录，再更新响应结果）
     */
    public void saveNoPermissionRecord(String requesterName, String inputText, String previousResponseId, Map<String, Object> response) {
        LocalDateTime requestTime = LocalDateTime.now();

        // 先插入请求记录
        TtsConversation record = new TtsConversation();
        record.setRequestTime(requestTime);
        record.setRequesterName(requesterName);
        record.setInputText(inputText);
        record.setPreviousResponseId(previousResponseId);
        record.setResponseTime(requestTime);
        record.setResponseId(previousResponseId);
        record.setResponseContent(response.toString());
        ttsConversationMapper.insert(record);
    }

    /**
     * 分页查询对话记录
     */
    public Page<TtsConversation> queryRecords(int page, int size, String startTime, String endTime) {
        LambdaQueryWrapper<TtsConversation> wrapper = new LambdaQueryWrapper<>();

        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(TtsConversation::getRequestTime, LocalDateTime.parse(startTime));
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(TtsConversation::getRequestTime, LocalDateTime.parse(endTime));
        }

        wrapper.orderByDesc(TtsConversation::getRequestTime);

        return ttsConversationMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 删除单条对话记录
     */
    public boolean deleteRecord(Long id) {
        return ttsConversationMapper.deleteById(id) > 0;
    }

    /**
     * 批量删除指定时间之前的记录
     * @param beforeTime 删除此时间之前的记录，格式如 "2026-06-06T10:00:00" 或相对时间如 "7d"(7天前)、"30d"(30天前)
     * @return 删除的记录数
     */
    public int deleteRecordsBefore(String beforeTime) {
        LocalDateTime cutoffTime;

        // 支持相对时间格式，如 "7d"、"30d"
        if (beforeTime.matches("\\d+d")) {
            int days = Integer.parseInt(beforeTime.replace("d", ""));
            cutoffTime = LocalDateTime.now().minusDays(days);
        } else {
            // 绝对时间格式
            cutoffTime = LocalDateTime.parse(beforeTime);
        }

        LambdaQueryWrapper<TtsConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(TtsConversation::getRequestTime, cutoffTime);

        return ttsConversationMapper.delete(wrapper);
    }

    /**
     * 流式调用TTS接口并存储记录
     * 先插入请求记录，然后流式转发hermes-proxy的SSE响应，最后更新完整响应内容
     */
    public void streamCallTtsAndSave(String input, String previousResponseId, String requesterName, String sessionKey, SseEmitter emitter) {
        LocalDateTime requestTime = LocalDateTime.now();

        // 先插入记录，填入请求时间、请求人、input
        TtsConversation record = new TtsConversation();
        record.setRequestTime(requestTime);
        record.setRequesterName(requesterName);
        record.setInputText(input);
        record.setPreviousResponseId(previousResponseId);
        ttsConversationMapper.insert(record);

        // 使用StringBuilder累积完整响应内容
        StringBuilder fullResponseBuilder = new StringBuilder();
        String[] finalSessionKey = new String[1];
        
        try {
            // 构建请求参数
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("message", input);
            if (sessionKey != null && !sessionKey.isEmpty()) {
                requestBody.put("sessionKey", sessionKey);
            }
            if (requesterName != null && !requesterName.isEmpty()) {
                requestBody.put("userId", requesterName);
            }

            // 创建WebClient实例
            WebClient webClient = WebClient.builder()
                    .baseUrl(ttsApiUrl.replace("/api/responses", ""))
                    .build();

            // 使用WebClient发送SSE请求，接收原始字符串流
            Flux<String> sseFlux = webClient.post()
                    .uri("/api/chat/stream")
                    .bodyValue(requestBody)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class);

            // 用于累积SSE数据的缓冲区
            StringBuilder sseBuffer = new StringBuilder();
            String[] currentEventName = new String[1];
            StringBuilder currentEventData = new StringBuilder();

            // 订阅响应流并转发到SSE发射器
            sseFlux.subscribe(
                    chunk -> {
                        try {
                            sseBuffer.append(chunk);
                            
                            // 按行分割处理
                            String content = sseBuffer.toString();
                            String[] lines = content.split("\n", -1);
                            
                            // 如果最后一行不完整，保留在缓冲区
                            sseBuffer.setLength(0);
                            for (int i = 0; i < lines.length; i++) {
                                String line = lines[i];
                                if (i == lines.length - 1 && !line.isEmpty()) {
                                    // 最后一行可能不完整，保留
                                    sseBuffer.append(line);
                                    break;
                                }
                                
                                line = line.trim();
                                if (line.startsWith("event:")) {
                                    currentEventName[0] = line.substring(6).trim();
                                } else if (line.startsWith("data:")) {
                                    String data = line.substring(5).trim();
                                    if (!data.isEmpty()) {
                                        currentEventData.append(data);
                                    }
                                } else if (line.isEmpty()) {
                                    // 空行表示事件结束
                                    if (currentEventData.length() > 0) {
                                        String eventData = currentEventData.toString();
                                        
                                        // 累积响应内容
                                        fullResponseBuilder.append(eventData).append("\n");
                                        
                                        // 提取sessionKey
                                        try {
                                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                            com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(eventData);
                                            if (jsonNode.has("sessionKey")) {
                                                finalSessionKey[0] = jsonNode.get("sessionKey").asText();
                                            }
                                        } catch (Exception e) {
                                            // 忽略解析错误
                                        }
                                        
                                        // 转发SSE事件到客户端
                                        SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event()
                                                .data(eventData);
                                        if (currentEventName[0] != null && !currentEventName[0].isEmpty()) {
                                            eventBuilder.name(currentEventName[0]);
                                        }
                                        emitter.send(eventBuilder);
                                        
                                        // 重置
                                        currentEventData.setLength(0);
                                        currentEventName[0] = null;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("转发SSE事件失败: {}", e.getMessage());
                        }
                    },
                    error -> {
                        log.error("流式请求发生错误: {}", error.getMessage());
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data("{\"error\":\"流式调用失败: " + error.getMessage() + "\"}"));
                            emitter.complete();
                            
                            // 更新失败记录
                            LocalDateTime responseTime = LocalDateTime.now();
                            record.setResponseTime(responseTime);
                            record.setResponseContent("调用失败: " + error.getMessage());
                            ttsConversationMapper.updateById(record);
                        } catch (Exception ex) {
                            log.error("发送错误事件失败: {}", ex.getMessage());
                        }
                    },
                    () -> {
                        try {
                            // 处理缓冲区中剩余的数据
                            if (sseBuffer.length() > 0) {
                                String line = sseBuffer.toString().trim();
                                if (line.startsWith("data:")) {
                                    String data = line.substring(5).trim();
                                    if (!data.isEmpty()) {
                                        fullResponseBuilder.append(data).append("\n");
                                        
                                        SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event()
                                                .data(data);
                                        if (currentEventName[0] != null && !currentEventName[0].isEmpty()) {
                                            eventBuilder.name(currentEventName[0]);
                                        }
                                        emitter.send(eventBuilder);
                                    }
                                }
                            }
                            
                            // 完成时发送done事件
                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data("{\"sessionKey\":\"" + (finalSessionKey[0] != null ? finalSessionKey[0] : (sessionKey != null ? sessionKey : "")) + "\"}"));
                            emitter.complete();
                            
                            // 更新该条记录的响应结果和时间
                            LocalDateTime responseTime = LocalDateTime.now();
                            record.setResponseTime(responseTime);
                            record.setResponseContent(fullResponseBuilder.toString());
                            record.setResponseId(finalSessionKey[0]);
                            ttsConversationMapper.updateById(record);
                        } catch (Exception e) {
                            log.error("完成SSE连接失败: {}", e.getMessage());
                        }
                    }
            );

        } catch (Exception e) {
            log.error("流式调用TTS接口失败: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"error\":\"流式调用失败: " + e.getMessage() + "\"}"));
                emitter.complete();
            } catch (Exception ex) {
                log.error("发送错误事件失败: {}", ex.getMessage());
            }
            
            // 更新失败记录
            LocalDateTime responseTime = LocalDateTime.now();
            record.setResponseTime(responseTime);
            record.setResponseContent("调用失败: " + e.getMessage());
            ttsConversationMapper.updateById(record);
        }
    }

    /**
     * 根据用户意图路由到最佳Agent并调用其Hermes接口
     * 参数和处理流程与 callTtsAndSave 相同，只是根据输入内容智能选择Agent
     */
    public Map<String, Object> callTtsWithAgentRouting(String input, String previousResponseId, String requesterName) {
        LocalDateTime requestTime = LocalDateTime.now();

        // 先插入记录，填入请求时间、请求人、input
        TtsConversation record = new TtsConversation();
        record.setRequestTime(requestTime);
        record.setRequesterName(requesterName);
        record.setInputText(input);
        record.setPreviousResponseId(previousResponseId);
        ttsConversationMapper.insert(record);

        // 根据用户输入内容智能匹配最佳Agent
        AgentConfig matchedAgent = routeToBestAgent(input);

        log.debug("用户输入: {}, 匹配到的Agent Hermes URL: {}", input, matchedAgent.getHermesUrl());

        // 构建请求参数
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("input", input);

        // 根据matchedAgent的agentId获取缓存的responseId
        String cachedResponseId = null;
        if (matchedAgent != null && matchedAgent.getAgentId() != null) {
            cachedResponseId = agentResponseIdCache.get(matchedAgent.getAgentId());
        }
        requestBody.put("previous_response_id", cachedResponseId != null ? cachedResponseId : (previousResponseId != null ? previousResponseId : ""));

        // 调用匹配到的Agent的Hermes接口
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        Map<String, Object> response = null;

        try {
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    matchedAgent.getHermesUrl(), HttpMethod.POST, entity, Map.class);
            response = responseEntity.getBody();
        } catch (Exception e) {
            log.error("调用Agent Hermes接口失败: {}", e.getMessage(), e);
            response = new HashMap<>();
            response.put("error", "调用Agent Hermes接口失败: " + e.getMessage());
        }

        LocalDateTime responseTime = LocalDateTime.now();
        // 更新该条记录的响应结果和时间
        record.setResponseTime(responseTime);
        record.setResponseContent(response != null ? response.toString() : "");
        String responseId = response != null ? (String) response.get("id") : null;
        record.setResponseId(responseId);
        ttsConversationMapper.updateById(record);

        // 缓存responseId到matchedAgent的agentId
        if (matchedAgent != null && matchedAgent.getAgentId() != null && responseId != null) {
            agentResponseIdCache.put(matchedAgent.getAgentId(), responseId);
        }

        return response;
    }

    /**
     * 根据用户输入内容智能匹配最佳Agent的Hermes URL
     * 使用AI判断input与哪个Agent的businessDomains最相关
     * 但凡有关能耗/电/电能的都采用能源管理Agent
     */
    public AgentConfig routeToBestAgent(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        // 获取所有可用Agent
        List<AgentConfig> agents = agentConfigService.listAvailable();
        if (agents == null || agents.isEmpty()) {
            return null;
        }

        // 先用AI判断最相关的Agent
        AgentConfig aiMatchedAgent = matchAgentByAI(input, agents);
        if (aiMatchedAgent != null && aiMatchedAgent.getHermesUrl() != null && !aiMatchedAgent.getHermesUrl().isEmpty()) {
            log.debug("AI匹配到Agent: {}, Hermes URL: {}", aiMatchedAgent.getAgentName(), aiMatchedAgent.getHermesUrl());
            return aiMatchedAgent;
        }

        // AI未匹配到，优先 fallback 到 commander。
        for (AgentConfig agent : agents) {
            if ("commander".equals(agent.getAgentId())
                    && agent.getHermesUrl() != null && !agent.getHermesUrl().isEmpty()) {
                log.debug("AI未匹配，使用默认Agent: {}, Hermes URL: {}", agent.getAgentName(), agent.getHermesUrl());
                return agent;
            }
        }

        // commander 未配置时，fallback 到第一个有 Hermes URL 的可用 Agent。
        for (AgentConfig agent : agents) {
            if (agent.getHermesUrl() != null && !agent.getHermesUrl().isEmpty()) {
                log.debug("AI未匹配，使用默认Agent: {}, Hermes URL: {}", agent.getAgentName(), agent.getHermesUrl());
                return agent;
            }
        }

        log.debug("未找到配置的Hermes URL，使用默认TTS API URL");
        return agents.get(0);
    }

    /**
     * 使用AI判断用户输入与哪个Agent的业务域最相关
     */
    private AgentConfig matchAgentByAI(String input, List<AgentConfig> agents) {
        try {
            // 构建Agent列表描述
            StringBuilder agentDesc = new StringBuilder();
            for (int i = 0; i < agents.size(); i++) {
                AgentConfig agent = agents.get(i);
                agentDesc.append(i + 1).append(". Agent名称: ").append(agent.getAgentName());
                if (agent.getBusinessDomains() != null && !agent.getBusinessDomains().isEmpty()) {
                    agentDesc.append(", 业务域: ").append(agent.getBusinessDomains());
                }
                agentDesc.append("\n");
            }

            String prompt = String.format(
                    "你是一个意图识别助手。请根据用户输入，判断与以下哪个Agent的业务域最相关，只返回对应的序号数字（1-%d）。\n\n" +
                    "可用Agent列表：\n%s\n" +
                    "用户输入：%s\n\n" +
                    "匹配规则：\n" +
                    "1. 但凡用户输入中包含能耗、电、电能、用电量、用电量分析、节能、用能等与能源管理相关的词汇，必须返回能源管理Agent的序号。\n" +
                    "2. 其他情况返回业务域最匹配的Agent序号。\n" +
                    "3. 如果都不相关，返回0。\n" +
                    "4. 只返回一个数字，不要返回其他内容。",
                    agents.size(), agentDesc.toString(), input
            );

            String result = chatModel.call(prompt);
            if (result != null) {
                result = result.trim();
                // 提取数字
                String numStr = result.replaceAll("[^0-9]", "");
                if (!numStr.isEmpty()) {
                    int index = Integer.parseInt(numStr);
                    if (index > 0 && index <= agents.size()) {
                        return agents.get(index - 1);
                    }
                }
            }
        } catch (Exception e) {
            log.error("AI匹配Agent失败: {}", e.getMessage(), e);
        }
        return null;
    }
}
