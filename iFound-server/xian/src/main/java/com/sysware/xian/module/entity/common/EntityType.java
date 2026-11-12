package com.sysware.xian.module.entity.common;

/** 统一实体中心支持的实体类型；各实体类型使用独立 ES 索引。 */
public enum EntityType {
    AREA("station_areas", "cascade_cid"),
    SYSTEM("station_systems", "element_key"),
    DEVICE_TYPE("station_device_types", "id"),
    DEVICE("station_devices", "id"),
    CAMERA("station_cameras", "id");

    private final String indexAlias;
    private final String businessIdField;

    EntityType(String indexAlias, String businessIdField) {
        this.indexAlias = indexAlias;
        this.businessIdField = businessIdField;
    }

    public String getIndexAlias() { return indexAlias; }
    public String getBusinessIdField() { return businessIdField; }

    public static EntityType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("entityType 不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的实体类型: " + value, ex);
        }
    }
}
