package com.sysware.swcloud.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/** 调用 xian-ai 中的统一业务实体解析 API。 */
@FeignClient(name = "xian-ai", fallback = EntityCenterClientFallback.class)
public interface EntityCenterClient {
    @PostMapping("/entity-center/resolve")
    Map<String, Object> resolve(@RequestBody Map<String, Object> request);
}
