package com.sysware.swcloud.feign;

import com.sysware.swcloud.common.core.vo.Res;
import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.ifim.CrowdedAreaVO;
import com.sysware.swcloud.domian.ifim.SecurityCheckCurrentVO;
import com.sysware.swcloud.domian.ifim.StationInOutTotalVO;
import com.sysware.swcloud.domian.ifim.StationRealtimeVO;
import com.sysware.swcloud.domian.videoAnalytics.PassengerAreaVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "iotBuilder-video-analytics",
        fallback = VideoAnalyticsClientHystrix.class
)
public interface VideoAnalyticsClient {

    /**
     * 查询车站实时客流概览
     *
     * @return 车站实时客流概览
     */
    @GetMapping("/v1/passenger-flow/station/realtime")
    Res<StationRealtimeVO> getStationRealtime();

    /**
     * 查询当前安检人数统计
     *
     * @return 站内人数、已安检人数、未安检人数
     */
    @GetMapping("/v1/passenger-flow/statistic/security-check/current")
    Res<SecurityCheckCurrentVO> getSecurityCheckCurrent();

    /**
     * 查询所有人数拥堵区域
     *
     * @return 所有人数拥堵区域
     */
    @GetMapping("/v1/passenger-flow/station/people-num-crowded")
    Res<List<CrowdedAreaVO>> getCrowdedAreas();

    /**
     * 查询该时间段累计进出站人数
     *
     * @param startTime 开始时间，格式 yyyy-MM-dd HH:mm:ss，不传默认今日 00:00:00
     * @param endTime   结束时间，格式 yyyy-MM-dd HH:mm:ss，不传默认今日 23:59:59
     * @return 累计进出站人数（entryCount / exitCount）
     */
    @GetMapping("/v1/passenger-flow/statistic/station/entry-exit")
    Res<StationInOutTotalVO> getStationInOutTotal(@RequestParam("startTime") String startTime,
                                                  @RequestParam("endTime") String endTime);

    /**
     * 根据区域编码查询客流区域信息（含摄像头流地址等）
     *
     * @param code 区域编码，如 HC-DN-7
     * @return 客流区域信息列表
     */
    @GetMapping("/v1/passenger-areas")
    AjaxResultVO getPassengerAreas(@RequestParam("code") String code);

    /**
     * 获取所有客流区域
     *
     * @return 客流区域
     */
    @GetMapping("/v1/passenger-areas/all")
    Res<List<PassengerAreaVO>> getALLPassengerAreas();
}
