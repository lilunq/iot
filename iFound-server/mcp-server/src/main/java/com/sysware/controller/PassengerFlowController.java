package com.sysware.controller;

import com.sysware.swcloud.common.core.vo.Res;
import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.ifim.CrowdedAreaVO;
import com.sysware.swcloud.domian.ifim.SecurityCheckCurrentVO;
import com.sysware.swcloud.domian.ifim.StationInOutTotalVO;
import com.sysware.swcloud.domian.ifim.StationRealtimeVO;
import com.sysware.swcloud.domian.videoAnalytics.PassengerAreaVO;
import com.sysware.swcloud.feign.VideoAnalyticsClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@Tag(name = "客流接口", description = "视频客流相关接口")
@Slf4j
@RequiredArgsConstructor
public class PassengerFlowController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VideoAnalyticsClient videoAnalyticsClient;

    /**
     * 查询车站实时客流概览
     *
     * @return 车站实时客流概览
     */
    @Operation(summary = "查询车站实时客流概览")
    @GetMapping("/passenger-flow/station/realtime")
    public Res<StationRealtimeVO> getStationRealtime() {
        return videoAnalyticsClient.getStationRealtime();
    }

    /**
     * 查询当前安检人数统计
     *
     * @return 站内人数、已安检人数、未安检人数
     */
    @Operation(summary = "查询当前安检人数统计")
    @GetMapping("/passenger-flow/statistic/security-check/current")
    public Res<SecurityCheckCurrentVO> getSecurityCheckCurrent() {
        return videoAnalyticsClient.getSecurityCheckCurrent();
    }

    /**
     * 查询所有拥堵区域
     *
     * @return 所有拥堵区域
     */
    @Operation(summary = "查询所有拥堵区域")
    @GetMapping("/passenger-flow/station/crowded")
    public Res<List<CrowdedAreaVO>> getCrowdedAreas() {
        return videoAnalyticsClient.getCrowdedAreas();
    }

    /**
     * 查询该时间段累计进出站人数
     *
     * @param startTime 开始时间，格式 yyyy-MM-dd HH:mm:ss，不传默认今日 00:00:00
     * @param endTime   结束时间，格式 yyyy-MM-dd HH:mm:ss，不传默认今日 23:59:59
     * @return 累计进出站人数
     */
    @Operation(summary = "查询累计进出站人数")
    @GetMapping("/passenger-flow/biz/in-out-total")
    public Res<StationInOutTotalVO> getStationInOutTotal(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        if (startTime == null || startTime.isEmpty()) {
            startTime = LocalDate.now().atTime(LocalTime.MIN).format(DATE_FMT);
        }
        if (endTime == null || endTime.isEmpty()) {
            endTime = LocalDate.now().atTime(LocalTime.MAX).format(DATE_FMT);
        }
        log.info("查询累计进出站人数: startTime={}, endTime={}", startTime, endTime);
        return videoAnalyticsClient.getStationInOutTotal(startTime, endTime);
    }

    /**
     * 根据区域编码查询客流区域信息（含摄像头流地址等）
     *
     * @param code 区域编码，如 HC-DN-7
     * @return 客流区域信息列表
     */
    @Operation(summary = "查询客流区域信息", description = "根据区域编码查询客流区域信息，含摄像头流地址、标定矩阵等")
    @GetMapping("/passenger-flow/areas")
    public AjaxResultVO getPassengerAreas(@RequestParam("code") String code) {
        log.info("查询客流区域信息: code={}", code);
        return videoAnalyticsClient.getPassengerAreas(code);
    }

    /**
     * 获取所有客流区域
     *
     * @return 客流区域
     */
    @Operation(summary = "获取所有客流区域", description = "获取所有客流区域")
    @GetMapping("/passenger-areas/all")
    public Res<List<PassengerAreaVO>> getALLPassengerAreas() {
        log.info("查询所有客流区域信息");
        return videoAnalyticsClient.getALLPassengerAreas();
    }
}
