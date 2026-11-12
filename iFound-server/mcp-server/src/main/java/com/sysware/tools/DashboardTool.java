package com.sysware.tools;

import com.sysware.service.DashboardService;
import com.sysware.swcloud.common.core.vo.Res;
import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.ifim.CrowdedAreaVO;
import com.sysware.swcloud.domian.ifim.StationRealtimeVO;
import com.sysware.swcloud.feign.VideoAnalyticsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 面板聚合工具。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardTool {

    /** 区域态势 + 客流并发查询专用线程池（3 路并行） */
    private static final ExecutorService DASHBOARD_EXECUTOR = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "dashboard-fetch");
        t.setDaemon(true);
        return t;
    });

    private final DashboardService dashboardService;
    private final VideoAnalyticsClient videoAnalyticsClient;

    /**
     * 获取组织下所有区域态势（客流情况仅在未指定区域时返回）。
     * <p>
     * 并发查询区域态势（设备/环境/能耗）和车站客流（实时在站人数/拥堵区域），
     * 返回 data 结构包含两个模块：
     * <ul>
     *   <li>subareaState：区域态势列表，每条含 subarea_name、subarea_cid、subarea_alter、
     *       deviceStateCount（total/fault_total/alarm_total）、
     *       environment（totalDevices环境设备总数/abnormalDevices环境异常数，来自AI每小时分析落库）、
     *       energy（status/message/value）</li>
     *   <li>passengerFlow：客流情况（仅未传 subareaCid 时返回，station-level 数据与区域无关）
     *     <ul>
     *       <li>stationRealtime：车站实时客流概览（stationCurrPeo 在站总人数、groupItems[] 各分组区域人数）</li>
     *       <li>crowdedAreas：拥堵区域列表（areaCode/areaName/currPeo/peopleNumThreshold/congestion）</li>
     *       <li>crowdedAreaCount：拥堵区域数量</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @return AjaxResultVO，data 为 { subareaState, passengerFlow } 结构；
     *         传 subareaCid 时 data 仅包含 subareaState 模块
     */
    @Tool(name = "getSubareaState", description = "获取组织下所有区域态势。"
            + "① subareaState — 每个区域含：deviceStateCount（设备总数/故障数/报警数）、environment（环境设备总数/异常数，来自AI每小时分析落库）、energy（能耗状态）、subarea_alter（区域是否有异常 0/1）；"
            + "② passengerFlow — 车站实时客流概览（在站总人数、各分组区域当前人数）和拥堵区域列表（区域名称/当前人数/拥挤度）。"
            + "注意：passengerFlow 为车站级数据，仅在未传 subareaCid（查询全站/多区域）时返回；传 subareaCid 查询单个区域时 data 只包含 subareaState 模块，不含客流数据")
    public AjaxResultVO getSubareaState(
            @ToolParam(description = "组织ID，默认3（西安东站）", required = false) String orgId,
            @ToolParam(description = "区域级联ID，不传时查询组织下全部区域并附带车站客流数据；传值时只查询该区域态势（无客流数据），示例0.001.001.001-0", required = false) String subareaCid) {
        String finalOrgId = defaultOrgId(orgId);
        // 客流为车站级数据，与具体区域无关：指定区域查询时无需请求，减少无效调用
        boolean includePassengerFlow = subareaCid == null || subareaCid.isBlank();
        log.debug("mcp 调用获取区域态势: orgId={}, subareaCid={}, includePassengerFlow={}",
                finalOrgId, subareaCid, includePassengerFlow);

        // 1. 并发查询：区域态势（固定）+ 车站实时客流 + 拥堵区域（指定区域时不请求客流）
        CompletableFuture<List<HashMap<String, Object>>> subareaFuture = CompletableFuture.supplyAsync(
                () -> dashboardService.getSubareaState(finalOrgId, subareaCid), DASHBOARD_EXECUTOR);
        CompletableFuture<Res<StationRealtimeVO>> stationFuture = includePassengerFlow
                ? CompletableFuture.supplyAsync(() -> videoAnalyticsClient.getStationRealtime(), DASHBOARD_EXECUTOR)
                : null;
        CompletableFuture<Res<List<CrowdedAreaVO>>> crowdedFuture = includePassengerFlow
                ? CompletableFuture.supplyAsync(() -> videoAnalyticsClient.getCrowdedAreas(), DASHBOARD_EXECUTOR)
                : null;

        if (includePassengerFlow) {
            CompletableFuture.allOf(subareaFuture, stationFuture, crowdedFuture).join();
        } else {
            subareaFuture.join();
        }

        // 2. 组装区域态势列表
        List<HashMap<String, Object>> subareaState = subareaFuture.join();
        if (subareaState == null) {
            subareaState = Collections.emptyList();
        }

        // 3. 组装返回数据：subareaState（固定）+ passengerFlow（仅全站查询时）
        Map<String, Object> data = new HashMap<>();
        data.put("subareaState", subareaState);

        if (includePassengerFlow) {
            Res<StationRealtimeVO> stationRes = stationFuture.join();
            Res<List<CrowdedAreaVO>> crowdedRes = crowdedFuture.join();

            Map<String, Object> passengerFlow = new HashMap<>();
            // 3.1 车站实时客流概览（在站总人数 + 各分组区域人数）
            StationRealtimeVO stationRealtime =
                    stationRes != null ? stationRes.getData() : null;
            passengerFlow.put("stationRealtime", stationRealtime);
            // 3.2 拥堵区域列表
            List<CrowdedAreaVO> crowdedAreas =
                    crowdedRes != null && crowdedRes.getData() != null
                            ? new ArrayList<>(crowdedRes.getData()) : Collections.emptyList();
            passengerFlow.put("crowdedAreas", crowdedAreas);
            passengerFlow.put("crowdedAreaCount", crowdedAreas.size());

            data.put("passengerFlow", passengerFlow);
        }

        return AjaxResultVO.ok().data(data);
    }

//    @Tool(name = "getEnvAlter", description = "获取异常环境数据，可按组织、区域和告警状态筛选")
    public List<HashMap<String, Object>> getEnvAlter(
            @ToolParam(description = "组织ID，默认3（西安东站）", required = false) String orgId,
            @ToolParam(description = "区域级联ID，不传时查询组织下全部区域，示例0.001.001.001-0", required = false) String subareaCid,
            @ToolParam(description = "告警状态，1表示异常，不传时不按告警状态过滤", required = false) String alarmState) {
        String finalOrgId = defaultOrgId(orgId);
        log.debug("mcp 调用获取异常环境数据: orgId={}, subareaCid={}, alarmState={}", finalOrgId, subareaCid, alarmState);
        return dashboardService.getEnvAlter(finalOrgId, subareaCid, alarmState);
    }

    private String defaultOrgId(String orgId) {
        if (orgId == null || orgId.isBlank()) {
            return "3";
        }
        return orgId;
    }
}
