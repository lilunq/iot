package com.sysware.xian.module.entity.catalog.controller;

import com.sysware.xian.module.entity.catalog.EntityCatalogService;
import com.sysware.xian.module.entity.common.EntityType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 实体目录管理查询接口。 */
@RestController
@RequestMapping("/entity-center/catalog")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "enabled", havingValue = "true")
public class EntityCatalogController {
    private final EntityCatalogService catalogService;

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return catalogService.summary();
    }

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

    @GetMapping("/{entityType}/{id}")
    public Map<String, Object> detail(@PathVariable String entityType, @PathVariable String id) {
        return catalogService.detail(EntityType.from(entityType), id);
    }
}
