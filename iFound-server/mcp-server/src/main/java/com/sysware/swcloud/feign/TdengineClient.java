package com.sysware.swcloud.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(
        name = "iotBuilder-tdengine",
        fallback = TdengineClientHystrix.class
)
public interface TdengineClient {
    // 查询场景
    @GetMapping("/out/rtu")
    public String rtu();

}
