package com.sysware.swcloud.feign;

import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.Dto;
import com.sysware.swcloud.domian.HashDto;
import com.sysware.swcloud.domian.item.EnergyContrastDayDto;
import com.sysware.swcloud.domian.item.EnergyContrastHourDto;
import com.sysware.swcloud.domian.item.EnergyContrastMonthDto;
import com.sysware.swcloud.domian.item.EnergyContrastYearDto;
import com.sysware.swcloud.domian.item.SubAllEnergyQueryDto;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class IemsClientHystrix implements IemsClient {
    @Override
    @RequestMapping(value = "/out/deviceList")
    public List listDevice(@RequestParam Map<String, Object> inMap) {
        return new ArrayList<>();
    }

    ;

    @Override
    @RequestMapping("/getYearData")
    public List<Dto> getYearData(@RequestParam(value = "org_id") String org_id,
                                 @RequestParam(value = "energy_type", defaultValue = "0.001") String energyTypeCid,
                                 @RequestParam(value = "dateTimes") List<String> dateTimes) {
        return new ArrayList<>();
    }

    @Override
    public AjaxResultVO getYearDataV2(EnergyContrastYearDto dto) {
        return AjaxResultVO.error();
    }

    @Override
    public AjaxResultVO getHourDataV2(EnergyContrastHourDto dto) {
        return AjaxResultVO.error();
    }

    @Override
    public AjaxResultVO getDayDataV2(EnergyContrastDayDto dto) {
        return AjaxResultVO.error();
    }

    @Override
    public AjaxResultVO getMonthDataV2(EnergyContrastMonthDto dto) {
        return AjaxResultVO.error();
    }


    @RequestMapping("/getMonthData")
    public List<Dto> getMonthData(@RequestParam(value = "org_id") String org_id,
                                  @RequestParam(value = "energy_type", defaultValue = "0.001") String energyTypeCid,
                                  @RequestParam(value = "dateTimes") List<String> dateTimes) {
        return new ArrayList<>();
    }

    @RequestMapping("/getDayData")
    public List<Dto> getDayData(@RequestParam(value = "org_id") String org_id,
                                @RequestParam(value = "energy_type", defaultValue = "0.001") String energyTypeCid,
                                @RequestParam(value = "dateTimes") List<String> dateTimes) {
        return new ArrayList<>();
    }


    @RequestMapping("/getHourData")
    public Dto getHourData(@RequestParam(value = "org_id") String org_id,
                           @RequestParam(value = "energy_type", defaultValue = "0.001") String energyTypeCid,
                           @RequestParam(value = "dateTimes") List<String> dateTimes) {
        return new HashDto();
    }

    @Override
    public AjaxResultVO getDmyDataByDevice(Map<String, Object> inMap) {
        return null;
    }

    @Override
    public AjaxResultVO listData(@RequestParam Map<String, Object> inMap,
                                 @RequestParam(value = "dateTimes") List<String> dateTimes,
                                 @RequestParam(value = "subCids") List<String> subCids,
                                 @RequestParam(value = "typeCids") List<String> typeCids,
                                 @RequestParam(value = "timeType") String timeType) {
        return AjaxResultVO.error();
    }

    @Override
    public AjaxResultVO devicePage(Map<String, Object> inMap) {
        return AjaxResultVO.error();
    }

    @Override
    public AjaxResultVO subareaAllEnergy(SubAllEnergyQueryDto dto) {
        return AjaxResultVO.error();
    }
}
