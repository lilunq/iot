package com.sysware.swcloud.feign;

import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.SwEnumEntity;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(
        name = "iotBuilder-admin",
        fallback = AdminClientHystrix.class
)
public interface AdminClient {
    @RequestMapping(value = "/listUserPage")
    public AjaxResultVO listUserPage(@RequestParam Map<String, Object> inMap);
    /**
     * 获取区域列表
     * @param parent_id
     * @param org_id
     * @param cascade_id
     * @return
     */
    @GetMapping(value="/subarea/list")
    public AjaxResultVO getSubareaList(@RequestParam Map<String, Object> inMap);
    /**
     * 通过指定的key获取对应字典数据集合
     * @param key 机电设备系统列表 device_system_type
     * @return
     */
    @GetMapping(value = "/enum/getEnumKeyValues")
    public List<SwEnumEntity> getEnumKeyValues(@RequestParam String key);
}

