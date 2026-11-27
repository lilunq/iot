package com.sysware.swcloud.feign;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EntityCenterClientFallback implements EntityCenterClient {
    @Override
    public Map<String, Object> resolve(Map<String, Object> request) {
        return unavailable("统一业务实体解析中心暂不可用，请稍后重试");
    }

    @Override
    public Map<String, Object> searchAssistant(Map<String, Object> request) {
        return unavailable("实体检索服务暂不可用，请稍后重试");
    }

    private Map<String, Object> unavailable(String message) {
        return Map.of(
                "status", "SERVICE_UNAVAILABLE",
                "message", message,
                "candidates", java.util.List.of()
        );
    }
}
