package com.sysware.controller;

import com.sysware.core.report.ReportService;
import com.sysware.core.report.domain.ReportDataVO;
import com.sysware.service.DashboardService;
import com.sysware.swcloud.domian.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;

@RestController
@Tag(name = "AI面板接口", description = "聚合接口")
@RequiredArgsConstructor
public class DashboardController {
    /**
     * AI 面板聚合业务服务。
     */
    private final DashboardService dashboardService;

    private final ReportService reportService;

    /**
     * 获取报表聚合数据。
     *
     * @return 报表聚合数据（设备健康率/区域态势/故障设备/严重事件/实时客流/累计进出站客流/拥堵区域，含各模块统计总数）
     */
    @Operation(summary = "报表数据", description = "后端并发聚合 8 路接口数据：设备健康率、区域态势、故障设备、严重事件、实时客流、当前安检人数、累计进出站客流、拥堵区域，每个模块均附带统计总数")
    @GetMapping("/getReportData")
    public R<ReportDataVO> getReportData() {
        ReportDataVO report = reportService.getReportData();
        return R.success(report);
    }

    /**
     * 获取组织下所有区域态势。
     *
     * @param orgId      组织 ID
     * @param subareaCid 区域级联 ID
     * @return 区域态势列表
     */
    @Operation(summary = "获取组织下所有区域态势(实时的设备/环境/能耗/客流)")
    @GetMapping("/subareaState")
    public R<List<HashMap<String, Object>>> getSubareaState(
            @RequestParam(value = "orgId", defaultValue = "3", required = false) String orgId,
            @RequestParam(value = "subareaCid", required = false) String subareaCid) {
        List<HashMap<String, Object>> state = dashboardService.getSubareaState(orgId, subareaCid);
        return R.success(state);
    }

    /**
     * 获取来自iot的异常环境数据。
     *
     * @param orgId       组织 ID
     * @param subareaCid  区域级联 ID
     * @param alarm_state 告警状态
     * @return 异常环境数据列表
     */
//    @Operation(summary = "获取异常环境数据")
//    @GetMapping("/envAlter")
//    public R<List<HashMap<String, Object>>> getEnvAlter(
//            @RequestParam(value = "org_id", defaultValue = "3", required = false) String orgId,
//            @RequestParam(value = "subarea_cid", required = false) String subareaCid,
//            @RequestParam(value = "alarm_state", required = false) String alarm_state) {
//        List<HashMap<String, Object>> envAlter = dashboardService.getEnvAlter(orgId, subareaCid, alarm_state);
//        return R.success(envAlter);
//    }
}
