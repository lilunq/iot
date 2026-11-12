package com.sysware.xian.module.entity.init.alias;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sysware.xian.module.entity.common.EntityTextNormalizer;
import com.sysware.xian.module.entity.common.EntityType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BizEntityAliasService {
    private final BizEntityAliasMapper mapper;

    public List<String> findEnabledDataIds(EntityType type, String normalizedAlias) {
        if (normalizedAlias == null || normalizedAlias.isBlank()) return List.of();
        return mapper.selectList(new LambdaQueryWrapper<BizEntityAlias>()
                        .select(BizEntityAlias::getDataId)
                        .eq(BizEntityAlias::getEntityType, type.name())
                        .eq(BizEntityAlias::getNormalizedAlias, normalizedAlias)
                        .eq(BizEntityAlias::getEnabled, 1))
                .stream()
                .map(BizEntityAlias::getDataId)
                .distinct()
                .toList();
    }

    public List<BizEntityAlias> listEnabled(EntityType type, Collection<String> dataIds) {
        if (dataIds == null || dataIds.isEmpty()) return List.of();
        return mapper.selectList(new LambdaQueryWrapper<BizEntityAlias>()
                .eq(BizEntityAlias::getEntityType, type.name())
                .eq(BizEntityAlias::getEnabled, 1)
                .in(BizEntityAlias::getDataId, dataIds));
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveAlias(BizEntityAlias alias) {
        if (alias == null || alias.getEntityType() == null || alias.getDataId() == null
                || alias.getDataId().isBlank() || alias.getAliasName() == null
                || alias.getAliasName().isBlank()) {
            throw new IllegalArgumentException("实体类型、实体业务标识和别名不能为空");
        }
        alias.setEntityType(EntityType.from(alias.getEntityType()).name());
        alias.setDataId(alias.getDataId().trim());
        alias.setAliasName(alias.getAliasName().trim());
        alias.setNormalizedAlias(EntityTextNormalizer.normalize(alias.getAliasName()));
        if (alias.getEnabled() == null) alias.setEnabled(1);
        if (alias.getSource() == null || alias.getSource().isBlank()) alias.setSource("MANUAL");

        Long duplicateCount = mapper.selectCount(new LambdaQueryWrapper<BizEntityAlias>()
                .eq(BizEntityAlias::getEntityType, alias.getEntityType())
                .eq(BizEntityAlias::getDataId, alias.getDataId())
                .eq(BizEntityAlias::getNormalizedAlias, alias.getNormalizedAlias()));
        if (duplicateCount != null && duplicateCount > 0) {
            throw new IllegalArgumentException("该实体已存在相同别名");
        }
        mapper.insert(alias);
    }
}
