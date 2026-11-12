package com.sysware.xian.module.entity.init.alias;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_entity_alias")
public class BizEntityAlias {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String entityType;
    private String dataId;
    private String aliasName;
    private String normalizedAlias;
    private Integer enabled;
    private String source;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
