-- 统一业务实体解析中心：别名表
-- 本脚本由部署人员在确认目标数据库后手工执行，不随应用启动自动执行。
CREATE TABLE IF NOT EXISTS biz_entity_alias (
    id BIGINT NOT NULL COMMENT '主键',
    entity_type VARCHAR(32) NOT NULL COMMENT 'AREA/SYSTEM/DEVICE_TYPE/DEVICE/CAMERA',
    data_id VARCHAR(128) NOT NULL COMMENT '实体稳定业务标识',
    alias_name VARCHAR(255) NOT NULL COMMENT '别名原文',
    normalized_alias VARCHAR(255) NOT NULL COMMENT '标准化别名',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    source VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '别名来源',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_entity_alias (entity_type, data_id, normalized_alias),
    KEY idx_alias_lookup (entity_type, normalized_alias),
    KEY idx_entity_lookup (entity_type, data_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一业务实体别名配置表';
