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

    /**
     * 返回当前启用的真实实体词典，供自然语言解析使用。
     * 词典中的 ID 和名称直接来自 ES，不由 LLM 猜测。
     */
    public List<Map<String, Object>> vocabulary(EntityType type, int requestedLimit) {
        if (type != EntityType.AREA && type != EntityType.SYSTEM && type != EntityType.DEVICE_TYPE) {
            throw new IllegalArgumentException("不支持为该实体类型生成解析词典: " + type);
        }
        int limit = Math.min(5000, Math.max(1, requestedLimit));
        String nameField = nameField(type);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("size", limit);
        query.put("track_total_hits", false);
        query.put("_source", List.of(type.getBusinessIdField(), nameField, "aliases"));
        query.put("query", Map.of("term", Map.of("enabled", true)));
        query.put("sort", List.of(Map.of(nameField + ".raw", Map.of(
                "order", "asc", "unmapped_type", "keyword"))));

        JsonNode response = repository.search(type.getIndexAlias(), query);
        JsonNode hits = response == null ? null : response.path("hits").path("hits");
        List<Map<String, Object>> result = new ArrayList<>();
        if (hits == null || !hits.isArray()) return result;
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            String id = source.path(type.getBusinessIdField()).asText(hit.path("_id").asText(""));
            String name = source.path(nameField).asText("").trim();
            if (id.isBlank() || name.isBlank()) continue;
            List<String> aliases = new ArrayList<>();
            JsonNode aliasNode = source.path("aliases");
            if (aliasNode.isArray()) {
                aliasNode.forEach(alias -> {
                    String value = alias.asText("").trim();
                    if (!value.isBlank()) aliases.add(value);
                });
            } else if (aliasNode.isTextual() && !aliasNode.asText().isBlank()) {
                aliases.add(aliasNode.asText().trim());
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("name", name);
            item.put("aliases", aliases);
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> list(EntityType type, String keyword, String areaId,
                                    String systemKey, String deviceTypeId, Integer page, Integer size) {
        return listInternal(type, keyword, areaId, systemKey, deviceTypeId, null, List.of(), page, size);
    }

    /**
     * 自然语言设备检索：分类名称仅作为相关性偏好，不作为硬过滤条件。
     * 这样源设备尚未维护 device_type_id 时，仍可通过设备名称被召回。
     */
    public Map<String, Object> listForAssistant(EntityType type, String keyword, String areaId,
                                                String systemKey, String deviceTypeId,
                                                String preferredDeviceTypeName,
                                                List<String> additionalKeywords,
                                                Integer page, Integer size) {
        return listInternal(type, keyword, areaId, systemKey, deviceTypeId,
                preferredDeviceTypeName, additionalKeywords, page, size);
    }

    private Map<String, Object> listInternal(EntityType type, String keyword, String areaId,
                                             String systemKey, String deviceTypeId,
                                             String preferredDeviceTypeName,
                                             List<String> additionalKeywords,
                                             Integer page, Integer size) {
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
        List<Map<String, Object>> preferenceClauses = new ArrayList<>();
        if (type == EntityType.DEVICE && hasText(preferredDeviceTypeName)) {
            // 放在外层 should 中，仅对已由关键词命中的结果加分，不允许分类偏好单独召回设备。
            preferenceClauses.add(Map.of("match_phrase", Map.of("device_type_name", Map.of(
                    "query", preferredDeviceTypeName.trim(), "boost", 8))));
            preferenceClauses.add(Map.of("match", Map.of("device_type_name", Map.of(
                    "query", preferredDeviceTypeName.trim(), "boost", 3))));
        }
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

            // 设备名称优先于其他文本字段；对“岛式空调1”这类带序号的描述，
            // 同时检索名称主体“岛式空调”，以召回“候车层1A岛式空调”等命名方式不同的设备。
            if (type == EntityType.DEVICE) {
                should.add(Map.of("match", Map.of(nameField, Map.of(
                        "query", q, "boost", 18))));
                String nameCore = removeTrailingDeviceNumber(q);
                if (!nameCore.equals(q)) {
                    should.add(Map.of("match_phrase", Map.of(nameField, Map.of(
                            "query", nameCore, "boost", 30))));
                    should.add(Map.of("match", Map.of(nameField, Map.of(
                            "query", nameCore, "boost", 20))));
                }
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
                    "type", "best_fields", "fuzziness", "AUTO", "boost", type == EntityType.DEVICE ? 1 : 2)));

            // 辅助关键词用于补偿口语表达与设备台账命名差异，权重低于主关键词。
            if (additionalKeywords != null) {
                for (String alternative : additionalKeywords) {
                    if (!hasText(alternative) || alternative.trim().equalsIgnoreCase(q)) continue;
                    should.add(Map.of("match", Map.of(nameField, Map.of(
                            "query", alternative.trim(), "boost", 6))));
                    should.add(Map.of("match", Map.of("aliases", Map.of(
                            "query", alternative.trim(), "boost", 4))));
                    if (type == EntityType.DEVICE) {
                        should.add(Map.of("match", Map.of("device_alias", Map.of(
                                "query", alternative.trim(), "boost", 3))));
                    }
                }
            }
            // 关键词必须至少命中一项；分类偏好位于外层可选 should 中，不能独立扩大召回集合。
            bool.put("must", List.of(Map.of("bool", Map.of(
                    "should", should, "minimum_should_match", 1))));
            if (!preferenceClauses.isEmpty()) {
                bool.put("should", preferenceClauses);
            }
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityType", type.name());
        result.put("entityId", id);
        result.put("name", name);
        result.put("source", source);
        result.put("aliases", aliases);
        result.put("pinyinTokens", pinyinTokens);
        result.put("pinyinAvailable", pinyinAvailable);
        // 设备详情同时展开系统、区域和分类关联，避免只向用户暴露外键 ID。
        if (type == EntityType.DEVICE) {
            List<Map<String, Object>> relations = new ArrayList<>();
            addRelatedEntity(relations, EntityType.SYSTEM, textValue(sourceNode, "system_key"), "element_value");
            addRelatedEntity(relations, EntityType.AREA, textValue(sourceNode, "subarea_cid"), "area_name");
            addRelatedEntity(relations, EntityType.DEVICE_TYPE, textValue(sourceNode, "device_type_id"), "name");
            result.put("relations", relations);
        }
        return result;
    }

    private void addRelatedEntity(List<Map<String, Object>> relations, EntityType type,
                                  String relatedId, String relatedNameField) {
        if (!hasText(relatedId)) return;
        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("entityType", type.name());
        relation.put("label", label(type));
        relation.put("entityId", relatedId);
        JsonNode response = repository.getDocument(type.getIndexAlias(), relatedId);
        boolean found = response != null && response.path("found").asBoolean(false);
        relation.put("found", found);
        String relatedName = found ? response.path("_source").path(relatedNameField).asText("") : "";
        relation.put("name", relatedName);
        relation.put("aliases", aliasService.listEnabled(type, List.of(relatedId)));
        relations.add(relation);
    }

    private String textValue(JsonNode source, String field) {
        String value = source.path(field).asText("");
        return value == null ? "" : value.trim();
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

    /**
     * 仅为中文设备名称追加一个去尾部编号的检索变体。
     * 原始关键词始终保留，避免丢失用户指定的编号信息。
     */
    private String removeTrailingDeviceNumber(String value) {
        if (!hasText(value) || !value.matches("(?s).*\\p{IsHan}.*")) return value;
        String core = value.replaceFirst("(?i)(?:[-_#\\s]*\\d{1,4}[a-z]?(?:号)?)$", "").trim();
        return core.length() >= 2 ? core : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
