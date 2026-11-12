package com.sysware.xian.module.entity.common;

import lombok.Data;

/**
 * areaId 对应区域 cascade_cid；systemKey 对应系统 element_key。
 * 区域和系统过滤仅用于 DEVICE；CAMERA 不执行区域解析或过滤。
 */
@Data
public class EntityResolveRequest {
    private String query;
    private String entityType;
    private String areaId;
    private String systemKey;
    private String deviceTypeId;
    private Integer limit;
}
