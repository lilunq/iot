package com.sysware.xian.controller;

import com.sysware.xian.domain.ZoneConfig;
import com.sysware.xian.service.ZoneConfigService;
import com.sysware.xian.swcloud.fegin.AdminClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/zoneConfig")
@Tag(name = "分区配置管理", description = "分区配置的增删改查")
public class ZoneConfigController {
    
    @Autowired
    private ZoneConfigService zoneConfigService;
    @Autowired
    private AdminClient adminClient;
    
    @GetMapping("/list")
    @Operation(summary = "获取分区列表")
    public Map<String, Object> list(@RequestParam(defaultValue = "3") String orgId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<ZoneConfig> list = zoneConfigService.getByOrgId(orgId);
            result.put("code", "200");
            result.put("msg", "成功");
            result.put("data", list);
        } catch (Exception e) {
            result.put("code", "500");
            result.put("msg", "失败: " + e.getMessage());
        }
        return result;
    }
    
    @GetMapping("/get")
    @Operation(summary = "获取分区详情")
    public Map<String, Object> get(@RequestParam String id) {
        Map<String, Object> result = new HashMap<>();
        try {
            ZoneConfig zone = zoneConfigService.getById(id);
            result.put("code", "200");
            result.put("msg", "成功");
            result.put("data", zone);
        } catch (Exception e) {
            result.put("code", "500");
            result.put("msg", "失败: " + e.getMessage());
        }
        return result;
    }
    
    @PostMapping("/add")
    @Operation(summary = "新增分区")
    public Map<String, Object> add(@RequestBody ZoneConfig zoneConfig) {
        Map<String, Object> result = new HashMap<>();
        try {
            zoneConfigService.add(zoneConfig);
            result.put("code", "200");
            result.put("msg", "新增成功");
        } catch (Exception e) {
            result.put("code", "500");
            result.put("msg", "失败: " + e.getMessage());
        }
        return result;
    }
    
    @PostMapping("/update")
    @Operation(summary = "更新分区")
    public Map<String, Object> update(@RequestBody ZoneConfig zoneConfig) {
        Map<String, Object> result = new HashMap<>();
        try {
            zoneConfigService.update(zoneConfig);
            result.put("code", "200");
            result.put("msg", "更新成功");
        } catch (Exception e) {
            result.put("code", "500");
            result.put("msg", "失败: " + e.getMessage());
        }
        return result;
    }
    
    @PostMapping("/delete")
    @Operation(summary = "删除分区")
    public Map<String, Object> delete(@RequestParam Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            zoneConfigService.delete(id);
            result.put("code", "200");
            result.put("msg", "删除成功");
        } catch (Exception e) {
            result.put("code", "500");
            result.put("msg", "失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/sync")
    @Operation(summary = "一键同步")
    public Map<String, Object> sync(@RequestParam(defaultValue = "3") String orgId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<HashMap<String, Object>> list = adminClient.getSubareaList(new HashMap<>(){{
                put("org_id", orgId);
            }}).getData();
            
            if (list == null || list.isEmpty()) {
                result.put("code", "500");
                result.put("msg", "未获取到分区数据");
                return result;
            }
            
            int addCount = 0;
            int updateCount = 0;
            
            for (HashMap<String, Object> item : list) {
                ZoneConfig existing = zoneConfigService.getById(item.get("id").toString());
                
                ZoneConfig zone = new ZoneConfig();
                zone.setId(item.get("id").toString());
                zone.setOrgId(item.get("org_id") != null ? item.get("org_id").toString() : orgId);
                zone.setParentId(item.get("parent_id") != null ? item.get("parent_id").toString() : "");
                zone.setName(item.get("name") != null ? item.get("name").toString() : "");
                zone.setCascadeId(item.get("cascade_id") != null ? item.get("cascade_id").toString() : "");
                zone.setSortOrder(item.get("sort_no") != null ? Integer.valueOf(item.get("sort_no").toString()) : 0);
                zone.setRemark(item.get("remark") != null ? item.get("remark").toString() : "");
                zone.setStatus(1);
                
                if (existing == null) {
                    zoneConfigService.add(zone);
                    addCount++;
                } else {
                    zone.setCoordX(existing.getCoordX());
                    zone.setCoordY(existing.getCoordY());
                    zone.setCoordZ(existing.getCoordZ());
                    zoneConfigService.update(zone);
                    updateCount++;
                }
            }
            
            result.put("code", "200");
            result.put("msg", "同步成功");
            result.put("data", Map.of("addCount", addCount, "updateCount", updateCount));
        } catch (Exception e) {
            result.put("code", "500");
            result.put("msg", "同步失败: " + e.getMessage());
        }
        return result;
    }
}
