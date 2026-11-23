package com.sysware.xian.module.entity.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Elasticsearch 访问边界。上层服务不直接拼装 HTTP 请求。 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "enabled", havingValue = "true")
public class EntityIndexRepository {
    private final RestClient entityElasticsearchRestClient;
    private final ObjectMapper objectMapper;

    public JsonNode search(String indexAlias, Map<String, Object> query) {
        return entityElasticsearchRestClient.post()
                .uri("/{index}/_search", indexAlias)
                .contentType(MediaType.APPLICATION_JSON)
                .body(query)
                .retrieve()
                .body(JsonNode.class);
    }

    /** 查询索引中启用实体数量。 */
    public long countEnabled(String indexAlias) {
        JsonNode response = entityElasticsearchRestClient.post()
                .uri("/{index}/_count", indexAlias)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", Map.of("term", Map.of("enabled", true))))
                .retrieve()
                .body(JsonNode.class);
        return response == null ? 0L : response.path("count").asLong(0L);
    }

    /** 按稳定业务 ID 读取完整 _source。 */
    public JsonNode getDocument(String indexAlias, String id) {
        try {
            return entityElasticsearchRestClient.get()
                    .uri("/{index}/_doc/{id}", indexAlias, id)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) return null;
            throw ex;
        }
    }

    /** 只更新实体文档中的指定字段，避免为了同步别名而覆盖源系统的其它字段。 */
    public JsonNode updateDocument(String indexAlias, String id, Map<String, Object> partialDocument) {
        if (partialDocument == null || partialDocument.isEmpty()) {
            throw new IllegalArgumentException("ES 局部更新内容不能为空");
        }
        JsonNode response = entityElasticsearchRestClient.post()
                .uri("/{index}/_update/{id}", indexAlias, id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("doc", partialDocument))
                .retrieve()
                .body(JsonNode.class);
        if (response == null || response.has("error")) {
            throw new IllegalStateException("ES 局部更新失败: "
                    + (response == null ? "empty response" : response.path("error").toString()));
        }
        return response;
    }

    /** 使用映射字段的拼音 multi-field 获取 ES 实际分析 token，不在 Java 侧生成拼音。 */
    public JsonNode analyze(String indexAlias, String field, String text) {
        return entityElasticsearchRestClient.post()
                .uri("/{index}/_analyze", indexAlias)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("field", field, "text", text))
                .retrieve()
                .body(JsonNode.class);
    }

    /** 通过源系统稳定业务 ID 作为 ES 文档 ID，重复执行具备幂等性。 */
    public int bulkIndex(String indexAlias, Map<String, Map<String, Object>> documents) {
        if (documents == null || documents.isEmpty()) return 0;
        StringBuilder ndjson = new StringBuilder();
        try {
            for (Map.Entry<String, Map<String, Object>> entry : documents.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    throw new IllegalArgumentException("ES 文档业务 ID 不能为空");
                }
                Map<String, Object> action = Map.of("index",
                        Map.of("_index", indexAlias, "_id", entry.getKey()));
                ndjson.append(objectMapper.writeValueAsString(action)).append('\n');
                String documentJson = objectMapper.writeValueAsString(entry.getValue());
                ndjson.append(documentJson).append('\n');
            }
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("构造 ES Bulk 请求失败", ex);
        }

        // StringHttpMessageConverter 在未声明 charset 时可能使用 ISO-8859-1，
        // 导致中文在 HTTP 发送前被替换为问号。显式构造 UTF-8 字节，避免默认字符集介入。
        byte[] requestBody = ndjson.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode response = entityElasticsearchRestClient.post()
                .uri("/_bulk")
                .contentType(MediaType.parseMediaType("application/x-ndjson;charset=UTF-8"))
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) throw new IllegalStateException("ES Bulk 返回空响应");
        if (response.path("errors").asBoolean(false)) {
            List<String> failures = new ArrayList<>();
            for (JsonNode item : response.path("items")) {
                JsonNode operation = item.path("index");
                if (operation.path("error").isObject()) {
                    failures.add(operation.path("_id").asText() + ": "
                            + operation.path("error").path("reason").asText("unknown"));
                }
            }
            throw new EntityBulkWriteException("ES Bulk 部分写入失败: " + String.join("; ", failures));
        }
        return documents.size();
    }

    public List<Map<String, Object>> sources(JsonNode response) {
        List<Map<String, Object>> result = new ArrayList<>();
        JsonNode hits = response == null ? null : response.path("hits").path("hits");
        if (hits == null || !hits.isArray()) return result;
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            if (source.isObject()) {
                result.add(objectMapper.convertValue(source, new TypeReference<Map<String, Object>>() { }));
            }
        }
        return result;
    }

    public static class EntityBulkWriteException extends RuntimeException {
        public EntityBulkWriteException(String message) { super(message); }
    }
}
