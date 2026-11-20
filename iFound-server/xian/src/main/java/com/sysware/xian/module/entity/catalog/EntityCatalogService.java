package com.sysware.xian.module.entity.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.module.entity.common.EntityType;
import com.sysware.xian.module.entity.common.EntityTextNormalizer;
import com.sysware.xian.module.entity.common.EntityAreaNameNormalizer;
import com.sysware.xian.module.entity.init.alias.BizEntityAlias;
import com.sysware.xian.module.entity.init.alias.BizEntityAliasService;
import com.sysware.xian.module.entity.repository.EntityIndexRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        // 业务别名可能是在上次 ES 同步之后新增的，额外从别名表实时合并，保证词典及时生效。
        List<String> ids = result.stream().map(item -> String.valueOf(item.get("id"))).toList();
        Map<String, List<String>> databaseAliases = aliasService.listEnabled(type, ids).stream()
                .collect(Collectors.groupingBy(BizEntityAlias::getDataId,
                        Collectors.mapping(BizEntityAlias::getAliasName, Collectors.toList())));
        for (Map<String, Object> item : result) {
            LinkedHashSet<String> merged = new LinkedHashSet<>();
            Object sourceAliases = item.get("aliases");
            if (sourceAliases instanceof List<?> values) {
                values.forEach(value -> {
                    if (value != null && !value.toString().isBlank()) merged.add(value.toString().trim());
                });
            }
            merged.addAll(databaseAliases.getOrDefault(String.valueOf(item.get("id")), List.of()));
            item.put("aliases", new ArrayList<>(merged));
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

    /**
     * 对区域台账中带阿拉伯数字的名称/别名，使用 ES Pinyin Analyzer 比较中文读法。
     * 例如输入“武站台”，与“5站台”生成的中文读法候选“五站台”拼音一致时，返回真实区域候选。
     * Java 仅做数字转汉字，不生成拼音；拼音 token 完全来自 ES。
     */
    public List<Map<String, Object>> findPinyinAreaCandidates(
            String query, List<Map<String, Object>> vocabulary) {
        if (!hasText(query) || vocabulary == null || vocabulary.isEmpty()) return List.of();
        String areaUnit = trailingAreaUnit(query);
        // 没有可识别的位置单位时，不扫描整本区域词典，避免泛化拼音比较。
        if (!hasText(areaUnit)) return List.of();
        String querySignature = pinyinSignature(query);
        if (!hasText(querySignature)) return List.of();
        int queryLength = query.codePointCount(0, query.length());

        Map<String, String> analyzedVariants = new LinkedHashMap<>();
        Map<String, Map<String, Object>> matchedById = new LinkedHashMap<>();
        for (Map<String, Object> item : vocabulary) {
            String id = String.valueOf(item.getOrDefault("id", ""));
            if (!hasText(id)) continue;
            for (String term : areaVocabularyTerms(item)) {
                if (!term.endsWith(areaUnit)) continue;
                int termLength = term.codePointCount(0, term.length());
                if (Math.abs(termLength - queryLength) > 2) continue;

                // 同时比较原始名称和阿拉伯数字对应的中文读法，如“5站台”与“五站台”。
                Set<String> variants = new LinkedHashSet<>();
                variants.add(term);
                String chineseVariant = EntityAreaNameNormalizer.toChineseNumeralVariant(term);
                if (hasText(chineseVariant)) {
                    variants.add(chineseVariant);
                    variants.add(chineseVariant.replaceFirst("^第", ""));
                }
                for (String variant : variants) {
                    if (!hasText(variant)
                            || Math.abs(variant.codePointCount(0, variant.length()) - queryLength) > 2) continue;
                    String signature = analyzedVariants.computeIfAbsent(variant, this::pinyinSignature);
                    if (hasText(signature) && querySignature.equals(signature)) {
                        matchedById.putIfAbsent(id, item);
                        break;
                    }
                }
                if (matchedById.containsKey(id)) break;
            }
        }
        return new ArrayList<>(matchedById.values());
    }

    private List<String> areaVocabularyTerms(Map<String, Object> item) {
        List<String> terms = new ArrayList<>();
        String name = String.valueOf(item.getOrDefault("name", "")).trim();
        if (hasText(name)) terms.add(name);
        Object aliases = item.get("aliases");
        if (aliases instanceof List<?> values) {
            for (Object value : values) {
                if (value != null && hasText(value.toString())) terms.add(value.toString().trim());
            }
        }
        return terms;
    }

    private String trailingAreaUnit(String value) {
        if (!hasText(value)) return "";
        for (String unit : List.of("候车层", "站台", "站厅", "站", "台", "层", "楼", "区")) {
            if (value.endsWith(unit)) return unit;
        }
        return "";
    }

    /** 拼音 token 由 Elasticsearch Pinyin Analyzer 生成；忽略保留的原始中文 token。 */
    private String pinyinSignature(String value) {
        try {
            JsonNode analyzed = repository.analyze(EntityType.AREA.getIndexAlias(), "area_name.pinyin", value);
            JsonNode tokens = analyzed == null ? null : analyzed.path("tokens");
            if (tokens == null || !tokens.isArray()) return "";
            return java.util.stream.StreamSupport.stream(tokens.spliterator(), false)
                    .map(token -> token.path("token").asText("").toLowerCase(java.util.Locale.ROOT))
                    .filter(token -> token.matches("[a-z]+"))
                    .distinct()
                    .collect(Collectors.joining(" "));
        } catch (RuntimeException ignored) {
            // 拼音核验属于增强能力；ES 分析接口不可用时不影响普通区域解析。
            return "";
        }
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
        List<Map<String, Object>> keywordShould = new ArrayList<>();
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
            List<Map<String, Object>> should = keywordShould;
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

            List<String> searchFields = new ArrayList<>(List.of(nameField + "^5", "aliases^4"));
            if (type == EntityType.DEVICE) {
                searchFields.add("device_alias^5");
                searchFields.add("device_code^7");
                searchFields.add("location^3");
            } else if (type == EntityType.DEVICE_TYPE) {
                searchFields.add("device_name^5");
                searchFields.add("device_alias^4");
                searchFields.add("location^3");
                searchFields.add("system_type^2");
                searchFields.add("device_type^2");
            } else if (type == EntityType.CAMERA) {
                searchFields.add("camera_no^7");
                searchFields.add("address^2");
            }
            // 常规阶段只做精确/IK 召回；拼音和 Fuzzy 分别在后续阶段执行，便于控制优先级与解释命中通道。
            should.add(Map.of("multi_match", Map.of("query", q, "fields", searchFields,
                    "type", "best_fields", "boost", type == EntityType.DEVICE ? 1 : 2)));

            // 辅助关键词用于补偿口语表达与设备台账命名差异，权重低于主关键词。
            if (additionalKeywords != null) {
                for (String alternative : additionalKeywords) {
                    if (!hasText(alternative) || alternative.trim().equalsIgnoreCase(q)) continue;
                    int alternativeBoost = Math.min(18, Math.max(7, alternative.trim().length() * 2));
                    if (type == EntityType.DEVICE) {
                        should.add(Map.of("match_phrase", Map.of(nameField, Map.of(
                                "query", alternative.trim(), "boost", alternativeBoost + 3))));
                    }
                    should.add(Map.of("match", Map.of(nameField, Map.of(
                            "query", alternative.trim(), "boost", alternativeBoost))));
                    should.add(Map.of("match", Map.of("aliases", Map.of(
                            "query", alternative.trim(), "boost", Math.max(4, alternativeBoost - 2)))));
                    if (type == EntityType.DEVICE) {
                        should.add(Map.of("match", Map.of("device_alias", Map.of(
                                "query", alternative.trim(), "boost", Math.max(3, alternativeBoost - 4)))));
                        should.add(Map.of("match", Map.of("location", Map.of(
                                "query", alternative.trim(), "boost", Math.max(3, alternativeBoost - 5)))));
                    } else if (type == EntityType.DEVICE_TYPE) {
                        should.add(Map.of("match", Map.of("device_name", Map.of(
                                "query", alternative.trim(), "boost", Math.max(4, alternativeBoost - 2)))));
                        should.add(Map.of("match", Map.of("device_alias", Map.of(
                                "query", alternative.trim(), "boost", Math.max(3, alternativeBoost - 4)))));
                        should.add(Map.of("match", Map.of("location", Map.of(
                                "query", alternative.trim(), "boost", Math.max(3, alternativeBoost - 5)))));
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

        // 位置描述可能与设备名称的一部分同时出现；把补充关键词的全拼作为低权重并行召回，
        // 避免“空调”等普通词已经命中时，主关键词的拼音纠错通道被提前跳过。
        addPinyinSupplementClauses(keywordShould, type, additionalKeywords);
        JsonNode response = repository.search(type.getIndexAlias(), query);
        boolean pinyinFallbackUsed = false;
        boolean fuzzyFallbackUsed = false;

        // 常规名称/别名/IK 未命中后，单独执行拼音全拼查询；不在 Java 中生成拼音。
        if (!hasHits(response) && hasText(keyword)) {
            addPinyinFallbackClauses(keywordShould, type, keyword.trim(), additionalKeywords);
            response = repository.search(type.getIndexAlias(), query);
            pinyinFallbackUsed = hasHits(response);
        }

        // 拼音全拼仍未命中时，才以较低权重启用 Fuzzy 单字符容错。
        if (!hasHits(response) && hasText(keyword) && shouldUseFuzzy(keyword)) {
            addFuzzyFallbackClauses(keywordShould, type, keyword.trim());
            response = repository.search(type.getIndexAlias(), query);
            fuzzyFallbackUsed = hasHits(response);
        }
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityType", type.name());
        result.put("page", currentPage);
        result.put("size", pageSize);
        result.put("total", total);
        result.put("pages", total == 0 ? 0 : (total + pageSize - 1) / pageSize);
        result.put("records", records);
        if (fuzzyFallbackUsed) {
            result.put("retrievalMode", "FUZZY_FALLBACK");
            result.put("retrievalMessage", "常规名称/别名及拼音全拼检索未命中，已通过 Elasticsearch Fuzzy 单字符容错召回");
        } else if (pinyinFallbackUsed) {
            result.put("retrievalMode", "PINYIN_FALLBACK");
            result.put("retrievalMessage", "常规中文名称/别名检索未命中，已通过 Elasticsearch Pinyin Analyzer 全拼召回候选");
        }
        return result;
    }

    /**
     * 对完整短语拆分出的辅助关键词并行执行低权重全拼召回。
     * 不能等主检索完全无结果才做，否则“空调”等泛词先命中会掩盖位置词的同音纠错。
     */
    private void addPinyinSupplementClauses(List<Map<String, Object>> clauses, EntityType type,
                                            List<String> additionalKeywords) {
        if (additionalKeywords == null || additionalKeywords.isEmpty()) return;
        String nameField = nameField(type);
        for (String alternative : additionalKeywords) {
            if (!hasText(alternative)) continue;
            String value = alternative.trim();
            if (value.length() < 2) continue;
            addPinyinMatch(clauses, nameField + ".pinyin", value, 4);
            addPinyinMatch(clauses, "aliases.pinyin", value, 3);
            if (type == EntityType.DEVICE || type == EntityType.DEVICE_TYPE) {
                addPinyinMatch(clauses, "device_name.pinyin", value, 4);
                addPinyinMatch(clauses, "device_alias.pinyin", value, 3);
                addPinyinMatch(clauses, "location.pinyin", value, 3);
            } else if (type == EntityType.CAMERA) {
                addPinyinMatch(clauses, "address.pinyin", value, 2);
            }
        }
    }

    private void addPinyinFallbackClauses(List<Map<String, Object>> clauses, EntityType type,
                                          String keyword, List<String> additionalKeywords) {
        if (type != EntityType.DEVICE && type != EntityType.CAMERA && type != EntityType.DEVICE_TYPE) return;
        String nameField = nameField(type);
        addPinyinMatch(clauses, nameField + ".pinyin", keyword, 15);
        addPinyinMatch(clauses, "aliases.pinyin", keyword, 12);
        if (type == EntityType.DEVICE || type == EntityType.DEVICE_TYPE) {
            addPinyinMatch(clauses, "device_name.pinyin", keyword, 13);
            addPinyinMatch(clauses, "device_alias.pinyin", keyword, 12);
            addPinyinMatch(clauses, "location.pinyin", keyword, 10);
        }
        if (type == EntityType.CAMERA) {
            addPinyinMatch(clauses, "address.pinyin", keyword, 8);
        }
        if (additionalKeywords == null) return;
        for (String alternative : additionalKeywords) {
            if (!hasText(alternative) || alternative.trim().equalsIgnoreCase(keyword)) continue;
            String value = alternative.trim();
            addPinyinMatch(clauses, nameField + ".pinyin", value, 9);
            addPinyinMatch(clauses, "aliases.pinyin", value, 7);
            if (type == EntityType.DEVICE || type == EntityType.DEVICE_TYPE) {
                addPinyinMatch(clauses, "device_name.pinyin", value, 8);
                addPinyinMatch(clauses, "device_alias.pinyin", value, 7);
                addPinyinMatch(clauses, "location.pinyin", value, 6);
            }
            if (type == EntityType.CAMERA) {
                addPinyinMatch(clauses, "address.pinyin", value, 5);
            }
        }
    }

    private void addPinyinMatch(List<Map<String, Object>> clauses, String field, String value, int boost) {
        clauses.add(Map.of("match", Map.of(field, Map.of(
                "query", value, "boost", boost))));
    }

    private boolean hasHits(JsonNode response) {
        JsonNode hits = response == null ? null : response.path("hits").path("hits");
        return hits != null && hits.isArray() && !hits.isEmpty();
    }

    private boolean shouldUseFuzzy(String value) {
        if (!hasText(value)) return false;
        int length = value.codePointCount(0, value.length());
        return length >= 3 && length <= 64 && !value.matches("\\d+");
    }

    /**
     * 单字符拼写容错仅作为常规召回失败后的补充通道，权重低于精确、IK 和拼音匹配。
     * match fuzziness 处理 IK 分词后的词项；raw fuzzy 处理完整名称/别名中的单字编辑差异。
     */
    private void addFuzzyFallbackClauses(List<Map<String, Object>> clauses, EntityType type, String value) {
        String name = nameField(type);
        addFuzzyMatch(clauses, name, value, 5);
        addFuzzyMatch(clauses, "aliases", value, 4);
        addFuzzyTerm(clauses, name + ".raw", value, 3);
        addFuzzyTerm(clauses, "aliases.raw", value, 2);

        if (type == EntityType.DEVICE || type == EntityType.DEVICE_TYPE) {
            addFuzzyMatch(clauses, "device_name", value, 5);
            addFuzzyMatch(clauses, "device_alias", value, 4);
            addFuzzyMatch(clauses, "location", value, 3);
            addFuzzyTerm(clauses, "device_name.raw", value, 4);
            addFuzzyTerm(clauses, "device_alias.raw", value, 3);
            addFuzzyTerm(clauses, "location.raw", value, 2);
            if (type == EntityType.DEVICE && value.matches("(?i).*[a-z].*\\d.*|.*\\d.*[a-z].*")
                    && value.length() <= 32) {
                addFuzzyTerm(clauses, "device_code", value, 2);
            }
        } else if (type == EntityType.CAMERA) {
            addFuzzyMatch(clauses, "address", value, 2);
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
            case DEVICE_TYPE -> List.of("id", "cascade_id", "device_type", "system_type", "device_alias.raw");
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
