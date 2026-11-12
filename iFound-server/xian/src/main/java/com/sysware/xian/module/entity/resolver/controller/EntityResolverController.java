package com.sysware.xian.module.entity.resolver.controller;

import com.sysware.xian.module.entity.common.EntityResolveRequest;
import com.sysware.xian.module.entity.common.EntityResolveResponse;
import com.sysware.xian.module.entity.resolver.EntityResolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** 统一业务实体解析 REST 接口。 */
@RestController
@RequestMapping("/api/entity-center")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center.elasticsearch", name = "enabled", havingValue = "true")
public class EntityResolverController {
    private final EntityResolverService resolverService;

    @PostMapping("/resolve")
    public EntityResolveResponse resolve(@RequestBody EntityResolveRequest request) {
        return resolverService.resolve(request);
    }
}
