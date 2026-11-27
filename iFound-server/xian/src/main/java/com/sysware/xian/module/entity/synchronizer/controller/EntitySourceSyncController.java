package com.sysware.xian.module.entity.synchronizer.controller;

import com.sysware.xian.module.entity.synchronizer.EntitySourceSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 手动同步接口默认关闭。启用前需完成 ES/SQL 初始化并由现有网关做权限控制。
 * 当前操作为幂等 upsert，不会删除源系统已不存在的旧文档。
 */
@RestController
@Tag(name = "ES接口", description = "")
@RequestMapping("/entity-center/admin/sync")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch",
        name = {"enabled", "sync-api-enabled"}, havingValue = "true")
public class EntitySourceSyncController {
    private final EntitySourceSyncService syncService;

    @Operation(summary = "同步全部可用实体数据接口", description = "同步当前可用数据源中的区域、系统、设备分类、设备和摄像头数据。")
    @PostMapping("/available")
    public Map<String, Integer> syncAvailableSources() {
        return syncService.syncAvailableSources();
    }

    @Operation(summary = "同步区域实体数据接口", description = "从源系统同步区域实体数据至 Elasticsearch。")
    @PostMapping("/areas")
    public int syncAreas() {
        return syncService.syncAreas();
    }

    @Operation(summary = "同步系统实体数据接口", description = "从源系统同步系统实体数据至 Elasticsearch。")
    @PostMapping("/systems")
    public int syncSystems() {
        return syncService.syncSystems();
    }

    @Operation(summary = "同步设备分类实体数据接口", description = "从源系统同步设备分类实体数据至 Elasticsearch。")
    @PostMapping("/device-types")
    public int syncDeviceTypes() {
        return syncService.syncDeviceTypes();
    }

    @Operation(summary = "同步设备实体数据接口", description = "从源系统同步设备实体数据至 Elasticsearch。")
    @PostMapping("/devices")
    public int syncDevices() {
        return syncService.syncDevices();
    }

    @Operation(summary = "同步摄像头实体数据接口", description = "从源系统同步摄像头实体数据至 Elasticsearch。")
    @PostMapping("/cameras")
    public int syncCameras() {
        return syncService.syncCameras();
    }
}
