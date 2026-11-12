package com.sysware.swcloud.feign;

import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.scada.ScadaYkSendCommand;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Component
public class FepClientHystrix implements FepClient {

    @Override
    public AjaxResultVO sendYkYtCommand(@SpringQueryMap Map<String, Object> map) {
        return AjaxResultVO.error();
    }
}
