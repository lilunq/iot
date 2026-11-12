package com.sysware.xian.domain;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ZoneConfig {
    private String id;
    private String orgId;
    private String parentId;
    private String name;
    private String cascadeId;
    private BigDecimal coordX;
    private BigDecimal coordY;
    private BigDecimal coordZ;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
