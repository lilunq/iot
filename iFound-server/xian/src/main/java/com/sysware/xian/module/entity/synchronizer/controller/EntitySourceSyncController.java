package com.sysware.xian.module.entity.synchronizer.controller;

import com.sysware.xian.module.entity.synchronizer.EntitySourceSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 手动同步接口默认关闭。启用前需完成 ES/SQL 初始化并由现有网关做权限控制。
 * 当前操作为幂等 upsert，不会删除源系统已不存在的旧文档。
 */
@RestController
@RequestMapping("/api/entity-center/admin/sync")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch",
        name = {"enabled", "sync-api-enabled"}, havingValue = "true")
public class EntitySourceSyncController {
    private final EntitySourceSyncService syncService;

    @PostMapping("/available")
    public Map<String, Integer> syncAvailableSources() {
        return syncService.syncAvailableSources();
    }

    @PostMapping("/areas")
    public int syncAreas() {
        return syncService.syncAreas();
    }

    @PostMapping("/systems")
    public int syncSystems() {
        return syncService.syncSystems();
    }

    @PostMapping("/devices")
    public int syncDevices() {
        return syncService.syncDevices();
    }

    @PostMapping("/cameras")
    public int syncCameras() {
        return syncService.syncCameras();
    }
}
