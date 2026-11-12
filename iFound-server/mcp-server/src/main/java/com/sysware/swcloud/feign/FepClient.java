package com.sysware.swcloud.feign;

import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.scada.ScadaYkSendCommand;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(
        name = "iotBuilder-scada-fep",
        fallback = FepClientHystrix.class
)
public interface FepClient {
    /**
     * 发送遥控遥调命令
     *
     * @param packageType 遥控类型值：遥信是1004，遥测是1026
     * @param value       控制值
     * @param key         数据key
     * @param userId      执行操作的人员id，如果是后台服务自动执行，则id为-1
     * @param ykType      遥控类型：0=带返校的控制，1=DO，2=BV, 0xFF: 模式引擎下发遥控
     * @return
     */
    @PostMapping("/ykyt/sendCommand")
    public AjaxResultVO sendYkYtCommand(@SpringQueryMap Map<String,Object> map);
}
