package com.sysware.xian.swcloud.fegin;

import com.sysware.xian.swcloud.domain.AjaxResultVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/** 机电设备 SCADA 数据源接口。 */
@FeignClient(
        name = "iotBuilder-scada-server",
        fallback = ScadaClientHystrix.class
)
public interface ScadaClient {

    /** 分页查询设备，支持 org_id、device_name、device_alias、subarea_cid 等条件。 */
    @RequestMapping("/base/device/listPage")
    AjaxResultVO listPage(@RequestParam Map<String, Object> inMap);

    /** 获取设备分类树。 */
    @RequestMapping("/base/device/type/list")
    AjaxResultVO listDeviceTypes();
}
