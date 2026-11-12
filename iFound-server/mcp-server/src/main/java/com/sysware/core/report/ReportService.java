package com.sysware.core.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.core.report.domain.ReportDataVO;
import com.sysware.core.report.domain.ReportDeviceEvent;
import com.sysware.core.report.domain.ReportDeviceHealthRate;
import com.sysware.core.report.domain.ReportDeviceState;
import com.sysware.service.DashboardService;
import com.sysware.service.DeviceService;
import com.sysware.swcloud.common.core.vo.Res;
import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.ifim.CrowdedAreaVO;
import com.sysware.swcloud.domian.ifim.SecurityCheckCurrentVO;
import com.sysware.swcloud.domian.ifim.StationInOutTotalVO;
import com.sysware.swcloud.domian.ifim.StationRealtimeVO;
import com.sysware.swcloud.domian.scada.ScadaPageQueryDto;
import com.sysware.swcloud.feign.ScadaClient;
import com.sysware.swcloud.feign.VideoAnalyticsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 报表请求工具，由后端一次性并发调用多个接口并整合，避免让 AI 串行调用多次。
 *
 * @Author: LiYanRu
 * @date: 2026-07-21
 **/
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {
    private static final DateTimeFormatter SCADA_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 累计进出站客流统计时间格式 */
    private static final DateTimeFormatter IN_OUT_TOTAL_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 报表 4 路远程查询专用线程池（固定 4 路并行） */
    private static final ExecutorService REPORT_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "report-fetch");
        t.setDaemon(true);
        return t;
    });

    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;
    private final DashboardService dashboardService;
    private final ScadaClient scadaClient;
    private final VideoAnalyticsClient videoAnalyticsClient;

    /**
     * 获取报表数据（8 路接口并发请求，再精简合并）
     *
     * @return 整合并精简后的报表数据（含各模块统计总数）
     */
    public ReportDataVO getReportData() {
        // 1. 准备与远程调用无关的入参
        String startTime = LocalDateTime.now().minusDays(1).format(SCADA_TIME_FMT);
        // 累计进出站客流统计区间：今日 00:00:00 至当前
        String inOutStartTime = LocalDateTime.now()
                .withHour(0).withMinute(0).withSecond(0).withNano(0)
                .format(IN_OUT_TOTAL_TIME_FMT);
        String inOutEndTime = LocalDateTime.now().format(IN_OUT_TOTAL_TIME_FMT);

        ScadaPageQueryDto dto = new ScadaPageQueryDto();
        dto.setFaultState("1");
        dto.setLimit("5");
        dto.setOrgId("3");
//        dto.setAlarmState("1");

        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put("page", 1);
        eventParams.put("limit", 2);
        eventParams.put("org_id", 3);
        eventParams.put("start_time", startTime);
        eventParams.put("subarea_cid", "0.001.001.001-0");
//        eventParams.put("event_level", 3);
        eventParams.put("event_state", 1);

        // 2. 七路互不依赖的远程查询并发执行
        CompletableFuture<AjaxResultVO> healthFuture = CompletableFuture.supplyAsync(
                () -> deviceService.getDeviceHealthRate("3", null, null, "0"), REPORT_EXECUTOR);
        CompletableFuture<List<HashMap<String, Object>>> subareaFuture = CompletableFuture.supplyAsync(
                () -> dashboardService.getSubareaState("3", "0.001.001.001-0"), REPORT_EXECUTOR);
        CompletableFuture<AjaxResultVO> deviceListFuture = CompletableFuture.supplyAsync(
                () -> deviceService.StateListPage(dto), REPORT_EXECUTOR);
        CompletableFuture<AjaxResultVO> eventListFuture = CompletableFuture.supplyAsync(
                () -> scadaClient.eventListPage(eventParams), REPORT_EXECUTOR);
        CompletableFuture<Res<StationRealtimeVO>> stationFlowFuture = CompletableFuture.supplyAsync(
                () -> videoAnalyticsClient.getStationRealtime(), REPORT_EXECUTOR);
        CompletableFuture<Res<StationInOutTotalVO>> inOutTotalFuture = CompletableFuture.supplyAsync(
                () -> videoAnalyticsClient.getStationInOutTotal(inOutStartTime, inOutEndTime), REPORT_EXECUTOR);
        CompletableFuture<Res<List<CrowdedAreaVO>>> crowdedAreasFuture = CompletableFuture.supplyAsync(
                () -> videoAnalyticsClient.getCrowdedAreas(), REPORT_EXECUTOR);
        CompletableFuture<Res<SecurityCheckCurrentVO>> securityCheckFuture = CompletableFuture.supplyAsync(
                () -> videoAnalyticsClient.getSecurityCheckCurrent(), REPORT_EXECUTOR);

        CompletableFuture.allOf(healthFuture, subareaFuture, deviceListFuture, eventListFuture,
                stationFlowFuture, inOutTotalFuture, crowdedAreasFuture, securityCheckFuture).join();

        // 3. 取结果并精简（SnakeCase → camelCase）
        AjaxResultVO deviceHealthRate = healthFuture.join();
        List<HashMap<String, Object>> subareaState = subareaFuture.join();
        AjaxResultVO deviceListVo = deviceListFuture.join();
        AjaxResultVO eventListVo = eventListFuture.join();
        Res<StationRealtimeVO> stationFlowRes = stationFlowFuture.join();
        Res<StationInOutTotalVO> inOutTotalRes = inOutTotalFuture.join();
        Res<List<CrowdedAreaVO>> crowdedAreasRes = crowdedAreasFuture.join();
        Res<SecurityCheckCurrentVO> securityCheckRes = securityCheckFuture.join();
        Object deviceList = deviceListVo != null ? deviceListVo.getData() : null;
        Object eventList = eventListVo != null ? eventListVo.getData() : null;

        List<ReportDeviceState> faultDevices = deviceList == null
                ? Collections.emptyList()
                : objectMapper.convertValue(deviceList, new TypeReference<>() {});
        List<ReportDeviceEvent> deviceEvents = eventList == null
                ? Collections.emptyList()
                : objectMapper.convertValue(eventList, new TypeReference<>() {});
        // 4. 整合返回（每个模块附带统计总数）
        return ReportDataVO.builder()
                // 区域态势列表
                .subareaState(subareaState != null ? new ArrayList<>(subareaState) : Collections.emptyList())
                // 设备健康率统计（总数/故障数/告警数/健康率）
                .deviceHealthRate(toHealthRate(deviceHealthRate))
                // 故障设备列表及故障设备总数（分页 count）
                .faultDevices(faultDevices != null ? faultDevices : Collections.emptyList())
                .faultDeviceCount(extractCount(deviceListVo))
                // 严重事件列表及事件总数（分页 count）
                .deviceEvents(deviceEvents != null ? deviceEvents : Collections.emptyList())
                .deviceEventCount(extractCount(eventListVo))
                .deviceEventQueryStartTime(startTime)
                // 客流数据（实时在站人数概览）
                .stationRealtime(stationFlowRes != null ? stationFlowRes.getData() : null)
                // 当前安检人数（站内人数/已安检人数/未安检人数）
                .securityCheckCurrent(securityCheckRes != null ? securityCheckRes.getData() : null)
                // 累计进出站客流（统计区间：今日 00:00:00 至当前）
                .stationInOutTotal(inOutTotalRes != null ? inOutTotalRes.getData() : null)
                // 拥堵区域列表及拥堵区域数量
                .crowdedAreas(crowdedAreasRes != null ? crowdedAreasRes.getData() : null)
                .crowdedAreaCount(crowdedAreasRes != null && crowdedAreasRes.getData() != null
                        ? crowdedAreasRes.getData().size() : 0)
                .build();
    }

    /**
     * 将设备健康率查询结果转为结构化统计对象。
     *
     * @param vo 设备健康率查询结果
     * @return 健康率统计对象，查询失败时为 null
     */
    private ReportDeviceHealthRate toHealthRate(AjaxResultVO vo) {
        Object data = vo != null ? vo.getData() : null;
        return data == null ? null : objectMapper.convertValue(data, ReportDeviceHealthRate.class);
    }

    /**
     * 安全提取分页查询结果中的统计总数（count 缺失时返回 null，避免 NPE）。
     *
     * @param vo 分页查询结果
     * @return 统计总数
     */
    private Integer extractCount(AjaxResultVO vo) {
        Object count = vo != null ? vo.get("count") : null;
        return count instanceof Number n ? n.intValue() : null;
    }
}
