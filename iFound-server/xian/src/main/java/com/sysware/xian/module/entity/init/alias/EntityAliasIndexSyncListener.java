package com.sysware.xian.module.entity.init.alias;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sysware.xian.module.entity.common.EntityAreaNameNormalizer;
import com.sysware.xian.module.entity.common.EntityType;
import com.sysware.xian.module.entity.repository.EntityIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 数据库事务提交后，只更新发生别名变更的实体文档。
 * 设备别名同步时重新生成位置词条，避免要求用户手工维护拼音。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EntityAliasIndexSyncListener {
    private final EntityIndexRepository repository;
    private final BizEntityAliasMapper aliasMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAliasChanged(EntityAliasChangedEvent event) {
        if (event == null || event.entityType() == null || event.dataId() == null
                || event.dataId().isBlank()) return;

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                syncEntity(event);
                return;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("实体别名 ES 增量同步失败，第 {} 次尝试：type={}, id={}, reason={}",
                        attempt, event.entityType(), event.dataId(), ex.getMessage());
            }
        }
        log.error("实体别名已提交数据库，但 ES 增量同步连续失败，请重试或执行对应实体同步：type={}, id={}",
                event.entityType(), event.dataId(), lastFailure);
    }

    private void syncEntity(EntityAliasChangedEvent event) {
        EntityType type = event.entityType();
        String id = event.dataId();
        JsonNode document = repository.getDocument(type.getIndexAlias(), id);
        if (document == null || !document.path("found").asBoolean(false)) {
            // 实体尚未进入 ES 时不阻断别名保存；之后的源数据同步会一并写入别名。
            log.info("实体尚未建立 ES 文档，跳过本次别名增量更新：type={}, id={}", type, id);
            return;
        }

        List<BizEntityAlias> aliases = aliasMapper.selectList(new LambdaQueryWrapper<BizEntityAlias>()
                .eq(BizEntityAlias::getEntityType, type.name())
                .eq(BizEntityAlias::getDataId, id)
                .eq(BizEntityAlias::getEnabled, 1)
                .orderByAsc(BizEntityAlias::getId));
        List<String> aliasNames = aliases.stream()
                .map(BizEntityAlias::getAliasName)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        JsonNode source = document.path("_source");
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("aliases", aliasNames);
        if (type == EntityType.DEVICE) {
            List<String> positionSources = new ArrayList<>();
            addText(positionSources, source.path("device_name"));
            addText(positionSources, source.path("device_alias"));
            addText(positionSources, source.path("location"));
            addText(positionSources, source.path("subarea_name"));
            aliasNames.forEach(positionSources::add);
            addText(positionSources, source.path("device_position_variants"));
            patch.put("device_position_variants", new ArrayList<>(new LinkedHashSet<>(
                    EntityAreaNameNormalizer.extractPositionVariants(positionSources.toArray(new String[0])))));
        }
        repository.updateDocument(type.getIndexAlias(), id, patch);
        log.info("实体业务别名已增量同步至 ES：type={}, id={}, aliasCount={}",
                type, id, aliasNames.size());
    }

    private void addText(List<String> target, JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return;
        if (value.isArray()) {
            value.forEach(item -> addText(target, item));
            return;
        }
        if (!value.isValueNode()) return;
        String text = value.asText("").trim();
        if (!text.isBlank()) target.add(text);
    }
}
