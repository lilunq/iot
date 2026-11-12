package com.sysware.swcloud.feign;

import com.sysware.swcloud.common.core.vo.Res;
import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.ifim.CrowdedAreaVO;
import com.sysware.swcloud.domian.ifim.SecurityCheckCurrentVO;
import com.sysware.swcloud.domian.ifim.StationInOutTotalVO;
import com.sysware.swcloud.domian.ifim.StationRealtimeVO;
import com.sysware.swcloud.domian.videoAnalytics.PassengerAreaVO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VideoAnalyticsClientHystrix implements VideoAnalyticsClient {

    @Override
    public Res<StationRealtimeVO> getStationRealtime() {
        return Res.failed();
    }

    @Override
    public Res<SecurityCheckCurrentVO> getSecurityCheckCurrent() {
        return Res.failed();
    }

    @Override
    public Res<List<CrowdedAreaVO>> getCrowdedAreas() {
        return Res.failed();
    }

    @Override
    public Res<StationInOutTotalVO> getStationInOutTotal(String startTime, String endTime) {
        return Res.failed();
    }

    @Override
    public AjaxResultVO getPassengerAreas(String code) {
        return AjaxResultVO.error("查询客流区域信息失败");
    }

    @Override
    public Res<List<PassengerAreaVO>> getALLPassengerAreas() {
        return Res.failed();
    }
}
