package com.sysware.swcloud.feign;

import com.sysware.swcloud.domian.AjaxResultVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(
        name = "iotBuilder-xiandong",
        fallback = XadClientHystrix.class
)
public interface XadClient {
    @RequestMapping("/env/monitor/getEnvMonitor")
    public AjaxResultVO getEnvMonitor(@RequestParam Map<String, Object> inMap);

    /**
     * 查询生产信息月数据（假数据模式，忽略入参）。
     * 真实响应每条：{id, month(yyyyMM), sum_value(当月累计), d1~d31(每日旅客发送量)}
     * month按近7个月动态计算（当月及前6个月），按月份倒序返回
     */
    @RequestMapping("/stationScxx/list")
    public AjaxResultVO list(@RequestParam Map<String, Object> inMap) ;

}
