package com.sysware.xian.module.entity.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.module.entity.common.EntityType;
import com.sysware.xian.module.entity.common.EntityTextNormalizer;
import com.sysware.xian.module.entity.init.alias.BizEntityAlias;
import com.sysware.xian.module.entity.init.alias.BizEntityAliasService;
import com.sysware.xian.module.entity.repository.EntityIndexRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 实体目录查询：统计、分页检索、详情和 ES 拼音分析。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "enabled", havingValue = "true")
public class EntityCatalogService {
    private final EntityIndexRepository repository;
    private final BizEntityAliasService aliasService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> summary() {
        List<Map<String, Object>> items = new ArrayList<>();
        long total = 0L;
        for (EntityType type : EntityType.values()) {
            long count = repository.countEnabled(type.getIndexAlias());
            total += count;
            items.add(Map.of("entityType", type.name(), "indexAlias", type.getIndexAlias(),
                    "label", label(type), "count", count));
        }
        return Map.of("total", total, "items", items);
    }

    public Map<String, Object> list(EntityType type, String keyword, String areaId,
                                    String systemKey, String deviceTypeId, Integer page, Integer size) {
        int pageSize = size == null ? 20 : Math.min(100, Math.max(1, size));
        int maxPage = Math.max(1, 10_000 / pageSize);
        int currentPage = page == null || page < 1 ? 1 : Math.min(page, maxPage);
        int from = (currentPage - 1) * pageSize;
        String nameField = nameField(type);
        List<Map<String, Object>> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("enabled", true)));
        if (type == EntityType.DEVICE) {
            if (hasText(areaId)) filters.add(Map.of("prefix", Map.of("subarea_cid", areaId.trim())));
            if (hasText(systemKey)) filters.add(Map.of("term", Map.of("system_key", systemKey.trim())));
            if (hasText(deviceTypeId)) filters.add(Map.of("term", Map.of("device_type_id", deviceTypeId.trim())));
        }

        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("filter", filters);
        if (hasText(keyword)) {
            String q = keyword.trim();
            List<Map<String, Object>> should = new ArrayList<>();
            should.add(Map.of("term", Map.of(type.getBusinessIdField(), Map.of("value", q, "boost", 100))));
            List<String> aliasIds = aliasService.findEnabledDataIds(type, EntityTextNormalizer.normalize(q));
            if (!aliasIds.isEmpty()) {
                should.add(Map.of("terms", Map.of(type.getBusinessIdField(), aliasIds, "boost", 120)));
            }
            for (String exactField : exactFields(type)) {
                should.add(Map.of("term", Map.of(exactField, Map.of("value", q, "boost", 90))));
            }
            List<String> searchFields = new ArrayList<>(List.of(
                    nameField + "^5", "aliases^4", nameField + ".pinyin^3", "aliases.pinyin^2"));
            if (type == EntityType.DEVICE) {
                searchFields.add("device_alias^5");
                searchFields.add("device_code^7");
            } else if (type == EntityType.CAMERA) {
                searchFields.add("camera_no^7");
                searchFields.add("address^2");
            }
            should.add(Map.of("multi_match", Map.of("query", q, "fields", searchFields,
                    "type", "best_fields", "fuzziness", "AUTO")));
            bool.put("should", should);
            bool.put("minimum_should_match", 1);
        }

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("from", from);
        query.put("size", pageSize);
        query.put("track_total_hits", true);
        query.put("query", Map.of("bool", bool));
        query.put("sort", hasText(keyword)
                ? List.of(Map.of("_score", Map.of("order", "desc")))
                : List.of(Map.of(nameField + ".raw", Map.of("order", "asc", "unmapped_type", "keyword"))));

        JsonNode response = repository.search(type.getIndexAlias(), query);
        JsonNode hitsNode = response == null ? null : response.path("hits").path("hits");
        long total = response == null ? 0L : response.path("hits").path("total").path("value").asLong(0L);
        List<Map<String, Object>> records = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        if (hitsNode != null && hitsNode.isArray()) {
            for (JsonNode hit : hitsNode) {
                String id = hit.path("_id").asText("");
                if (!id.isBlank()) ids.add(id);
                Map<String, Object> source = objectMapper.convertValue(hit.path("_source"),
                        objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
                source.put("entityId", id);
                source.put("name", resolveName(type, hit.path("_source")));
                source.put("score", hit.path("_score").asDouble(0D));
                records.add(source);
            }
        }
        Map<String, List<String>> aliasesById = aliasService.listEnabled(type, ids).stream()
                .collect(Collectors.groupingBy(BizEntityAlias::getDataId,
                        Collectors.mapping(BizEntityAlias::getAliasName, Collectors.toList())));
        for (Map<String, Object> record : records) {
            record.put("aliases", aliasesById.getOrDefault(String.valueOf(record.get("entityId")), List.of()));
        }
        return Map.of("entityType", type.name(), "page", currentPage, "size", pageSize,
                "total", total, "pages", total == 0 ? 0 : (total + pageSize - 1) / pageSize,
                "records", records);
    }

    public Map<String, Object> detail(EntityType type, String id) {
        JsonNode response = repository.getDocument(type.getIndexAlias(), id);
        if (response == null || !response.path("found").asBoolean(false)) {
            throw new IllegalArgumentException("未找到实体：" + id);
        }
        JsonNode sourceNode = response.path("_source");
        Map<String, Object> source = objectMapper.convertValue(sourceNode,
                objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        List<BizEntityAlias> aliases = aliasService.listEnabled(type, List.of(id));
        String name = resolveName(type, sourceNode);
        List<String> pinyinTokens = new ArrayList<>();
        boolean pinyinAvailable = false;
        if (hasText(name)) {
            try {
                JsonNode analyzed = repository.analyze(type.getIndexAlias(), nameField(type) + ".pinyin", name);
                JsonNode tokens = analyzed == null ? null : analyzed.path("tokens");
                if (tokens != null && tokens.isArray()) {
                    tokens.forEach(token -> pinyinTokens.add(token.path("token").asText()));
                    pinyinAvailable = true;
                }
            } catch (RuntimeException ignored) {
                // 详情不因拼音分析接口暂不可用而失败；保留 ES 原始模型与别名。
            }
        }
        return Map.of("entityType", type.name(), "entityId", id, "name", name,
                "source", source, "aliases", aliases, "pinyinTokens", pinyinTokens,
                "pinyinAvailable", pinyinAvailable);
    }

    public String label(EntityType type) {
        return switch (type) {
            case AREA -> "区域";
            case SYSTEM -> "系统";
            case DEVICE_TYPE -> "设备分类";
            case DEVICE -> "设备";
            case CAMERA -> "摄像头";
        };
    }

    private List<String> exactFields(EntityType type) {
        return switch (type) {
            case AREA -> List.of("cascade_cid");
            case SYSTEM -> List.of("element_key");
            case DEVICE_TYPE -> List.of("id", "cascade_id");
            case DEVICE -> List.of("id", "device_code", "device_alias.raw");
            case CAMERA -> List.of("id", "camera_no");
        };
    }

    private String nameField(EntityType type) {
        return switch (type) {
            case AREA -> "area_name";
            case SYSTEM -> "element_value";
            case DEVICE_TYPE -> "name";
            case DEVICE -> "device_name";
            case CAMERA -> "camera_name";
        };
    }

    private String resolveName(EntityType type, JsonNode source) {
        return source.path(nameField(type)).asText("");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
