package com.sysware.xian.module.entity.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.module.entity.catalog.EntityCatalogService;
import com.sysware.xian.module.entity.common.EntityType;
import com.sysware.xian.module.entity.common.EntityResolveRequest;
import com.sysware.xian.module.entity.common.EntityResolveResponse;
import com.sysware.xian.module.entity.resolver.EntityResolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** LLM 只做自然语言意图结构化；实体召回和候选数据全部由 Elasticsearch 提供。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "enabled", havingValue = "true")
public class EntityAssistantService {
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ObjectMapper objectMapper;
    private final EntityCatalogService catalogService;
    private final EntityResolverService resolverService;

    public Map<String, Object> search(String prompt) {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("请输入自然语言检索需求");
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) throw new IllegalStateException("当前服务未配置可用的 Spring AI ChatClient");
        String raw = builder.build().prompt()
                .system("""
                        你是西安东站统一业务实体解析中心的查询意图解析器。
                        只负责把用户自然语言转换成检索条件，不负责查数据，不得编造实体、实体ID、区域编码、系统编码或设备分类ID。
                        抽取 entityType、keyword，以及设备查询中用户明确提到的 areaName、systemName、deviceTypeName。
                        摄像头没有区域归属，摄像头查询不得提取或使用区域、系统、设备分类过滤。
                        仅当用户原文明确给出稳定编码时才填写 areaId、systemKey、deviceTypeId；不得根据中文名称编造编码。
                        entityType 只能是 AREA、SYSTEM、DEVICE_TYPE、DEVICE、CAMERA。
                        keyword 只提取目标实体名称、编号或别名，不要把区域/系统/分类限定词混入设备名称。
                        只输出一个 JSON 对象，不要 Markdown，不要解释性前后缀。
                        JSON字段：entityType、keyword、areaName、systemName、deviceTypeName、areaId、systemKey、deviceTypeId、intent。
                        """)
                .user(prompt.trim())
                .call()
                .content();
        if (raw == null || raw.isBlank()) throw new IllegalStateException("LLM 未返回结构化检索条件");
        String json = extractJson(raw);
        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("LLM 返回内容不是有效 JSON：" + raw, ex);
        }
        EntityType type = EntityType.from(parsed.path("entityType").asText("DEVICE"));
        String keyword = parsed.path("keyword").asText("").trim();
        if (keyword.isBlank()) keyword = prompt.trim();
        String areaId = explicitValue(parsed, "areaId", prompt);
        String systemKey = explicitValue(parsed, "systemKey", prompt);
        String deviceTypeId = explicitValue(parsed, "deviceTypeId", prompt);
        String areaName = extractedText(parsed, "areaName");
        String systemName = extractedText(parsed, "systemName");
        String deviceTypeName = extractedText(parsed, "deviceTypeName");
        if (type != EntityType.DEVICE) {
            // 摄像头等非设备实体不接受区域、系统或设备分类过滤。
            areaId = null;
            systemKey = null;
            deviceTypeId = null;
            areaName = null;
            systemName = null;
            deviceTypeName = null;
        }
        Map<String, Object> filterResolutions = new LinkedHashMap<>();
        boolean filterNeedsClarification = false;
        if (type == EntityType.DEVICE) {
            if (areaId == null && areaName != null) {
                EntityResolveResponse resolved = resolveFilter(EntityType.AREA, areaName);
                filterResolutions.put("area", filterResolution(resolved));
                if ("FOUND".equals(resolved.getStatus())) areaId = resolved.getResolved().getEntityId();
                else filterNeedsClarification = true;
            }
            if (systemKey == null && systemName != null) {
                EntityResolveResponse resolved = resolveFilter(EntityType.SYSTEM, systemName);
                filterResolutions.put("system", filterResolution(resolved));
                if ("FOUND".equals(resolved.getStatus())) systemKey = resolved.getResolved().getEntityId();
                else filterNeedsClarification = true;
            }
            if (deviceTypeId == null && deviceTypeName != null) {
                EntityResolveResponse resolved = resolveFilter(EntityType.DEVICE_TYPE, deviceTypeName);
                filterResolutions.put("deviceType", filterResolution(resolved));
                if ("FOUND".equals(resolved.getStatus())) deviceTypeId = resolved.getResolved().getEntityId();
                else filterNeedsClarification = true;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prompt", prompt);
        result.put("llmRaw", raw);
        Map<String, Object> parsedView = new LinkedHashMap<>();
        parsedView.put("entityType", type.name());
        parsedView.put("entityLabel", catalogService.label(type));
        parsedView.put("keyword", keyword);
        parsedView.put("areaName", nullSafe(areaName));
        parsedView.put("systemName", nullSafe(systemName));
        parsedView.put("deviceTypeName", nullSafe(deviceTypeName));
        parsedView.put("areaId", nullSafe(areaId));
        parsedView.put("systemKey", nullSafe(systemKey));
        parsedView.put("deviceTypeId", nullSafe(deviceTypeId));
        parsedView.put("intent", parsed.path("intent").asText("实体检索"));
        result.put("parsed", parsedView);
        result.put("filterResolutions", filterResolutions);
        result.put("status", filterNeedsClarification ? "NEEDS_CLARIFICATION" : "SEARCHED");
        result.put("searchResult", filterNeedsClarification
                ? Map.of("entityType", type.name(), "page", 1, "size", 20, "total", 0, "pages", 0, "records", java.util.List.of())
                : catalogService.list(type, keyword, areaId, systemKey, deviceTypeId, 1, 20));
        return result;
    }

    private EntityResolveResponse resolveFilter(EntityType type, String name) {
        EntityResolveRequest request = new EntityResolveRequest();
        request.setEntityType(type.name());
        request.setQuery(name);
        request.setLimit(5);
        return resolverService.resolve(request);
    }

    private Map<String, Object> filterResolution(EntityResolveResponse response) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", response.getStatus());
        result.put("message", response.getMessage());
        result.put("candidates", response.getCandidates());
        return result;
    }

    private String extractedText(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        return value.isBlank() ? null : value;
    }

    private String explicitValue(JsonNode node, String field, String userPrompt) {
        String value = node.path(field).asText("").trim();
        // 过滤模型臆造的稳定标识：只有用户原文明确出现的编码才允许成为硬过滤条件。
        if (value.isBlank() || !userPrompt.contains(value)) return null;
        // 区域/系统编码为级联编码；分类主键为雪花 ID，避免把“2号”等普通序号误当成稳定 ID。
        if ("areaId".equals(field) || "systemKey".equals(field)) {
            return value.matches("\\d+(?:\\.\\d+)+(?:-\\d+(?:\\.\\d+)*)?") ? value : null;
        }
        if ("deviceTypeId".equals(field)) {
            return value.matches("\\d{10,}") ? value : null;
        }
        return null;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String extractJson(String raw) {
        String value = raw.trim();
        String fence = String.valueOf((char) 96).repeat(3);
        if (value.startsWith(fence)) {
            value = value.replaceFirst("^" + fence + "(?:json)?\\s*", "");
            value = value.replaceFirst("\\s*" + fence + "$", "");
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end < start) throw new IllegalStateException("LLM 返回中未找到 JSON 对象：" + raw);
        return value.substring(start, end + 1);
    }
}
