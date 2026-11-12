package com.sysware.xian.swcloud.fegin;

import com.sysware.xian.swcloud.domain.AjaxResultVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 视频平台摄像头目录接口。 */
@FeignClient(
        name = "iotBuilder-video",
        fallback = VideoClientHystrix.class
)
public interface VideoClient {

    @GetMapping("/camera/listPage")
    AjaxResultVO listPage(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "subarea_cid", required = false) String subareaCid,
            @RequestParam("page") String page,
            @RequestParam("limit") String limit);
}
