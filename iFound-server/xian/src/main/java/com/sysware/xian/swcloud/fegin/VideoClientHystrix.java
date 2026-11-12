package com.sysware.xian.swcloud.fegin;

import com.sysware.xian.swcloud.domain.AjaxResultVO;
import org.springframework.stereotype.Component;

@Component
public class VideoClientHystrix implements VideoClient {
    @Override
    public AjaxResultVO listPage(String name, String subareaCid, String page, String limit) {
        return AjaxResultVO.error("视频平台摄像头列表服务暂不可用");
    }
}
