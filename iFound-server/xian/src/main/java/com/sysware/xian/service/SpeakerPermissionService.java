package com.sysware.xian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sysware.xian.mapper.SpeakerPermissionMapper;
import com.sysware.xian.domain.SpeakerPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakerPermissionService {

    private static final String STRANGER_ID = "-1";

    private final SpeakerPermissionMapper speakerPermissionMapper;
    private final CachedSpeakerPermissionService cachedSpeakerPermissionService;

    public List<SpeakerPermission> listAll() {
        LambdaQueryWrapper<SpeakerPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(SpeakerPermission::getId, STRANGER_ID);
        wrapper.orderByAsc(SpeakerPermission::getCreateTime);
        List<SpeakerPermission> users = speakerPermissionMapper.selectList(wrapper);

        SpeakerPermission stranger = speakerPermissionMapper.selectById(STRANGER_ID);
        if (stranger == null) {
            stranger = createDefaultStranger();
        }

        List<SpeakerPermission> result = new ArrayList<>();
        result.add(stranger);
        result.addAll(users);
        return result;
    }

    @Transactional
    public SpeakerPermission create(SpeakerPermission permission) {
        if (!StringUtils.hasText(permission.getName())) {
            throw new IllegalArgumentException("用户名称不能为空");
        }
        if (STRANGER_ID.equals(permission.getId())) {
            throw new IllegalArgumentException("不能创建陌生人权限");
        }

        // permission.setId(null);
        if (permission.getCanAwake() == null) {
            permission.setCanAwake(false);
        }
        if (permission.getCanYk() == null) {
            permission.setCanYk(false);
        }

        speakerPermissionMapper.insert(permission);
        cachedSpeakerPermissionService.put(permission);
        return permission;
    }

    @Transactional
    public SpeakerPermission update(String id, SpeakerPermission permission) {
        SpeakerPermission existing = speakerPermissionMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("权限对象不存在");
        }

        if (STRANGER_ID.equals(id)) {
            existing.setName(permission.getName());
            existing.setCanAwake(permission.getCanAwake());
            existing.setCanYk(permission.getCanYk());
        } else {
            if (!StringUtils.hasText(permission.getName())) {
                throw new IllegalArgumentException("用户名称不能为空");
            }
            existing.setName(permission.getName());
            existing.setOrgId(permission.getOrgId());
            existing.setCanAwake(permission.getCanAwake());
            existing.setCanYk(permission.getCanYk());
            existing.setEmbedding(permission.getEmbedding());
        }

        speakerPermissionMapper.updateById(existing);
        cachedSpeakerPermissionService.put(existing);
        return existing;
    }

    @Transactional
    public void delete(String id) {
        if (STRANGER_ID.equals(id)) {
            throw new IllegalArgumentException("不能删除陌生人权限");
        }

        SpeakerPermission existing = speakerPermissionMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("权限对象不存在");
        }

        speakerPermissionMapper.deleteById(id);
        cachedSpeakerPermissionService.evictByName(existing.getName());
    }

    private SpeakerPermission createDefaultStranger() {
        SpeakerPermission stranger = new SpeakerPermission();
        stranger.setId(STRANGER_ID);
        stranger.setName("陌生人");
        stranger.setCanAwake(false);
        stranger.setCanYk(false);
        stranger.setEmbedding("");
        speakerPermissionMapper.insert(stranger);
        return stranger;
    }

    /**
     * 从用户系统同步语音权限
     * 
     * @return 同步的用户数量
     */
    @Transactional
    public Map<String, Object> syncFromUsers(List<HashMap<String, Object>> users) {
        int addCount = 0;
        int updateCount = 0;
        for (HashMap<String, Object> user : users) {
            String id = String.valueOf(user.get("id"));
            String name = String.valueOf(user.get("name"));
            String org_id = String.valueOf(user.get("org_id"));

            if (!StringUtils.hasText(id) || "0".equals(id)) {
                continue;
            }

            SpeakerPermission existing = speakerPermissionMapper.selectById(id);
            if (existing != null) {
                // id存在，更新名称
                existing.setOrgId(org_id);
                existing.setName(name);
                speakerPermissionMapper.updateById(existing);
                updateCount++;
            } else {
                // id不存在，新增
                SpeakerPermission permission = new SpeakerPermission();
                permission.setId(id);
                permission.setOrgId(org_id);
                permission.setName(name);
                permission.setCanAwake(false);
                permission.setCanYk(false);
                permission.setEmbedding("");
                speakerPermissionMapper.insert(permission);
                addCount++;
            }
        }
        HashMap<String, Object> result = new HashMap<>();
        result.put("updateCount", updateCount);
        result.put("addCount", addCount);
        cachedSpeakerPermissionService.refreshAll();
        return result;
    }
}
