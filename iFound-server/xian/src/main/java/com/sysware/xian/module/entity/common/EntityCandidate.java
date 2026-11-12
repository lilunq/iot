package com.sysware.xian.module.entity.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityCandidate {
    private String entityId;
    private String entityType;
    private String name;
    private double score;
    private Map<String, Object> data;
}
