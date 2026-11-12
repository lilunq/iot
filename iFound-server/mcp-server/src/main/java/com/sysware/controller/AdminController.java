package com.sysware.controller;

import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.SwEnumEntity;
import com.sysware.swcloud.feign.AdminClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * @Description: 用户模块相关接口
 * @Author: LiYanRu
 * @date: 2026-06-28
 **/
@RestController
@Tag(name = "管理接口", description = "admin相关接口")
@RequiredArgsConstructor
public class AdminController {
    private final AdminClient adminClient;

    /**
     * 获取区域列表
     *
     * @param inMap parent_id、org_id、cascade_id
     * @return 区域列表
     */
    @Operation(summary = "获取区域列表")
    @GetMapping("/subarea/list")
    public AjaxResultVO getSubareaList(@RequestParam Map<String, Object> inMap) {
        return adminClient.getSubareaList(inMap);
    }

    @Operation(summary = "获取用户列表", description = "可根据名称筛选")
    @GetMapping(value = "/listUserPage")
    public AjaxResultVO listUserPage(@RequestParam Map<String, Object> inMap){
        return adminClient.listUserPage(inMap);
    }
    /**
     * 通过指定的 key 获取对应字典数据集合
     *
     * @param key 机电设备系统列表 device_system_type
     * @return 字典数据集合
     */
    @Operation(summary = "获取枚举字典数据")
    @GetMapping("/admin/enum/getEnumKeyValues")
    public List<SwEnumEntity> getEnumKeyValues(@RequestParam String key) {
        return adminClient.getEnumKeyValues(key);
    }
}
