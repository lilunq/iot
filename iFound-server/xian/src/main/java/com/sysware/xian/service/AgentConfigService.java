package com.sysware.xian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sysware.xian.domain.AgentConfig;
import com.sysware.xian.mapper.AgentConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConfigService extends ServiceImpl<AgentConfigMapper, AgentConfig> {

    @Value("${agent.config-cache-ttl-ms:300000}")
    private long configCacheTtlMs;

    private volatile List<AgentConfig> availableAgentCache = Collections.emptyList();
    private volatile long availableAgentCacheExpiresAt;

    public List<AgentConfig> listAll() {
        return list(new LambdaQueryWrapper<AgentConfig>().orderByAsc(AgentConfig::getCreateTime));
    }

    public List<AgentConfig> listAvailable() {
        long now = System.currentTimeMillis();
        List<AgentConfig> cached = availableAgentCache;
        if (now < availableAgentCacheExpiresAt) {
            return cached;
        }

        synchronized (this) {
            now = System.currentTimeMillis();
            if (now < availableAgentCacheExpiresAt) {
                return availableAgentCache;
            }
            List<AgentConfig> loaded = getBaseMapper().selectAvailableAgents();
            availableAgentCache = loaded == null ? Collections.emptyList() : List.copyOf(loaded);
            availableAgentCacheExpiresAt = now + Math.max(configCacheTtlMs, 1000L);
            log.debug("已刷新可用Agent配置缓存，共 {} 条", availableAgentCache.size());
            return availableAgentCache;
        }
    }

    public void evictAvailableCache() {
        availableAgentCacheExpiresAt = 0L;
        availableAgentCache = Collections.emptyList();
    }

    /**
     * 根据业务域匹配Agent
     */
    public AgentConfig matchAgentByDomain(String domain) {
        if (domain == null) return null;
        List<AgentConfig> agents = listAvailable();
        for (AgentConfig agent : agents) {
            if (agent.getBusinessDomains() != null) {
                List<String> domains = Arrays.stream(agent.getBusinessDomains().split(","))
                        .map(String::trim)
                        .collect(Collectors.toList());
                if (domains.contains(domain)) {
                    return agent;
                }
            }
        }
        return null;
    }

    public AgentConfig getById(String agentId) {
        return getOne(new LambdaQueryWrapper<AgentConfig>().eq(AgentConfig::getAgentId, agentId));
    }
}
