package com.sysware.xian.module.entity.init.alias.controller;

import com.sysware.xian.module.entity.common.EntityType;
import com.sysware.xian.module.entity.init.alias.BizEntityAlias;
import com.sysware.xian.module.entity.init.alias.BizEntityAliasService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 别名维护接口默认关闭，启用前应由现有网关/权限体系限制访问。 */
@RestController
@Tag(name = "ES接口", description = "")
@RequestMapping("/entity-center/aliases")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "entity-center", name = "alias-api-enabled", havingValue = "true")
public class EntityAliasController {
    private final BizEntityAliasService aliasService;

    @Operation(summary = "实体业务别名查询接口", description = "根据实体类型和稳定业务标识查询已启用的业务别名。")
    @GetMapping
    public List<BizEntityAlias> list(@RequestParam String entityType, @RequestParam String dataId) {
        return aliasService.listEnabled(EntityType.from(entityType), List.of(dataId));
    }

    @Operation(summary = "新增实体业务别名接口", description = "为指定实体新增业务别名，用于实体检索和自然语言解析。")
    @PostMapping
    public void create(@RequestBody BizEntityAlias alias) {
        aliasService.saveAlias(alias);
    }
}
