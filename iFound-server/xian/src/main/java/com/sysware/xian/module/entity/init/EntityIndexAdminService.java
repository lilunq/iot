package com.sysware.xian.module.entity.init;

import com.sysware.xian.module.entity.common.EntityType;
import com.sysware.xian.module.entity.config.EntityCenterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 初始化五类实体索引。逻辑索引名作为读写 Alias，物理索引使用版本后缀。
 * 该服务只负责首次创建；后续全量重建应创建新版本、校验数据后再原子切换 Alias。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "enabled", havingValue = "true")
public class EntityIndexAdminService {
    private final RestClient entityElasticsearchRestClient;

    public Map<String, String> initializeAll() {
        Map<String, String> result = new LinkedHashMap<>();
        for (EntityType type : EntityType.values()) {
            String alias = type.getIndexAlias();
            String physical = alias + "_v1";
            ensureIndex(physical, mappingFor(type));
            ensureAlias(alias, physical);
            result.put(alias, physical);
        }
        return result;
    }

    private void ensureIndex(String index, Map<String, Object> definition) {
        try {
            entityElasticsearchRestClient.get().uri("/{index}", index)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() != 404) throw ex;
            entityElasticsearchRestClient.put().uri("/{index}", index)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(definition)
                    .retrieve().toBodilessEntity();
        }
    }

    private void ensureAlias(String alias, String physicalIndex) {
        try {
            entityElasticsearchRestClient.get().uri("/_alias/{alias}", alias)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() != 404) throw ex;
            entityElasticsearchRestClient.post().uri("/_aliases")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("actions", new Object[]{
                            Map.of("add", Map.of("index", physicalIndex, "alias", alias, "is_write_index", true))
                    }))
                    .retrieve().toBodilessEntity();
        }
    }

    private Map<String, Object> mappingFor(EntityType type) {
        Map<String, Object> fields = new LinkedHashMap<>();
        switch (type) {
            case AREA -> {
                keyword(fields, "cascade_cid", "parent_cascade_cid", "parent_id", "org_id", "is_leaf");
                integer(fields, "sort_no");
                text(fields, "area_name", "parent_name", "aliases");
                bool(fields, "enabled");
            }
            case SYSTEM -> {
                keyword(fields, "element_key", "enum_key");
                integer(fields, "sort_no");
                text(fields, "element_value", "enum_name", "aliases");
                bool(fields, "enabled");
            }
            case DEVICE_TYPE -> {
                keyword(fields, "id", "parent_id", "cascade_id", "is_folder", "is_expand", "ext_code");
                integer(fields, "sort_no");
                text(fields, "name", "remark", "aliases");
                bool(fields, "enabled");
            }
            case DEVICE -> {
                keyword(fields, "id", "org_id", "device_code", "subarea_cid", "subarea_id",
                        "system_key", "device_type_id", "device_type_cascade_id", "parent_device_id",
                        "factory", "production_date", "install_date", "nfc", "fire_key", "device_fac_id");
                integer(fields, "sort_no");
                text(fields, "device_name", "device_alias", "aliases",
                        "subarea_name", "system_name", "device_type_name", "location");
                bool(fields, "enabled");
            }
            case CAMERA -> {
                // 摄像头没有区域字段，不添加区域映射，也不执行区域过滤。
                keyword(fields, "id", "camera_no", "nvr_id", "org_id");
                text(fields, "camera_name", "address", "aliases");
                integer(fields, "main_channel", "sub_channel", "scada_no");
                bool(fields, "online", "enabled");
            }
        }

        Map<String, Object> pinyinTokenizer = Map.of(
                "type", "pinyin",
                "keep_first_letter", false,
                "keep_full_pinyin", true,
                "keep_joined_full_pinyin", true,
                "keep_original", true,
                "limit_first_letter_length", 0,
                "remove_duplicated_term", true
        );
        Map<String, Object> settings = Map.of("analysis", Map.of(
                "tokenizer", Map.of("entity_pinyin", pinyinTokenizer),
                "analyzer", Map.of(
                        "entity_ik_index", Map.of("type", "custom", "tokenizer", "ik_max_word"),
                        "entity_ik_search", Map.of("type", "custom", "tokenizer", "ik_smart"),
                        "entity_pinyin", Map.of("type", "custom", "tokenizer", "entity_pinyin")
                )
        ));
        return Map.of(
                "settings", settings,
                "mappings", Map.of("dynamic", false, "properties", fields)
        );
    }

    private void keyword(Map<String, Object> fields, String... names) {
        for (String name : names) fields.put(name, Map.of("type", "keyword"));
    }

    private void bool(Map<String, Object> fields, String... names) {
        for (String name : names) fields.put(name, Map.of("type", "boolean"));
    }

    private void integer(Map<String, Object> fields, String... names) {
        for (String name : names) fields.put(name, Map.of("type", "integer"));
    }

    private void text(Map<String, Object> fields, String... names) {
        for (String name : names) {
            fields.put(name, Map.of(
                    "type", "text",
                    "analyzer", "entity_ik_index",
                    "search_analyzer", "entity_ik_search",
                    "fields", Map.of(
                            "raw", Map.of("type", "keyword", "ignore_above", 512),
                            "pinyin", Map.of("type", "text", "analyzer", "entity_pinyin")
                    )
            ));
        }
    }
}
