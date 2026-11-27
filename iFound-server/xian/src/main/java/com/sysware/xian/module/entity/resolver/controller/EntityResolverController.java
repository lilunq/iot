package com.sysware.xian.module.entity.resolver.controller;

import com.sysware.xian.module.entity.common.EntityResolveRequest;
import com.sysware.xian.module.entity.common.EntityResolveResponse;
import com.sysware.xian.module.entity.resolver.EntityResolverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** 统一业务实体解析 REST 接口。 */
@RestController
@Tag(name = "ES接口", description = "")
@RequestMapping("/entity-center")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "enabled", havingValue = "true")
public class EntityResolverController {
    private final EntityResolverService resolverService;
    @Operation(summary = "实体解析接口", description = "")
    @PostMapping("/resolve")
    public EntityResolveResponse resolve(@RequestBody EntityResolveRequest request) {
        return resolverService.resolve(request);
    }
}
