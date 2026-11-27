package com.sysware.xian.module.entity.init.controller;

import com.sysware.xian.module.entity.init.EntityIndexAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 管理接口默认关闭，需在受控环境显式开启。 */
@RestController
@RequestMapping("/entity-center/admin")
@Tag(name = "ES接口", description = "")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = {"enabled", "admin-api-enabled"}, havingValue = "true")
public class EntityIndexAdminController {
    private final EntityIndexAdminService indexAdminService;
    @Operation(summary = "管理接口", description = "")
    @PostMapping("/indexes/initialize")
    public Map<String, String> initializeIndexes() {
        return indexAdminService.initializeAll();
    }
}
