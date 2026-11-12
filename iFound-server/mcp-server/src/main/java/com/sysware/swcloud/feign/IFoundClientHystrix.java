package com.sysware.swcloud.feign;

import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.DispatchRequest;
import com.sysware.swcloud.domian.EmergencyPlanScreenResult;
import com.sysware.swcloud.domian.R;
import org.springframework.stereotype.Component;

/**
 * IFoundClient 降级处理
 */
@Component
public class IFoundClientHystrix implements IFoundClient {

    @Override
    public AjaxResultVO getEnvAnalysisResult(String orgId) {
        return AjaxResultVO.error("查询环境分析结果失败（服务降级）");
    }

    @Override
    public AjaxResultVO getEnvAnalysisStats(String orgId) {
        return AjaxResultVO.error("查询环境分析统计失败（服务降级）");
    }

    @Override
    public R<EmergencyPlanScreenResult> emergencyPlanScreen(DispatchRequest request) {
        return R.error("查询大屏应急预案失败（服务降级）");
    }
}
