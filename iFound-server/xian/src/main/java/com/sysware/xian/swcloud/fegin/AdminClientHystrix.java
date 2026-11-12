package com.sysware.xian.swcloud.fegin;

import com.sysware.xian.swcloud.domain.AjaxResultVO;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AdminClientHystrix implements AdminClient {
    @Override
    @GetMapping(value="/subarea/list")
    public AjaxResultVO getSubareaList(@RequestParam Map<String, Object> inMap){return AjaxResultVO.error();};

    @Override
    @GetMapping(value = "/listUserPage")
    public AjaxResultVO listUserPage(@RequestParam Map<String, Object> inMap){return AjaxResultVO.error();};
}