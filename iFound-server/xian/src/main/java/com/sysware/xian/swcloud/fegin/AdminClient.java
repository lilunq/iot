package com.sysware.xian.swcloud.fegin;


import com.sysware.xian.swcloud.domain.AjaxResultVO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(
        name = "iotBuilder-admin",
        fallback = AdminClientHystrix.class
)
public interface AdminClient {
    /**
     * 获取区域列表
     * @param parent_id
     * @param org_id
     * @param cascade_id
     * @return
     */
    @GetMapping(value="/subarea/list")
    public AjaxResultVO getSubareaList(@RequestParam Map<String, Object> inMap);
    /** 获取设备系统字典，返回 element_key / element_value 等字段。 */
    @GetMapping(value = "/enum/getEnumKeyValues")
    public List<Map<String, Object>> getEnumKeyValues(@RequestParam("key") String key);

    @GetMapping(value = "/listUserPage")
    public AjaxResultVO listUserPage(@RequestParam Map<String, Object> inMap);
}

