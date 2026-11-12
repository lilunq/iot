package com.sysware.swcloud.feign;

import com.sysware.swcloud.domian.AjaxResultVO;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Component
public class VideoClientHystrix implements VideoClient {
    @Override
    public AjaxResultVO getToken(@RequestParam Map<String, Object> inMap) {return new AjaxResultVO();};

    @Override
    public AjaxResultVO listPage(String name, String page, String limit) {
        return AjaxResultVO.error();
    }
}
