package com.sysware.xian.module.entity.synchronizer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.module.entity.common.EntityType;
import com.sysware.xian.module.entity.init.alias.BizEntityAlias;
import com.sysware.xian.module.entity.init.alias.BizEntityAliasService;
import com.sysware.xian.module.entity.repository.EntityIndexRepository;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import com.sysware.xian.swcloud.domain.ScadaPageQueryDto;
import com.sysware.xian.swcloud.fegin.AdminClient;
import com.sysware.xian.swcloud.fegin.MCPXClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 已确认数据源的首版同步适配器。
 * 使用源系统稳定业务标识作为 ES _id，按页 upsert，可重复执行。
 * 当前不执行全量删除/重建；索引重建和 Alias 切换走独立运维流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "enabled", havingValue = "true")
public class EntitySourceSyncService {
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGES = 1000;

    private final AdminClient adminClient;
    private final MCPXClient mcpClient;
    private final EntityIndexRepository indexRepository;
    private final BizEntityAliasService aliasService;
    private final ObjectMapper objectMapper;

    /** 同步当前工程中已定位并确认接口的数据源。设备分类源接口待确认。 */
    public Map<String, Integer> syncAvailableSources() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("AREA", syncAreas());
        result.put("SYSTEM", syncSystems());
        result.put("DEVICE", syncDevices());
        result.put("CAMERA", syncCameras());
        return result;
    }

    public int syncAreas() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("org_id", "3");
        AjaxResultVO response = adminClient.getSubareaList(params);
        assertSuccess(response, "区域列表");
        List<Map<String, Object>> rows = readRows(response.get("data"));
        Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String cascadeCid = firstText(row, "cascade_cid", "cascadeCid");
            if (isBlank(cascadeCid)) continue;
            Map<String, Object> doc = new LinkedHashMap<>();
            put(doc, "cascade_cid", cascadeCid);
            put(doc, "area_name", firstText(row, "subarea_name", "area_name", "name"));
            put(doc, "parent_cascade_cid", firstText(row, "parent_cascade_cid", "parentCascadeCid"));
            put(doc, "parent_name", firstText(row, "parent_name", "parentName"));
            put(doc, "org_id", firstText(row, "org_id", "orgId"));
            doc.put("enabled", true);
            documents.put(cascadeCid, doc);
        }
        return writeWithAliases(EntityType.AREA, documents);
    }

    /** 字典接口已确认返回 element_key -> element_value。 */
    public int syncSystems() {
        AjaxResultVO response = mcpClient.getDeviceSystemEnums("device_system_type");
        assertSuccess(response, "设备系统枚举");
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> values)) {
            throw new IllegalStateException("设备系统枚举 data 不是对象，无法按 element_key 映射");
        }
        Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String elementKey = Objects.toString(entry.getKey(), "").trim();
            if (elementKey.isEmpty()) continue;
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("element_key", elementKey);
            doc.put("element_value", Objects.toString(entry.getValue(), ""));
            doc.put("enabled", true);
            documents.put(elementKey, doc);
        }
        return writeWithAliases(EntityType.SYSTEM, documents);
    }

    public int syncDevices() {
        int written = 0;
        long total = Long.MAX_VALUE;
        for (int page = 1; page <= MAX_PAGES && (page - 1L) * PAGE_SIZE < total; page++) {
            ScadaPageQueryDto query = new ScadaPageQueryDto();
            query.setPage(Integer.toString(page));
            query.setLimit(Integer.toString(PAGE_SIZE));
            query.setOrgId("3");
            AjaxResultVO response = mcpClient.StateListPage(query);
            assertSuccess(response, "设备列表第 " + page + " 页");
            List<Map<String, Object>> rows = readRows(response.get("data"));
            total = readCount(response, rows.size(), total);
            Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String id = firstText(row, "id");
                if (isBlank(id)) continue;
                Map<String, Object> doc = new LinkedHashMap<>();
                put(doc, "id", id);
                put(doc, "device_name", firstText(row, "device_name", "name"));
                put(doc, "device_code", firstText(row, "device_code", "asset_code"));
                put(doc, "device_alias", firstText(row, "device_alias", "alias"));
                put(doc, "subarea_cid", firstText(row, "subarea_cid", "subareaCid"));
                put(doc, "subarea_name", firstText(row, "subarea_name", "subareaName"));
                // 当前 SCADA 查询条件使用 system_type；该字段对应系统字典 element_key。
                put(doc, "system_key", firstText(row, "system_key", "element_key", "system_type"));
                put(doc, "system_name", firstText(row, "system_name", "systemName"));
                put(doc, "device_type_id", firstText(row, "device_type_id", "deviceTypeId"));
                put(doc, "device_type_name", firstText(row, "device_type_name", "deviceTypeName"));
                put(doc, "parent_device_id", firstText(row, "parent_device_id", "parentDeviceId"));
                doc.put("enabled", true);
                documents.put(id, doc);
            }
            written += writeWithAliases(EntityType.DEVICE, documents);
            if (rows.size() < PAGE_SIZE) break;
        }
        return written;
    }

    public int syncCameras() {
        int written = 0;
        long total = Long.MAX_VALUE;
        for (int page = 1; page <= MAX_PAGES && (page - 1L) * PAGE_SIZE < total; page++) {
            AjaxResultVO response = mcpClient.listCameraPage(null,
                    Integer.toString(page), Integer.toString(PAGE_SIZE));
            assertSuccess(response, "摄像头列表第 " + page + " 页");
            List<Map<String, Object>> rows = readRows(response.get("data"));
            total = readCount(response, rows.size(), total);
            Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String id = firstText(row, "id");
                if (isBlank(id)) continue;
                Map<String, Object> doc = new LinkedHashMap<>();
                put(doc, "id", id);
                put(doc, "camera_no", firstText(row, "camera_no", "cameraNo"));
                put(doc, "camera_name", firstText(row, "camera_name", "name"));
                put(doc, "address", firstText(row, "address"));
                put(doc, "nvr_id", firstText(row, "nvr_id", "nvrId"));
                put(doc, "org_id", firstText(row, "org_id", "orgId"));
                put(doc, "main_channel", firstNumber(row, "main_channel", "mainChannel"));
                put(doc, "sub_channel", firstNumber(row, "sub_channel", "subChannel"));
                put(doc, "scada_no", firstNumber(row, "scada_no", "scadaNo"));
                Object online = firstValue(row, "Online", "online");
                if (online != null) doc.put("online", objectMapper.convertValue(online, Boolean.class));
                doc.put("enabled", true);
                // 摄像头不写入任何区域字段，不做区域解析或过滤。
                documents.put(id, doc);
            }
            written += writeWithAliases(EntityType.CAMERA, documents);
            if (rows.size() < PAGE_SIZE) break;
        }
        return written;
    }

    private int writeWithAliases(EntityType type, Map<String, Map<String, Object>> documents) {
        if (documents.isEmpty()) return 0;
        List<BizEntityAlias> aliases = aliasService.listEnabled(type, documents.keySet());
        Map<String, List<String>> aliasesById = aliases.stream().collect(Collectors.groupingBy(
                BizEntityAlias::getDataId,
                Collectors.mapping(BizEntityAlias::getAliasName, Collectors.toList())));
        documents.forEach((id, doc) -> {
            List<String> names = aliasesById.get(id);
            if (names != null && !names.isEmpty()) doc.put("aliases", names);
        });
        return indexRepository.bulkIndex(type.getIndexAlias(), documents);
    }

    private void assertSuccess(AjaxResultVO response, String sourceName) {
        if (response == null || !"200".equals(response.getCode())) {
            String message = response == null ? "empty response" : response.getMsg();
            throw new IllegalStateException(sourceName + "同步失败: " + message);
        }
    }

    private List<Map<String, Object>> readRows(Object data) {
        if (data == null) return List.of();
        if (data instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull)
                    .map(value -> objectMapper.convertValue(value,
                            new TypeReference<Map<String, Object>>() { }))
                    .toList();
        }
        if (data instanceof Map<?, ?> map) {
            Object nested = firstValue(map, "records", "list", "rows", "data");
            if (nested != null && nested != data) return readRows(nested);
        }
        throw new IllegalStateException("源系统分页 data 不是数组，实际类型: " + data.getClass().getName());
    }

    private long readCount(AjaxResultVO response, int currentSize, long fallback) {
        Object count = response.get("count");
        if (count instanceof Number number) return number.longValue();
        if (count != null) {
            try { return Long.parseLong(count.toString()); }
            catch (NumberFormatException ignored) { /* use current page size */ }
        }
        return currentSize < PAGE_SIZE ? currentSize : fallback;
    }

    private String firstText(Map<?, ?> map, String... names) {
        Object value = firstValue(map, names);
        return value == null ? null : value.toString().trim();
    }

    private Object firstValue(Map<?, ?> map, String... names) {
        for (String name : names) {
            Object value = map.get(name);
            if (value != null && !value.toString().isBlank()) return value;
        }
        return null;
    }

    private Number firstNumber(Map<String, Object> map, String... names) {
        Object value = firstValue(map, names);
        if (value instanceof Number number) return number;
        if (value != null) {
            try { return Integer.parseInt(value.toString()); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null && !value.toString().isBlank()) target.put(key, value);
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
