package com.sysware.swcloud.feign;

import com.sysware.swcloud.domian.AjaxResultVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(
        name = "iotBuilder-video",
        fallback = VideoClientHystrix.class
)
public interface VideoClient {
    /**
     * 获取token
     * @param inMap
     * @return {
     *     "Status": "Ok",
     *     "msg": "操作成功",
     *     "code": "200",
     *     "data": "24PPW0vl6ejbjDwIoxqrWFky9LEWk57cRQ6gdEfg6Bi.oxNzc5MTcxNDY1LCJwIjoiOTU2ZTdmMjE2ODlkY2MxYmU0M2M2ZGVhYmNjMTZmNTJiYjYyYTZhMDRkNWVhMjEyZmQ1OWJkYWJmMDVkM2RkZiIsInQiOjE3Nzg1NjY2NjUsInUiOiI5MTNjNzc3ODMwIn0eyJlIj.GciOiJIUzI1NiIsInR5cCI6IkpXVCJ9eyJhb"
     * }
     */
    @GetMapping("nvr/getToken")
    public AjaxResultVO getToken(@RequestParam Map<String, Object> inMap) ;

    /**
     * 摄像头查询
     * @param inMap
     * @return {
     *     "Status": "Ok",
     *     "msg": "操作成功",
     *     "code": "200",
     *     "data": [
     *         {
     *             "address": "Address",
     *             "camera_no": "34020000001320000532",
     *             "lastSaveTime": "20260512132003",
     *             "main_channel": 1,
     *             "sort_no": 0,
     *             "param": "{\"param_ptz\":\"0\",\"param_focusing\":\"0\",\"param_voice\":\"0\",\"param_wiper\":\"0\",\"param_cruise\":\"0\",\"param_light\":\"0\",\"param_preset\":\"0\",\"param_aperture\":\"0\"}",
     *             "org_id": "0",
     *             "scada_no": 1,
     *             "name": "实验室",
     *             "nvr_id": "1238805016148447232",
     *             "id": "1238805390712377344",
     *             "Online": true,
     *             "sub_channel": 0
     *         }
     *     ]
     * }
     */
    /**
     * 分页查询摄像头列表
     * @param name 摄像头名称（模糊查询）
     * @param page 页码
     * @param limit 分页大小
     * @return 摄像头列表
     */
    @GetMapping("/camera/listPage")
    AjaxResultVO listPage(@RequestParam(value = "name",required = false) String name,
                          @RequestParam("page") String page,
                          @RequestParam("limit") String limit);
}

