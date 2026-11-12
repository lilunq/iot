package com.sysware.xian.module.entity.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.module.entity.common.EntityCandidate;
import com.sysware.xian.module.entity.common.EntityResolveRequest;
import com.sysware.xian.module.entity.common.EntityResolveResponse;
import com.sysware.xian.module.entity.common.EntityTextNormalizer;
import com.sysware.xian.module.entity.common.EntityType;
import com.sysware.xian.module.entity.config.EntityCenterProperties;
import com.sysware.xian.module.entity.repository.EntityIndexRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 第一阶段解析器：类型路由、明确过滤优先、精确标识优先、文本/拼音召回，
 * 并根据得分阈值和第一、第二候选差值决定是否自动消歧。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "enabled", havingValue = "true")
public class EntityResolverService {
    private final EntityIndexRepository repository;
    private final EntityCenterProperties properties;
    private final ObjectMapper objectMapper;

    public EntityResolveResponse resolve(EntityResolveRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        EntityType type = EntityType.from(request.getEntityType());
        String query = request.getQuery().trim();
        String normalized = EntityTextNormalizer.normalize(query);

        JsonNode result = repository.search(type.getIndexAlias(), buildQuery(type, request, query));
        List<EntityCandidate> candidates = toCandidates(type, result, requestLimit(request.getLimit()));

        String status;
        EntityCandidate resolved = null;
        String message;
        if (candidates.isEmpty()) {
            status = "NOT_FOUND";
            message = "没有找到匹配的实体";
        } else {
            EntityCandidate first = candidates.get(0);
            double minScore = properties.getResolver().getMinimumScore();
            double gap = candidates.size() > 1
                    ? first.getScore() - candidates.get(1).getScore()
                    : Double.POSITIVE_INFINITY;
            if (first.getScore() >= minScore && gap >= properties.getResolver().getMinimumScoreGap()) {
                status = "FOUND";
                resolved = first;
                message = "已找到高置信度实体";
            } else {
                status = "AMBIGUOUS";
                message = first.getScore() < minScore
                        ? "候选匹配置信度不足，请补充实体信息"
                        : "存在多个相近候选，请进一步确认";
            }
        }

        return EntityResolveResponse.builder()
                .status(status)
                .entityType(type.name())
                .query(query)
                .normalizedQuery(normalized)
                .resolved(resolved)
                .candidates(candidates)
                .message(message)
                .build();
    }

    private Map<String, Object> buildQuery(EntityType type, EntityResolveRequest request, String query) {
        List<Map<String, Object>> filters = new ArrayList<>();
        filters.add(term("enabled", true));

        // 规范中的区域 cascade_cid 和系统 element_key 过滤仅应用于设备。
        // 摄像头无区域归属，不解析、不过滤区域。
        if (type == EntityType.DEVICE) {
            addFilter(filters, "subarea_cid", request.getAreaId());
            addFilter(filters, "system_key", request.getSystemKey());
            addFilter(filters, "device_type_id", request.getDeviceTypeId());
        }

        List<Map<String, Object>> should = new ArrayList<>();
        switch (type) {
            case AREA -> {
                exact(should, "cascade_cid", query, 120);
                match(should, "area_name", query, 12);
                match(should, "aliases", query, 10);
                pinyin(should, "area_name", query, 6);
                pinyin(should, "aliases", query, 5);
            }
            case SYSTEM -> {
                exact(should, "element_key", query, 120);
                match(should, "element_value", query, 12);
                match(should, "aliases", query, 10);
                pinyin(should, "element_value", query, 6);
                pinyin(should, "aliases", query, 5);
            }
            case DEVICE_TYPE -> {
                exact(should, "id", query, 120);
                match(should, "name", query, 12);
                match(should, "aliases", query, 10);
                pinyin(should, "name", query, 6);
                pinyin(should, "aliases", query, 5);
            }
            case DEVICE -> {
                exact(should, "id", query, 130);
                exact(should, "device_code", query, 120);
                exact(should, "device_alias", query, 110);
                match(should, "device_name", query, 12);
                match(should, "device_alias", query, 11);
                match(should, "aliases", query, 10);
                pinyin(should, "device_name", query, 6);
                pinyin(should, "aliases", query, 5);
            }
            case CAMERA -> {
                exact(should, "id", query, 130);
                exact(should, "camera_no", query, 120);
                match(should, "camera_name", query, 12);
                match(should, "address", query, 6);
                match(should, "aliases", query, 10);
                pinyin(should, "camera_name", query, 6);
                pinyin(should, "aliases", query, 5);
            }
        }

        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("filter", filters);
        bool.put("should", should);
        bool.put("minimum_should_match", 1);
        return Map.of(
                "size", requestLimit(request.getLimit()),
                "track_total_hits", false,
                "query", Map.of("bool", bool)
        );
    }

    private int requestLimit(Integer requested) {
        EntityCenterProperties.Resolver resolver = properties.getResolver();
        int max = Math.max(1, resolver.getMaxLimit());
        if (requested == null || requested < 1) return Math.min(Math.max(1, resolver.getDefaultLimit()), max);
        return Math.min(requested, max);
    }

    private void exact(List<Map<String, Object>> queries, String field, String value, int boost) {
        queries.add(Map.of("term", Map.of(field, Map.of("value", value, "boost", boost))));
        queries.add(Map.of("term", Map.of(field + ".raw", Map.of("value", value, "boost", boost - 5))));
    }

    private void match(List<Map<String, Object>> queries, String field, String value, int boost) {
        queries.add(Map.of("match", Map.of(field, Map.of("query", value, "boost", boost))));
    }

    private void pinyin(List<Map<String, Object>> queries, String field, String value, int boost) {
        queries.add(Map.of("match", Map.of(field + ".pinyin", Map.of("query", value, "boost", boost))));
    }

    private Map<String, Object> term(String field, Object value) {
        return Map.of("term", Map.of(field, value));
    }

    private void addFilter(List<Map<String, Object>> filters, String field, String value) {
        if (value != null && !value.isBlank()) filters.add(term(field, value.trim()));
    }

    private List<EntityCandidate> toCandidates(EntityType type, JsonNode response, int limit) {
        List<EntityCandidate> result = new ArrayList<>();
        JsonNode hits = response == null ? null : response.path("hits").path("hits");
        if (hits == null || !hits.isArray()) return result;
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            String id = hit.path("_id").asText("");
            Map<String, Object> data = objectMapper.convertValue(source,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
            result.add(EntityCandidate.builder()
                    .entityId(id)
                    .entityType(type.name())
                    .name(resolveName(type, source))
                    .score(hit.path("_score").asDouble(0.0))
                    .data(data)
                    .build());
            if (result.size() >= limit) break;
        }
        return result;
    }

    private String resolveName(EntityType type, JsonNode source) {
        return switch (type) {
            case AREA -> source.path("area_name").asText("");
            case SYSTEM -> source.path("element_value").asText("");
            case DEVICE_TYPE -> source.path("name").asText("");
            case DEVICE -> source.path("device_name").asText("");
            case CAMERA -> source.path("camera_name").asText("");
        };
    }
}
