package com.sysware.swcloud.feign;

import org.springframework.stereotype.Component;

@Component
public class TdengineClientHystrix implements TdengineClient {
    @Override
    public String rtu() {
        return "fegin 调用失败";
    }
}
