package com.sysware.xian.module.entity.init.controller;

import com.sysware.xian.module.entity.init.EntityIndexAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 管理接口默认关闭，需在受控环境显式开启。 */
@RestController
@RequestMapping("/api/entity-center/admin")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "admin-api-enabled", havingValue = "true")
public class EntityIndexAdminController {
    private final EntityIndexAdminService indexAdminService;

    @PostMapping("/indexes/initialize")
    public Map<String, String> initializeIndexes() {
        return indexAdminService.initializeAll();
    }
}
