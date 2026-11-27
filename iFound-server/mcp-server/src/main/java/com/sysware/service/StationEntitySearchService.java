package com.sysware.service;

import com.sysware.swcloud.feign.EntityCenterClient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MCP 内部复用的站房实体检索服务。
 * 这里只负责自然语言实体候选定位，不向 Agent 暴露额外的 MCP Tool。
 */
@Service
@RequiredArgsConstructor
public class StationEntitySearchService {

    private final EntityCenterClient entityCenterClient;

    public SearchResult searchIds(String prompt, String expectedEntityType) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("检索内容不能为空");
        }
        Map<String, Object> response = entityCenterClient.searchAssistant(Map.of("prompt", prompt.trim()));
        if (response == null) {
            throw new IllegalStateException("实体检索服务未返回结果，请稍后重试");
        }

        String status = stringValue(response.get("status"));
        String code = stringValue(response.get("code"));
        if ("SERVICE_UNAVAILABLE".equalsIgnoreCase(status)
                || "ERROR".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || (code != null && !code.isBlank() && !("200".equals(code) || "0".equals(code)))) {
            Object message = response.get("message") != null ? response.get("message") : response.get("msg");
            throw new IllegalStateException(message == null ? "实体检索服务暂不可用，请稍后重试" : String.valueOf(message));
        }

        Set<String> ids = new LinkedHashSet<>();
        collectIds(response, expectedEntityType.toUpperCase(Locale.ROOT), null, ids);
        return new SearchResult(status == null ? "OK" : status, new ArrayList<>(ids), response);
    }

    private void collectIds(Object node, String expectedType, String inheritedType, Set<String> ids) {
        if (node instanceof Collection<?> collection) {
            for (Object item : collection) {
                collectIds(item, expectedType, inheritedType, ids);
            }
            return;
        }
        if (!(node instanceof Map<?, ?> map)) {
            return;
        }

        String currentType = firstNonBlank(
                value(map, "entityType", "entity_type", "type", "category"),
                inheritedType
        );

        if (currentType != null && expectedType.equalsIgnoreCase(currentType)) {
            String id = value(map, "id", "entityId", "entity_id", "businessId", "business_id", "deviceId", "cameraId", "device_id", "camera_id");
            if (id != null && !id.isBlank()) {
                ids.add(id.trim());
            }
        }

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object child = entry.getValue();
            String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
            String childType = isEntityTypeKey(key) ? key : currentType;
            if (child instanceof Map<?, ?> || child instanceof Collection<?>) {
                collectIds(child, expectedType, childType, ids);
            }
        }
    }

    private boolean isEntityTypeKey(String key) {
        return "DEVICE".equalsIgnoreCase(key)
                || "CAMERA".equalsIgnoreCase(key)
                || "AREA".equalsIgnoreCase(key)
                || "SYSTEM".equalsIgnoreCase(key)
                || "DEVICE_TYPE".equalsIgnoreCase(key);
    }

    private String value(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @Getter
    @AllArgsConstructor
    public static class SearchResult {
        private final String status;
        private final List<String> ids;
        private final Map<String, Object> rawResponse;
    }
}
