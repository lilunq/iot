package com.sysware.xian.controller;

import com.sysware.xian.domain.ReportGenerateRequest;
import com.sysware.xian.domain.TunnelRunScore;
import com.sysware.xian.domain.ZoneConfig;
import com.sysware.xian.service.DashboardService;
import com.sysware.xian.service.EnvAnalysisService;
import com.sysware.xian.service.ReportService;
import com.sysware.xian.service.ZoneConfigService;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import com.sysware.xian.swcloud.fegin.AdminClient;
import com.sysware.xian.swcloud.fegin.MCPXClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@Tag(name = "综合接口", description = "站房运行评分、分区态势等")
public class DashboardController {
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private AdminClient adminClient;
    @Autowired
    private MCPXClient mcpClient;
    @Autowired
    private ZoneConfigService zoneConfigService;
    @Autowired
    private ReportService reportService;
    @Autowired
    private EnvAnalysisService envAnalysisService;

    @Operation(summary = "获取区域", description = "可根据名称筛选")
    @GetMapping("/subarea/list")
    public AjaxResultVO getSubarea(@RequestParam(name = "org_id", required = false,defaultValue = "3") String org_id) {
        Map<String,Object> inMap=new HashMap<>(){{
            put("org_id",org_id);
        }};
        return adminClient.getSubareaList(inMap);
    }

    @Operation(summary = "获取组织下所有区域态势(实时的设备/环境/能耗/客流)")
    @GetMapping("/subareaState")
    public AjaxResultVO getSubareaState(
            @RequestParam(value = "org_id",defaultValue = "3",required = false) String orgId,
            @RequestParam(value = "subarea_cid",required = false) String subareaCid) {
        List<HashMap<String, Object>> data = mcpClient.getSubareaState(orgId, subareaCid).getData();

        return AjaxResultVO.ok().data(data.stream().map(item -> {
            ZoneConfig zone = zoneConfigService.getByCascadeId(item.get("subarea_cid").toString());
            if (zone != null) {
                item.put("subarea_coord", zone.getCoordX() + "," + zone.getCoordY() + "," + zone.getCoordZ());
            }
            return item;
        }).toList());
    }

    @Operation(summary = "获取站房整体运行评分（实时）")
    @GetMapping("getTunnelRunScore")
    public AjaxResultVO getTunnelRunScore(
            @RequestParam(value = "org_id", required = false, defaultValue = "3") String orgId
    ) {
        return AjaxResultVO.ok().data(dashboardService.getTunnelRunScore(orgId));
    }

    @Operation(summary = "查询历史评分数据")
    @GetMapping("getHistoryScore")
    public AjaxResultVO getHistoryScore(
            @RequestParam(value = "org_id", required = false, defaultValue = "3") String orgId,
            @RequestParam(value = "start_time", required = false) String startTime,
            @RequestParam(value = "end_time", required = false) String endTime
    ) {
        List<TunnelRunScore> history = dashboardService.getHistoryScore(orgId, startTime, endTime);
        return AjaxResultVO.ok().data(history);
    }

    @Operation(summary = "查询最新历史评分数据")
    @GetMapping("getLatestScore")
    public AjaxResultVO getLatestScore(
            @RequestParam(value = "org_id", required = false, defaultValue = "3") String orgId
    ) {
        TunnelRunScore latest = dashboardService.getLatestScore(orgId);
        return AjaxResultVO.ok().data(latest);
    }

    @Operation(summary = "实时生成报表并落库")
    @PostMapping("/report/generate")
    public AjaxResultVO generateReport(@RequestBody ReportGenerateRequest request) {
        String input = request.getInput();
        if (input == null || input.isBlank()) {
            return AjaxResultVO.error("input 不能为空");
        }
        String result = reportService.reportChat(input);
        reportService.saveReportRecord(result);
        return AjaxResultVO.ok().data(result);
    }

    @Operation(summary = "获取最新一次已落库的运行报告 Markdown 正文")
    @GetMapping("/report/latest")
    public AjaxResultVO getLatestReport() {
        String content = reportService.getLatestReportContent();
        if (content == null || content.isBlank()) {
            return AjaxResultVO.ok().data("");
        }
        return AjaxResultVO.ok().data(content);
    }

    @Operation(summary = "环境分析", description = "获取所有环境设备数据，按国家/行业标准分析评分，输出不合规设备及整改建议")
    @GetMapping("/env/analyze")
    public AjaxResultVO analyzeEnv(
            @RequestParam(value = "org_id", defaultValue = "3") String orgId,
            @RequestParam(value = "subarea_cid", required = false) String subareaCid) {
        String result = envAnalysisService.analyzeEnv(orgId, subareaCid);
        return AjaxResultVO.ok().data(result);
    }

    @Operation(summary = "环境分析结果", description = "从数据库查询最新一条环境分析记录（由定时任务每小时落库），返回各区域的分析结果。若无记录则返回no_data状态")
    @GetMapping("/env/analyze/result")
    public AjaxResultVO getEnvAnalysisResult(
            @RequestParam(value = "org_id", defaultValue = "3") String orgId) {
        Map<String, Object> result = envAnalysisService.getCachedResult(orgId);
        if (result != null) {
            return AjaxResultVO.ok().data(result);
        }
        return AjaxResultVO.ok().data(Map.of("status", "no_data"));
    }

    @Operation(summary = "环境分析设备统计", description = "从最新一条环境分析记录中提取各区域的设备统计（区域cid、设备总数、异常数），供区域态势聚合使用")
    @GetMapping("/env/analyze/stats")
    public AjaxResultVO getEnvAnalysisStats(
            @RequestParam(value = "org_id", defaultValue = "3") String orgId) {
        List<Map<String, Object>> areas = envAnalysisService.getEnvAnalysisStats();
        return AjaxResultVO.ok().data(Map.of("status", "ready", "areas", areas));
    }

}
