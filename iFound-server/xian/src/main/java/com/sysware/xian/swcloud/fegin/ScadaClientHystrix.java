package com.sysware.xian.swcloud.fegin;

import com.sysware.xian.swcloud.domain.AjaxResultVO;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Component
public class ScadaClientHystrix implements ScadaClient {
    @Override
    public AjaxResultVO listPage(@RequestParam Map<String, Object> inMap) {
        return AjaxResultVO.error("SCADA 设备列表服务暂不可用");
    }

    @Override
    public AjaxResultVO listDeviceTypes() {
        return AjaxResultVO.error("SCADA 设备分类服务暂不可用");
    }
}
