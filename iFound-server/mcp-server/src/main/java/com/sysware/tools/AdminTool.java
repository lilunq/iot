package com.sysware.tools;

import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.R;
import com.sysware.swcloud.domian.SwEnumEntity;
import com.sysware.swcloud.feign.AdminClient;
import com.sysware.swcloud.feign.FepClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AdminTool {

    @Autowired
    private AdminClient adminClient;


    @Tool(name = "getSubarea", description = "获取区域列表")
    public R<List<?>> getSubarea() {
        System.out.println("mcp 调用获取区域列表");
        Map<String, Object> inMap = new HashMap<>();
        inMap.put("org_id",3);
        return R.success(adminClient.getSubareaList(inMap).getData());
    }

    /**
     * bDeviceSystem
     */
    @Tool(name = "getAllDeviceSystem", description = "获取设备的系统列表")
    public Map<String, String> bDeviceSystem() {
        return adminClient.getEnumKeyValues("device_system_type").stream()
                .collect(Collectors.toMap(SwEnumEntity::getElement_key, SwEnumEntity::getElement_value));
    }
}
