package com.sysware.xian.module.entity.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.module.entity.common.EntityCandidate;
import com.sysware.xian.module.entity.common.EntityResolveRequest;
import com.sysware.xian.module.entity.common.EntityResolveResponse;
import com.sysware.xian.module.entity.common.EntityTextNormalizer;
import com.sysware.xian.module.entity.common.EntityType;
import com.sysware.xian.module.entity.config.EntityCenterProperties;
import com.sysware.xian.module.entity.init.alias.BizEntityAliasService;
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
    private final BizEntityAliasService aliasService;

    public EntityResolveResponse resolve(EntityResolveRequest request) {
        return resolveInternal(request, false);
    }

    /**
     * 为已由实体词典识别出的错字候选执行显式 Fuzzy 确认。
     * 该入口不改变任何业务过滤条件，仅确保即使常规分词有部分命中，也执行模糊校验。
     */
    public EntityResolveResponse resolveWithFuzzy(EntityResolveRequest request) {
        return resolveInternal(request, true);
    }

    private EntityResolveResponse resolveInternal(EntityResolveRequest request, boolean forceFuzzy) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        EntityType type = EntityType.from(request.getEntityType());
        // 区域实体不执行 Fuzzy，防止编辑距离相近但语义无关的区域被自动确认。
        if (type == EntityType.AREA) forceFuzzy = false;
        String query = request.getQuery().trim();
        String normalized = EntityTextNormalizer.normalize(query);

        List<String> aliasDataIds = aliasService.findEnabledDataIds(type, normalized);
        Map<String, Object> queryDsl = buildQuery(type, request, query, aliasDataIds, forceFuzzy, forceFuzzy);
        JsonNode result = repository.search(type.getIndexAlias(), queryDsl);
        boolean fuzzyFallbackUsed = forceFuzzy && hasHits(result);
        if (!forceFuzzy && type != EntityType.AREA && !hasHits(result) && shouldUseFuzzy(query)) {
            // 常规精确/分词/拼音/别名检索无结果后，再启用低优先级 Fuzzy 容错。
            queryDsl = buildQuery(type, request, query, aliasDataIds, true, false);
            result = repository.search(type.getIndexAlias(), queryDsl);
            fuzzyFallbackUsed = hasHits(result);
        }
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

        if (fuzzyFallbackUsed && !candidates.isEmpty()) {
            message = "常规检索未命中，已通过 Elasticsearch Fuzzy 单字符错别字容错召回候选；"
                    + ("FOUND".equals(status) ? "当前候选已通过唯一性判断。" : "候选仍有歧义或置信度不足，请进一步确认。");
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

    private Map<String, Object> buildQuery(EntityType type, EntityResolveRequest request, String query,
                                            List<String> aliasDataIds, boolean includeFuzzy, boolean fuzzyOnly) {
        List<Map<String, Object>> filters = new ArrayList<>();
        filters.add(term("enabled", true));

        // 规范中的区域 cascade_cid 和系统 element_key 过滤仅应用于设备。
        // 摄像头无区域归属，不解析、不过滤区域。
        if (type == EntityType.DEVICE) {
            addPrefixFilter(filters, "subarea_cid", request.getAreaId());
            addFilter(filters, "system_key", request.getSystemKey());
            addFilter(filters, "device_type_id", request.getDeviceTypeId());
        }

        List<Map<String, Object>> should = new ArrayList<>();
        if (!fuzzyOnly) {
            // 别名精确命中后回查稳定业务标识；设备过滤仍在 bool.filter 中生效。
            if (aliasDataIds != null && !aliasDataIds.isEmpty()) {
                should.add(Map.of("terms", Map.of(type.getBusinessIdField(), aliasDataIds, "boost", 150)));
            }
            if (type == EntityType.AREA) {
                exact(should, "cascade_cid", query, 120);
                match(should, "area_name", query, 12);
                match(should, "aliases", query, 10);
                pinyin(should, "area_name", query, 6);
                pinyin(should, "aliases", query, 5);
            } else if (type == EntityType.SYSTEM) {
                exact(should, "element_key", query, 120);
                match(should, "element_value", query, 12);
                match(should, "aliases", query, 10);
                pinyin(should, "element_value", query, 6);
                pinyin(should, "aliases", query, 5);
            } else if (type == EntityType.DEVICE_TYPE) {
                exact(should, "id", query, 125);
                exact(should, "cascade_id", query, 115);
                exact(should, "device_type", query, 110);
                exact(should, "device_alias", query, 108);
                match(should, "name", query, 12);
                match(should, "device_name", query, 12);
                match(should, "device_alias", query, 11);
                match(should, "location", query, 8);
                match(should, "device_name_variants", query, 8);
                match(should, "location_variants", query, 7);
                match(should, "aliases", query, 10);
                pinyin(should, "name", query, 6);
                pinyin(should, "device_name", query, 6);
                pinyin(should, "device_alias", query, 5);
                pinyin(should, "location", query, 5);
                pinyin(should, "device_name_variants", query, 5);
                pinyin(should, "location_variants", query, 5);
                pinyin(should, "aliases", query, 5);
            } else if (type == EntityType.DEVICE) {
                exact(should, "id", query, 130);
                exact(should, "device_code", query, 120);
                exact(should, "device_type", query, 115);
                exact(should, "system_type", query, 115);
                exact(should, "device_alias", query, 110);
                match(should, "device_name", query, 12);
                match(should, "device_alias", query, 11);
                match(should, "location", query, 8);
                match(should, "device_name_variants", query, 8);
                match(should, "location_variants", query, 7);
                match(should, "aliases", query, 10);
                pinyin(should, "device_name", query, 6);
                pinyin(should, "device_alias", query, 5);
                pinyin(should, "location", query, 5);
                pinyin(should, "device_name_variants", query, 5);
                pinyin(should, "location_variants", query, 5);
                pinyin(should, "aliases", query, 5);
            } else if (type == EntityType.CAMERA) {
                exact(should, "id", query, 130);
                exact(should, "camera_no", query, 120);
                match(should, "camera_name", query, 12);
                match(should, "address", query, 6);
                match(should, "aliases", query, 10);
                pinyin(should, "camera_name", query, 6);
                pinyin(should, "address", query, 4);
                pinyin(should, "aliases", query, 5);
            }
    
    
        }
        if (includeFuzzy) addFuzzyFallbackClauses(should, type, query);

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

    private boolean hasHits(JsonNode response) {
        JsonNode hits = response == null ? null : response.path("hits").path("hits");
        return hits != null && hits.isArray() && !hits.isEmpty();
    }

    private boolean shouldUseFuzzy(String value) {
        if (value == null || value.isBlank()) return false;
        int length = value.codePointCount(0, value.length());
        return length >= 3 && length <= 64 && !value.matches("\\d+");
    }

    /**
     * 只在常规检索无命中后增加 Fuzzy 查询，且所有条件仍保留在 bool.filter 中。
     * 因此拼写容错不会绕过用户明确指定的区域、系统或设备分类范围。
     */
    private void addFuzzyFallbackClauses(List<Map<String, Object>> clauses, EntityType type, String value) {
        String nameField = switch (type) {
            case AREA -> "area_name";
            case SYSTEM -> "element_value";
            case DEVICE_TYPE -> "name";
            case DEVICE -> "device_name";
            case CAMERA -> "camera_name";
        };
        addFuzzyMatch(clauses, nameField, value, 12);
        addFuzzyMatch(clauses, "aliases", value, 10);
        addFuzzyTerm(clauses, nameField + ".raw", value, 18);
        addFuzzyTerm(clauses, "aliases.raw", value, 14);
        if (type == EntityType.DEVICE || type == EntityType.DEVICE_TYPE) {
            addFuzzyMatch(clauses, "device_name", value, 10);
            addFuzzyMatch(clauses, "device_alias", value, 9);
            addFuzzyMatch(clauses, "location", value, 7);
            addFuzzyTerm(clauses, "device_name.raw", value, 12);
            addFuzzyTerm(clauses, "device_alias.raw", value, 11);
            addFuzzyTerm(clauses, "location.raw", value, 8);
            addFuzzyMatch(clauses, "device_name_variants", value, 10);
            addFuzzyMatch(clauses, "location_variants", value, 8);
            addFuzzyTerm(clauses, "device_name_variants.raw", value, 10);
            addFuzzyTerm(clauses, "location_variants.raw", value, 8);
            if (type == EntityType.DEVICE && value.matches("(?i).*[a-z].*\\d.*|.*\\d.*[a-z].*")
                    && value.length() <= 32) {
                addFuzzyTerm(clauses, "device_code", value, 2);
            }
        } else if (type == EntityType.CAMERA) {
            addFuzzyMatch(clauses, "address", value, 7);
            if (value.matches("(?i).*[a-z].*\\d.*|.*\\d.*[a-z].*") && value.length() <= 32) {
                addFuzzyTerm(clauses, "camera_no", value, 2);
            }
        }
    }

    private void addFuzzyMatch(List<Map<String, Object>> clauses, String field, String value, int boost) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("query", value);
        options.put("fuzziness", 1);
        options.put("prefix_length", 0);
        options.put("max_expansions", 25);
        options.put("boost", boost);
        clauses.add(Map.of("match", Map.of(field, options)));
    }

    private void addFuzzyTerm(List<Map<String, Object>> clauses, String field, String value, int boost) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("value", value);
        options.put("fuzziness", 1);
        options.put("prefix_length", 0);
        options.put("max_expansions", 25);
        options.put("transpositions", true);
        options.put("boost", boost);
        clauses.add(Map.of("fuzzy", Map.of(field, options)));
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

    /** 区域 cascade_cid 为层级编码，按前缀匹配区域及其下级区域。 */
    private void addPrefixFilter(List<Map<String, Object>> filters, String field, String value) {
        if (value != null && !value.isBlank()) {
            filters.add(Map.of("prefix", Map.of(field, value.trim())));
        }
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
        if (type == EntityType.AREA) return source.path("area_name").asText("");
        if (type == EntityType.SYSTEM) return source.path("element_value").asText("");
        if (type == EntityType.DEVICE_TYPE) return source.path("name").asText("");
        if (type == EntityType.DEVICE) return source.path("device_name").asText("");
        if (type == EntityType.CAMERA) return source.path("camera_name").asText("");
        return "";
    }
}
