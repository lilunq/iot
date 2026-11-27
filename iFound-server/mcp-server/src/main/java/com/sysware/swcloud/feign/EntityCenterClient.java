package com.sysware.swcloud.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/** 调用 xian-ai 中的统一业务实体解析 API。 */
@FeignClient(name = "xian-ai", contextId = "entityCenterClient", fallback = EntityCenterClientFallback.class)
public interface EntityCenterClient {
    @PostMapping("/entity-center/resolve")
    Map<String, Object> resolve(@RequestBody Map<String, Object> request);

    /** 根据完整自然语言检索设备、摄像头、区域和系统实体候选。 */
    @PostMapping("/entity-center/assistant/search")
    Map<String, Object> searchAssistant(@RequestBody Map<String, Object> request);
}
