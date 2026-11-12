package com.sysware.xian.service;

import com.sysware.xian.domain.SpeakerPermission;
import com.sysware.xian.mapper.SpeakerPermissionMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户语音权限缓存，启动时全量加载，CRUD 时同步更新，永久缓存无需 TTL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CachedSpeakerPermissionService {

    private final SpeakerPermissionMapper speakerPermissionMapper;

    /** key = userName */
    private final Map<String, SpeakerPermission> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshAll();
    }

    /**
     * 从数据库全量加载权限到缓存
     */
    public void refreshAll() {
        List<SpeakerPermission> list = speakerPermissionMapper.selectList(null);
        cache.clear();
        for (SpeakerPermission p : list) {
            if (p.getName() != null) {
                cache.put(p.getName(), p);
            }
        }
        log.info("已加载 {} 条用户权限到缓存", cache.size());
    }

    /**
     * 按用户名获取权限（优先缓存）
     */
    public SpeakerPermission getByName(String name) {
        return cache.get(name);
    }

    /**
     * 新增或更新单条缓存
     */
    public void put(SpeakerPermission permission) {
        if (permission != null && permission.getName() != null) {
            cache.put(permission.getName(), permission);
        }
    }

    /**
     * 按用户名移除缓存
     */
    public void evictByName(String name) {
        if (name != null) {
            cache.remove(name);
        }
    }
}
