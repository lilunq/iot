package com.sysware.swcloud.feign;

import com.sysware.swcloud.domian.AjaxResultVO;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class XadClientHystrix implements XadClient{
    @Override
    public AjaxResultVO getEnvMonitor(Map<String, Object> inMap) {
        return AjaxResultVO.error("查询失败");
    }

    @Override
    public AjaxResultVO list(Map<String, Object> inMap) {
        return AjaxResultVO.error("查询失败");
    }
}
