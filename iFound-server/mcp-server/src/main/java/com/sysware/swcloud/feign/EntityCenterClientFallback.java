package com.sysware.swcloud.feign;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EntityCenterClientFallback implements EntityCenterClient {
    @Override
    public Map<String, Object> resolve(Map<String, Object> request) {
        return Map.of(
                "status", "SERVICE_UNAVAILABLE",
                "message", "统一业务实体解析中心暂不可用，请稍后重试",
                "candidates", java.util.List.of()
        );
    }
}
