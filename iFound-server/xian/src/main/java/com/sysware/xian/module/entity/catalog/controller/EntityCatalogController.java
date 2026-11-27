package com.sysware.xian.module.entity.catalog.controller;

import com.sysware.xian.module.entity.catalog.EntityCatalogService;
import com.sysware.xian.module.entity.common.EntityType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 实体目录管理查询接口。 */
@RestController
@Tag(name = "ES接口", description = "")
@RequestMapping("/entity-center/catalog")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "enabled", havingValue = "true")
public class EntityCatalogController {
    private final EntityCatalogService catalogService;

    @Operation(summary = "实体目录统计接口", description = "统计区域、系统、设备分类、设备和摄像头等实体数量。")
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return catalogService.summary();
    }

    @Operation(summary = "设备目录区域/系统级联选项", description = "返回区域 cascade_cid 层级树和系统 element_key 层级树。")
    @GetMapping("/filter-options")
    public Map<String, Object> filterOptions() {
        return catalogService.filterOptions();
    }

    @Operation(summary = "实体目录分页查询接口", description = "按实体类型分页查询实体目录，支持关键词及设备区域、系统和分类过滤。")
    @GetMapping("/{entityType}")
    public Map<String, Object> list(@PathVariable String entityType,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String areaId,
                                    @RequestParam(required = false) String systemKey,
                                    @RequestParam(required = false) String deviceTypeId,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size) {
        return catalogService.list(EntityType.from(entityType), keyword, areaId, systemKey, deviceTypeId, page, size);
    }

    @Operation(summary = "实体模型详情查询接口", description = "根据实体类型和稳定业务标识查询实体模型详情及关联信息。")
    @GetMapping("/{entityType}/{id}")
    public Map<String, Object> detail(@PathVariable String entityType, @PathVariable String id) {
        return catalogService.detail(EntityType.from(entityType), id);
    }
}
