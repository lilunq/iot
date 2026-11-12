package com.sysware.xian.controller;

import com.sysware.xian.domain.SpeakerPermission;
import com.sysware.xian.service.SpeakerPermissionService;
import com.sysware.xian.swcloud.fegin.AdminClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/speaker-permissions")
@RequiredArgsConstructor
@Tag(name = "语音权限管理接口", description = "语音权限增删改查")
public class SpeakerPermissionController {

    private final SpeakerPermissionService speakerPermissionService;
    @Autowired
    private AdminClient adminClient;

    //一键同步
    @PostMapping("/sync")
    @Operation(summary = "一键同步用户语音权限", description = "从用户系统同步用户数据到语音权限表")
    public Map<String, Object> sync() {
        List<HashMap<String, Object>> data = adminClient.listUserPage(new HashMap<>() {{
            put("limit", "100");
        }}).getData();
        Map<String, Object> count = speakerPermissionService.syncFromUsers(data);

        return Map.of(
            "code", 200,
            "message", "同步成功",
            "data", count
        );
    }

    @GetMapping
    @Operation(summary = "查询所有语音权限", description = "返回数组中第一个元素固定为陌生人权限")
    public Map<String, Object> listAll() {
        List<SpeakerPermission> list = speakerPermissionService.listAll();
        return Map.of(
            "code", 200,
            "message", "success",
            "data", list
        );
    }

    @PostMapping
    @Operation(summary = "创建用户语音权限", description = "只能创建用户，不能创建陌生人")
    public Map<String, Object> create(@RequestBody SpeakerPermission permission) {
        SpeakerPermission result = speakerPermissionService.create(permission);
        return Map.of(
            "code", 200,
            "message", "创建成功",
            "data", result
        );
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改语音权限", description = "可修改陌生人或用户权限")
    public Map<String, Object> update(@PathVariable String id, @RequestBody SpeakerPermission permission) {
        SpeakerPermission result = speakerPermissionService.update(id, permission);
        return Map.of(
            "code", 200,
            "message", "修改成功",
            "data", result
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户语音权限", description = "只能删除用户，不能删除陌生人")
    public Map<String, Object> delete(@PathVariable String id) {
        speakerPermissionService.delete(id);
        return Map.of(
            "code", 200,
            "message", "删除成功"
        );
    }
}
