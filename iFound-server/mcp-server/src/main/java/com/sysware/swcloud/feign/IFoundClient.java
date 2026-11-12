package com.sysware.swcloud.feign;

import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.DispatchRequest;
import com.sysware.swcloud.domian.EmergencyPlanScreenResult;
import com.sysware.swcloud.domian.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * iFound AI平台 Feign客户端
 * 对接 xian-ai 模块的接口（环境分析、站房评分等）
 */
@FeignClient(
        name = "xian-ai",
        fallback = IFoundClientHystrix.class
)
public interface IFoundClient {

    /**
     * 查询最新一条环境分析结果
     * 从数据库查询由定时任务每小时落库的环境分析记录，返回各区域的分析结果。
     *
     * @param orgId 组织ID，默认3（西安东站）
     * @return AjaxResultVO，data 含 status/results/lastAnalysisTime；无记录时 status=no_data
     */
    @GetMapping("/env/analyze/result")
    AjaxResultVO getEnvAnalysisResult(@RequestParam(value = "org_id", defaultValue = "3") String orgId);

    /**
     * 查询各区域环境设备统计（总数/异常数）
     * 从最新一条环境分析记录中提取各区域的设备统计，供区域态势聚合使用。
     *
     * @param orgId 组织ID，默认3（西安东站）
     * @return AjaxResultVO，data 含 status="ready" 和 areas 列表（每条含 subareaCid、subareaName、totalDevices、abnormalDevices）
     */
    @GetMapping("/env/analyze/stats")
    AjaxResultVO getEnvAnalysisStats(@RequestParam(value = "org_id", defaultValue = "3") String orgId);

    /**
     * 大屏应急预案（异步接口）
     * 先查缓存，命中则直接返回data；未命中则异步执行AI分析，立即返回data=null（msg=AI正在分析中），
     * 结果落库后通过再次调用查询。
     *
     * @param request 应急预案请求（question/operatorName/operatorId/type）
     * @return R，data 为 EmergencyPlanScreenResult（缓存命中时），data=null 表示AI正在分析中
     */
    @PostMapping("/knowledge/emergency-plan-screen")
    R<EmergencyPlanScreenResult> emergencyPlanScreen(@RequestBody DispatchRequest request);
}
