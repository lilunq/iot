package com.sysware.core.subAgent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子 agent 委派业务缓存
 * <p>缓存对话的会话记录：key 为委派的 agent，value 为该 agent 的会话长 id（一天一个 agent 共用同一会话）</p>
 *
 * @Author: LiYanRu
 * @date: 2026-07-23
 **/
@Slf4j
@Component
public class SubAgentCache {

    /**
     * 能管 agent 标识
     */
    private static final String ENERGY_AGENT = "energyAgent";

    /**
     * 站房 agent 标识
     */
    private static final String STATION_AGENT = "stationAgent";

    /**
     * 会话缓存：agent 名称 -> 会话长 id
     */
    private final Map<String, String> sessionCache = new ConcurrentHashMap<>();

    /**
     * 获取能管 agent 缓存的会话长 id
     *
     * @return 会话长 id，未缓存时返回 null
     */
    public String getEnergySessionId() {
        return sessionCache.get(ENERGY_AGENT);
    }

    /**
     * 缓存能管 agent 的会话长 id
     *
     * @param sessionLongId 会话长 id
     */
    public void putEnergySessionId(String sessionLongId) {
        if (sessionLongId != null) {
            sessionCache.put(ENERGY_AGENT, sessionLongId);
        }
    }

    /**
     * 移除能管 agent 缓存的会话长 id（会话过期时使用）
     */
    public void evictEnergySessionId() {
        sessionCache.remove(ENERGY_AGENT);
    }

    /**
     * 获取站房 agent 缓存的会话长 id
     *
     * @return 会话长 id，未缓存时返回 null
     */
    public String getStationSessionId() {
        return sessionCache.get(STATION_AGENT);
    }

    /**
     * 缓存站房 agent 的会话长 id
     *
     * @param sessionLongId 会话长 id
     */
    public void putStationSessionId(String sessionLongId) {
        if (sessionLongId != null) {
            sessionCache.put(STATION_AGENT, sessionLongId);
        }
    }

    /**
     * 移除站房 agent 缓存的会话长 id（会话过期时使用）
     */
    public void evictStationSessionId() {
        sessionCache.remove(STATION_AGENT);
    }

    /**
     * 清空全部会话缓存
     */
    public void clear() {
        int size = sessionCache.size();
        sessionCache.clear();
        log.info("SubAgent 会话缓存已清空, size={}", size);
    }

    /**
     * 每日 0 点清空会话缓存，保证一天一个 agent 共用同一会话
     */
    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Shanghai")
    public void clearAtMidnight() {
        clear();
    }
}