package com.sysware.xian.module.entity.synchronizer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.module.entity.common.EntityType;
import com.sysware.xian.module.entity.init.alias.BizEntityAlias;
import com.sysware.xian.module.entity.init.alias.BizEntityAliasService;
import com.sysware.xian.module.entity.repository.EntityIndexRepository;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import com.sysware.xian.swcloud.fegin.AdminClient;
import com.sysware.xian.swcloud.fegin.ScadaClient;
import com.sysware.xian.swcloud.fegin.VideoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 五类业务实体源数据同步适配器。
 * 统一以源系统稳定业务标识作为 ES _id，支持重复执行的幂等 upsert。
 * 不在普通同步中删除旧数据；删除/重建和 Alias 切换由独立运维流程负责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "enabled", havingValue = "true")
public class EntitySourceSyncService {
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGES = 1000;
    private static final String ORG_ID = "3";

    private final AdminClient adminClient;
    private final ScadaClient scadaClient;
    private final VideoClient videoClient;
    private final EntityIndexRepository indexRepository;
    private final BizEntityAliasService aliasService;
    private final ObjectMapper objectMapper;

    /** 按依赖顺序同步五类基础实体。 */
    public Map<String, Integer> syncAvailableSources() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("AREA", syncAreas());
        result.put("SYSTEM", syncSystems());
        result.put("DEVICE_TYPE", syncDeviceTypes());
        result.put("DEVICE", syncDevices());
        result.put("CAMERA", syncCameras());
        return result;
    }

    public int syncAreas() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("org_id", ORG_ID);
        AjaxResultVO response = adminClient.getSubareaList(params);
        assertSuccess(response, "区域列表");
        List<Map<String, Object>> rows = readRows(response.get("data"));

        Map<String, String> cascadeById = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String id = firstText(row, "id");
            String cascadeId = firstText(row, "cascade_id", "cascadeId");
            if (!isBlank(id) && !isBlank(cascadeId)) cascadeById.put(id, cascadeId);
        }

        Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String cascadeId = firstText(row, "cascade_id", "cascadeId");
            if (isBlank(cascadeId)) {
                log.warn("跳过缺少 cascade_id 的区域记录: {}", row.get("id"));
                continue;
            }
            String parentId = firstText(row, "parent_id", "parentId");
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("cascade_cid", cascadeId);
            put(doc, "area_name", firstText(row, "name"));
            put(doc, "parent_cascade_cid", cascadeById.get(parentId));
            put(doc, "parent_id", parentId);
            put(doc, "org_id", firstText(row, "org_id", "orgId"));
            put(doc, "sort_no", firstNumber(row, "sort_no", "sortNo"));
            put(doc, "is_leaf", firstText(row, "is_leaf", "isLeaf"));
            doc.put("enabled", true);
            documents.put(cascadeId, doc);
        }
        return writeWithAliases(EntityType.AREA, documents);
    }

    /** AdminClient 字典接口返回 List；element_key 是系统稳定业务标识。 */
    public int syncSystems() {
        List<Map<String, Object>> rows = adminClient.getEnumKeyValues("device_system_type");
        if (rows == null) throw new IllegalStateException("设备系统枚举接口返回空响应");
        Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = firstText(row, "element_key", "elementKey");
            if (isBlank(key)) continue;
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("element_key", key);
            put(doc, "element_value", firstText(row, "element_value", "elementValue"));
            put(doc, "enum_key", firstText(row, "enum_key", "enumKey"));
            put(doc, "enum_name", firstText(row, "enum_name", "enumName"));
            put(doc, "sort_no", firstNumber(row, "sort_no", "sortNo"));
            doc.put("enabled", isEnabled(firstValue(row, "is_enable", "isEnable")));
            documents.put(key, doc);
        }
        return writeWithAliases(EntityType.SYSTEM, documents);
    }

    /** 设备分类接口来自 ScadaClient：/base/device/type/list。 */
    public int syncDeviceTypes() {
        AjaxResultVO response = scadaClient.listDeviceTypes();
        assertSuccess(response, "设备分类列表");
        List<Map<String, Object>> rows = readRows(response.get("data"));
        Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String id = firstText(row, "id");
            if (isBlank(id)) continue;
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("id", id);
            put(doc, "name", firstText(row, "name"));
            put(doc, "parent_id", firstText(row, "parent_id", "parentId"));
            put(doc, "cascade_id", firstText(row, "cascade_id", "cascadeId"));
            put(doc, "is_folder", firstText(row, "is_folder", "isFolder"));
            put(doc, "is_expand", firstText(row, "is_expand", "isExpand"));
            put(doc, "sort_no", firstNumber(row, "sort_no", "sortNo"));
            put(doc, "ext_code", firstText(row, "ext_code", "extCode"));
            put(doc, "remark", firstText(row, "remark"));
            doc.put("enabled", true);
            documents.put(id, doc);
        }
        return writeWithAliases(EntityType.DEVICE_TYPE, documents);
    }

    public int syncDevices() {
        int written = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("page", Integer.toString(page));
            params.put("limit", Integer.toString(PAGE_SIZE));
            params.put("org_id", ORG_ID);
            AjaxResultVO response = scadaClient.listPage(params);
            assertSuccess(response, "设备列表第 " + page + " 页");
            List<Map<String, Object>> rows = readRows(response.get("data"));
            Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String id = firstText(row, "id");
                if (isBlank(id)) continue;
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("id", id);
                put(doc, "org_id", firstText(row, "org_id", "orgId"));
                put(doc, "device_name", firstText(row, "device_name", "deviceName"));
                put(doc, "device_alias", firstText(row, "device_alias", "deviceAlias"));
                put(doc, "device_type_id", firstText(row, "device_type", "device_type_id", "deviceType"));
                put(doc, "system_key", firstText(row, "system_type", "system_key", "systemType"));
                put(doc, "subarea_cid", firstText(row, "subarea_cid", "subareaCid"));
                put(doc, "subarea_id", firstText(row, "subarea_id", "subareaId"));
                put(doc, "factory", firstText(row, "factory"));
                put(doc, "production_date", firstText(row, "production_date", "productionDate"));
                put(doc, "install_date", firstText(row, "install_date", "installDate"));
                put(doc, "location", firstText(row, "location"));
                put(doc, "nfc", firstText(row, "nfc"));
                put(doc, "fire_key", firstText(row, "fire_key", "fireKey"));
                put(doc, "device_fac_id", firstText(row, "device_fac_id", "deviceFacId"));
                put(doc, "sort_no", firstNumber(row, "sort_no", "sortNo"));
                doc.put("enabled", true);
                documents.put(id, doc);
            }
            written += writeWithAliases(EntityType.DEVICE, documents);
            if (rows.size() < PAGE_SIZE) return written;
            if (page == MAX_PAGES) {
                throw new IllegalStateException("设备同步达到最大分页保护值 " + MAX_PAGES + "，请检查分页参数或总量");
            }
        }
        return written;
    }

    public int syncCameras() {
        int written = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            AjaxResultVO response = videoClient.listPage(null, null,
                    Integer.toString(page), Integer.toString(PAGE_SIZE));
            assertSuccess(response, "摄像头列表第 " + page + " 页");
            List<Map<String, Object>> rows = readRows(response.get("data"));
            Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String id = firstText(row, "id");
                if (isBlank(id)) continue;
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("id", id);
                put(doc, "camera_no", firstText(row, "camera_no", "cameraNo"));
                put(doc, "camera_name", firstText(row, "name", "camera_name", "cameraName"));
                put(doc, "address", firstText(row, "address"));
                put(doc, "nvr_id", firstText(row, "nvr_id", "nvrId"));
                put(doc, "org_id", firstText(row, "org_id", "orgId"));
                put(doc, "main_channel", firstNumber(row, "main_channel", "mainChannel"));
                put(doc, "sub_channel", firstNumber(row, "sub_channel", "subChannel"));
                put(doc, "scada_no", firstNumber(row, "scada_no", "scadaNo"));
                doc.put("online", isEnabled(firstValue(row, "Online", "online")));
                doc.put("enabled", true);
                // 摄像头不写区域字段，也不接收区域过滤条件。
                documents.put(id, doc);
            }
            written += writeWithAliases(EntityType.CAMERA, documents);
            if (rows.size() < PAGE_SIZE) return written;
            if (page == MAX_PAGES) {
                throw new IllegalStateException("摄像头同步达到最大分页保护值 " + MAX_PAGES + "，请检查分页参数或总量");
            }
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
                    .map(value -> objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() { }))
                    .toList();
        }
        if (data instanceof Map<?, ?> map) {
            Object nested = firstValue(map, "records", "list", "rows", "data");
            if (nested != null && nested != data) return readRows(nested);
        }
        throw new IllegalStateException("源系统 data 不是数组，实际类型: " + data.getClass().getName());
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

    private Number firstNumber(Map<?, ?> map, String... names) {
        Object value = firstValue(map, names);
        if (value instanceof Number number) return number;
        if (value != null) {
            try { return Integer.parseInt(value.toString()); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private boolean isEnabled(Object value) {
        if (value == null) return true;
        if (value instanceof Boolean bool) return bool;
        String text = value.toString().trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null && !value.toString().isBlank()) target.put(key, value);
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}