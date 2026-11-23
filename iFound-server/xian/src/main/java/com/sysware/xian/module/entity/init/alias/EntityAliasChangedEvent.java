package com.sysware.xian.module.entity.init.alias;

import com.sysware.xian.module.entity.common.EntityType;

/** 业务别名发生变化后用于触发 ES 单实体增量同步。 */
public record EntityAliasChangedEvent(EntityType entityType, String dataId) { }
