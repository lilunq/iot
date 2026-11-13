package com.sysware.xian.module.entity.init.alias.controller;

import com.sysware.xian.module.entity.common.EntityType;
import com.sysware.xian.module.entity.init.alias.BizEntityAlias;
import com.sysware.xian.module.entity.init.alias.BizEntityAliasService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 别名维护接口默认关闭，启用前应由现有网关/权限体系限制访问。 */
@RestController
@RequestMapping("/entity-center/aliases")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center", name = "alias-api-enabled", havingValue = "true")
public class EntityAliasController {
    private final BizEntityAliasService aliasService;

    @GetMapping
    public List<BizEntityAlias> list(@RequestParam String entityType, @RequestParam String dataId) {
        return aliasService.listEnabled(EntityType.from(entityType), List.of(dataId));
    }

    @PostMapping
    public void create(@RequestBody BizEntityAlias alias) {
        aliasService.saveAlias(alias);
    }
}
