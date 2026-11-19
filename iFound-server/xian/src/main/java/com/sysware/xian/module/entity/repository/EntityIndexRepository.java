package com.sysware.xian.module.entity.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

    /** 通过源系统稳定业务 ID 作为 ES 文档 ID，重复执行具备幂等性。 */
    public int bulkIndex(String indexAlias, Map<String, Map<String, Object>> documents) {
        if (documents == null || documents.isEmpty()) return 0;
        StringBuilder ndjson = new StringBuilder();
        int diagnosticSample = 0;
        try {
            for (Map.Entry<String, Map<String, Object>> entry : documents.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    throw new IllegalArgumentException("ES 文档业务 ID 不能为空");
                }
                Map<String, Object> action = Map.of("index",
                        Map.of("_index", indexAlias, "_id", entry.getKey()));
                ndjson.append(objectMapper.writeValueAsString(action)).append('\n');
                String documentJson = objectMapper.writeValueAsString(entry.getValue());
                if ("station_devices".equals(indexAlias) && diagnosticSample < 5) {
                    diagnosticSample++;
                    String deviceName = entry.getValue().get("device_name") == null
                            ? null : entry.getValue().get("device_name").toString();
                    String serializedName = objectMapper.readTree(documentJson)
                            .path("device_name").asText(null);
                    log.info("[实体中心设备名称诊断] phase=Bulk JSON序列化后, sample={}, id={}, deviceName={}, codePoints={}, serializedDeviceName={}, serializedCodePoints={}",
                            diagnosticSample, entry.getKey(), deviceName, unicodeCodePoints(deviceName),
                            serializedName, unicodeCodePoints(serializedName));
                }
                ndjson.append(documentJson).append('\n');
            }
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("构造 ES Bulk 请求失败", ex);
        }

        // StringHttpMessageConverter 在未声明 charset 时可能使用 ISO-8859-1，
        // 导致中文在 HTTP 发送前被替换为问号。显式构造 UTF-8 字节，避免默认字符集介入。
        byte[] requestBody = ndjson.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        log.info("[实体中心Bulk编码诊断] indexAlias={}, contentType=application/x-ndjson;charset=UTF-8, bodyBytes={}",
                indexAlias, requestBody.length);
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

    private String unicodeCodePoints(String value) {
        if (value == null) return "null";
        if (value.isEmpty()) return "(empty)";
        return value.codePoints()
                .mapToObj(codePoint -> String.format("U+%04X", codePoint))
                .collect(java.util.stream.Collectors.joining(" "));
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
