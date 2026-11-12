package com.sysware.xian.module.entity.common;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EntityResolveResponse {
    private String status;
    private String entityType;
    private String query;
    private String normalizedQuery;
    private EntityCandidate resolved;
    private List<EntityCandidate> candidates;
    private String message;
}
