package com.sysware.xian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import com.sysware.xian.swcloud.fegin.MCPXClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hermes Gateway WebSocket 监听服务。
 * <p>
 * 连接 ws://host:port/gateway/tool-payload，监听车站运营 Hermes 的工具调用事件。
 * 收到 tool_name=mcp__iot_mcp__getStationInfo 时，提取 areaCode，
 * 通过 MCPXClient 查询客流区域信息，封装 useData 事件推送给活跃 SSE 连接。
 * <p>
 * 断线自动重连，间隔 2 秒。
 *
 * @Author: LiYanRu
 * @date: 2026-08-03
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GatewayWebSocketService {

    private final ObjectMapper objectMapper;
    private final MCPXClient mcpXClient;
    private final StreamChatV2Service streamChatV2Service;

    @Value("${ai.gateway-ws-url:ws://192.168.128.191:10101/gateway/tool-payload}")
    private String wsUrl;

    /** 是否启用 Gateway WebSocket 连接，环境不具备时可关闭以避免重复重连 */
    @Value("${ai.gateway-ws-enabled:true}")
    private boolean enabled;

    /** 重连间隔（秒） */
    private static final long RECONNECT_DELAY_SECONDS = 2;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean manualClose = new AtomicBoolean(false);
    private volatile WebSocketClient client;
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-reconnect");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> reconnectTask;

    /**
     * 应用启动后自动连接 WebSocket。
     */
    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("Gateway WebSocket 已禁用（ai.gateway-ws-enabled=false），不建立连接");
            return;
        }
        running.set(true);
        connect();
        log.info("GatewayWebSocketService 已启动，目标地址: {}", wsUrl);
    }

    @PreDestroy
    public void destroy() {
        running.set(false);
        manualClose.set(true);
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
        }
        reconnectExecutor.shutdownNow();
        if (client != null && client.isOpen()) {
            try {
                client.close();
            } catch (Exception e) {
                log.debug("关闭 WebSocket 连接时忽略异常: {}", e.getMessage());
            }
        }
        log.info("GatewayWebSocketService 已停止");
    }

    /**
     * 创建并连接 WebSocket 客户端。
     */
    private void connect() {
        if (!running.get()) return;
        try {
            client = new WebSocketClient(new URI(wsUrl)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("Gateway WebSocket 已连接: {}", wsUrl);
                    // 连接成功后取消待执行的重连任务
                    if (reconnectTask != null) {
                        reconnectTask.cancel(false);
                        reconnectTask = null;
                    }
                }

                @Override
                public void onMessage(String message) {
                    log.info("收到 Gateway WebSocket 消息: {}", message);
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.info("Gateway WebSocket 已断开: code={}, reason={}, remote={}", code, reason, remote);
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    log.warn("Gateway WebSocket 异常: {}", ex.getMessage());
                    // onError 后通常会触发 onClose，由 onClose 调度重连
                }
            };
            client.connect();
        } catch (Exception e) {
            log.error("创建 WebSocket 客户端失败: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    /**
     * 调度重连任务，间隔 RECONNECT_DELAY_SECONDS 秒。
     */
    private void scheduleReconnect() {
        if (!running.get() || manualClose.get()) return;
        if (reconnectTask != null && !reconnectTask.isDone()) {
            return; // 已有重连任务在等待
        }
        reconnectTask = reconnectExecutor.schedule(() -> {
            log.info("正在重连 Gateway WebSocket: {}", wsUrl);
            connect();
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 处理收到的 WebSocket 消息。
     * <p>
     * 消息格式:
     * {
     *   "type": "event",
     *   "payload": {
     *     "sessionKey": "...",
     *     "message": {
     *       "payload": { "text": "{\"tool_name\":\"...\",\"args\":{...},\"result\":\"...\"}" },
     *       "type": "tool.full"
     *     }
     *   },
     *   "event": "chat",
     *   "gatewayEventType": "tool.full"
     * }
     */
    private void handleMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);

            // 1. 检查是否为 tool.full 类型的事件
            String gatewayEventType = root.path("gatewayEventType").asText("");
            if (!"tool.full".equals(gatewayEventType)) {
                return;
            }

            // 2. 解析 text JSON 获取 tool_name 和 args
            JsonNode payload = root.path("payload");
            JsonNode msgNode = payload.path("message");
            JsonNode msgPayload = msgNode.path("payload");
            JsonNode textNode = msgPayload.path("text");
            if (!textNode.isTextual()) return;

            JsonNode textObj = objectMapper.readTree(textNode.asText());
            String toolName = textObj.path("tool_name").asText("");

            // 3. 仅处理 getStationInfo 工具调用
            if (!"mcp__iot_mcp__openStationPage".equals(toolName)) {
                return;
            }

            // 4. 提取 areaCode
            JsonNode args = textObj.path("args");
            String areaCode = args.path("areaCode").asText("");
            if (areaCode.isEmpty()) {
                log.warn("getStationInfo 工具调用未包含 areaCode 参数");
                return;
            }

            log.info("收到 getStationInfo 工具调用, areaCode={}", areaCode);

            // 5. 通过 MCPXClient 查询客流区域信息
            AjaxResultVO result = mcpXClient.getPassengerAreas(areaCode);
            if (result == null || !"200".equals(result.getCode())) {
                log.warn("查询客流区域信息失败: areaCode={}, result={}", areaCode, result);
                return;
            }

            // 6. 提取响应数据中的关键字段
            List<HashMap<String, Object>> dataList = result.getData();
            if (dataList == null || dataList.isEmpty()) {
                log.warn("客流区域信息为空: areaCode={}", areaCode);
                return;
            }
            HashMap<String, Object> areaInfo = dataList.get(0);

            // 7. 封装 useData 事件 payload
            Map<String, Object> useDataPayload = new HashMap<>();
            useDataPayload.put("data_id", areaInfo.get("id"));
            useDataPayload.put("subarea_cid", "");
            useDataPayload.put("type", "客流区域");

            // 提取部分字段作为嵌套对象
            Map<String, Object> areaDetail = new HashMap<>();
            areaDetail.put("name", areaInfo.get("name"));
            areaDetail.put("channelCode", areaInfo.get("channelCode"));
            areaDetail.put("code", areaInfo.get("code"));
            areaDetail.put("flvAddr", areaInfo.get("flvAddr"));
            areaDetail.put("wsAddr", areaInfo.get("wsAddr"));
            useDataPayload.put("areaInfo", areaDetail);

            // 8. 推送给当前活跃的 SSE 连接
            boolean pushed = streamChatV2Service.pushUseDataToActiveStream(useDataPayload);
            if (pushed) {
                log.info("useData 事件已推送, areaCode={}, areaName={}", areaCode, areaInfo.get("name"));
            } else {
                log.debug("无活跃 SSE 连接，useData 事件未推送: areaCode={}", areaCode);
            }

        } catch (Exception e) {
            log.warn("处理 Gateway WebSocket 消息失败: {}", e.getMessage());
        }
    }
}
