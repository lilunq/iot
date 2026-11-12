package com.sysware.core.subAgent;

import com.sysware.core.subAgent.domain.ChatV2Response;
import com.sysware.core.subAgent.domain.SubAgentRequest;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 子 agent 委派业务
 *
 * @Author: LiYanRu
 * @date: 2026-07-23
 **/
@Service
@Slf4j
public class SubAgentService {

    @Resource
    private RestClient subAgentRestClient;

    @Resource
    private SubAgentCache subAgentCache;

    /**
     * 能管 agent 同步对话地址
     */
    private static final String ENERGY_CHAT_V2_URL = "http://192.168.128.235:10095/api/chatV2";

    /**
     * 站房 agent 同步对话地址
     */
    private static final String STATION_AGENT_CHAT_V2_URL = "http://192.168.128.191:10101/api/chatV2";

    /**
     * 会话过期错误标识（sessionKey 失效时远端返回的错误内容）
     */
    private static final String SESSION_EXPIRED_FLAG = "sessionId is required";

    /**
     * 将任务委派给能管 agent（对象重载），同步返回最终文本。
     *
     * @param request 任务与上下文
     * @return chatV2 最终消息
     */
    public String energyAgent(SubAgentRequest request) {
        return energyAgent(request.getTask(), request.getContext());
    }

    /**
     * 将任务委派给站房 agent（对象重载），同步返回最终文本。
     *
     * @param request 任务与上下文
     * @return chatV2 最终消息
     */
    public String stationAgent(SubAgentRequest request) {
        return stationAgent(request.getTask(), request.getContext());
    }

    /**
     * 将任务委派给能管 agent，同步返回最终文本。
     *
     * @param task    任务描述
     * @param context 必要上下文，可空
     * @return chatV2 最终消息（优先 msg，其次 streamMsg）
     */
    public String energyAgent(String task, String context) {
        // 1. 判断是否有会话可以复用
        String sessionLongId = subAgentCache.getEnergySessionId();

        // 2. 当前能源管理Agent gateway未启用，只能调用chatV2接口
        log.info("委派子Agent: task={}", task);
        ChatV2Response response = doChat(ENERGY_CHAT_V2_URL, buildMessage(task, context), sessionLongId);

        // 3. 会话已过期：清空缓存的 sessionKey 后去掉会话id重新请求一次
        if (isSessionExpired(response)) {
            log.warn("能管子Agent会话已过期, 清空缓存后重新请求");
            subAgentCache.evictEnergySessionId();
            sessionLongId = null;
            response = doChat(ENERGY_CHAT_V2_URL, buildMessage(task, context), null);
        }

        if (response == null) {
            return ("子Agent未返回有效内容");
        }

        // 4. 如果没有缓存会话id，则缓存当前的
        if (!StringUtils.hasText(sessionLongId)) {
            subAgentCache.putEnergySessionId(response.getSessionId());
        }

        // 5. 只需要message.complete文本
        String text = response.getMsg();
        if (!StringUtils.hasText(text)) {
            return ("子Agent未返回有效内容");
        }
        return text;
    }

    /**
     * 将任务委派给站房 agent，同步返回最终文本。
     *
     * @param task    任务描述
     * @param context 必要上下文，可空
     * @return chatV2 最终消息（优先 msg，其次 streamMsg）
     */
    public String stationAgent(String task, String context) {
        // 1. 判断是否有会话可以复用
        String sessionLongId = subAgentCache.getStationSessionId();

        log.info("委派站房子Agent: task={}", task);
        ChatV2Response response = doChat(STATION_AGENT_CHAT_V2_URL, buildMessage(task, context), sessionLongId);

        // 2. 会话已过期：清空缓存的 sessionKey 后去掉会话id重新请求一次
        if (isSessionExpired(response)) {
            log.warn("站房子Agent会话已过期, 清空缓存后重新请求");
            subAgentCache.evictStationSessionId();
            sessionLongId = null;
            response = doChat(STATION_AGENT_CHAT_V2_URL, buildMessage(task, context), null);
        }

        if (response == null) {
            return ("子Agent未返回有效内容");
        }

        // 3. 如果没有缓存会话id，则缓存当前的
        if (!StringUtils.hasText(sessionLongId)) {
            subAgentCache.putStationSessionId(response.getSessionId());
        }

        // 4. 只需要message.complete文本
        String text = response.getMsg();
        if (!StringUtils.hasText(text)) {
            return ("子Agent未返回有效内容");
        }
        return text;
    }

    /**
     * 调用 chatV2 接口，有会话id则携带 sessionKey。
     *
     * @param url        agent 对话地址
     * @param message    用户消息
     * @param sessionKey 会话长 id，可空
     * @return chatV2 响应
     */
    private ChatV2Response doChat(String url, String message, String sessionKey) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        if (StringUtils.hasText(sessionKey)) {
            body.put("sessionKey", sessionKey);
        }
        return subAgentRestClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ChatV2Response.class);
    }

    /**
     * 判断响应是否为会话过期（sessionKey 失效）。
     *
     * @param response chatV2 响应
     * @return true 表示会话已过期，需要清空缓存重试
     */
    private boolean isSessionExpired(ChatV2Response response) {
        return response != null && StringUtils.hasText(response.getMsg())
                && response.getMsg().contains(SESSION_EXPIRED_FLAG);
    }

    /**
     * 组装发给子 agent 的完整用户消息。
     *
     * @param task    任务
     * @param context 上下文
     * @return 最终 message
     */
    private String buildMessage(String task, String context) {
        if (!StringUtils.hasText(context)) {
            return task;
        }
        return "[任务]\n" + task + "\n\n[上下文]\n" + context;
    }
}
