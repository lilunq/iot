package com.sysware.controller;

import com.alibaba.fastjson.JSON;
import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.item.*;
import com.sysware.swcloud.feign.IemsClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hutool.core.bean.BeanUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@Tag(name = "能管接口", description = "使用原生iot接口")
public class EmsController {

    @Autowired
    private IemsClient iemsClient;

    /**
     * 获取历史能耗：区域、分类、时间
     * orgId 3
     * subCids 0.001.001.001-0.001
     * typeCids 0.001
     * groupCid JSON.stringify([{ subCid: "0.001.001.001", typeCid: "0.001" }]))
     * dateTimes 202604  日期分別对应格式：202604/2026/2026。,分割
     * timeType day/month/year
     */
    @Operation(summary = "获取历史能耗", description = "区域、分类、时间")
    @GetMapping("/report/listData")
    public AjaxResultVO listData(
            @RequestParam(value = "orgId", defaultValue = "3") String orgId,
            @RequestParam(value = "subCids", defaultValue = "0.001.001.001-0.001") List<String> subCids,
            @RequestParam(value = "typeCids", defaultValue = "0.001") List<String> typeCids,
            @RequestParam(value = "timeType", defaultValue = "day/month/year") String timeType,
            @RequestParam(value = "dateTimes", defaultValue = "202604/2026/2026") List<String> dateTimes) {
        Map<String, Object> inMap = new HashMap<>();
        inMap.put("orgId", orgId);
        inMap.put("groupCid", JSON.toJSONString(List.of(
                Map.of("subCid", subCids.get(0), "typeCid", typeCids.get(0))
        )));
        return iemsClient.listData(inMap, dateTimes, subCids, typeCids, timeType);
    }


    @GetMapping("/device/history/getDmyDataByDevice")
    @Operation(summary = "查询指定电表的数据", description = "可筛选:device_id,date:20260328")
    public AjaxResultVO getEmsByDevice(@RequestParam("device_id") String deviceId, @RequestParam(value = "date", defaultValue = "20260531") String date) {
        Map<String, Object> inMap = new HashMap<String, Object>() {{
            put("device_id", deviceId);
            put("date", date);
        }};
        return iemsClient.getDmyDataByDevice(inMap);
    }


//    @Operation(summary = "查询指定天的小时能耗", description = "查询指定天的小时能耗:dateTimes:20260320,20260321")
//    @GetMapping("/out/getHourData")
//    public Map<String, Object> getHourData(@RequestParam(value = "org_id", defaultValue = "3") String org_id,
//                                           @RequestParam(value = "energy_type", defaultValue = "0.001") String energyTypeCid,
//                                           @RequestParam(value = "dateTimes", defaultValue = "20260320,20260321") List<String> dateTimes) {
//        return iemsClient.getHourData(org_id, energyTypeCid, dateTimes);
//    }


//    @GetMapping("/out/deviceList")
//    @Operation(summary = "查询电表", description = "可筛选:组织 id,设备 id,区域 cid,能源类型 cid,设备名称,设备类型,是否用于统计单位总用电量：1 是，0 否")
//    public List listDevice(
//            @RequestParam(name = "org_id", required = false, defaultValue = "3") String orgId,
//            @RequestParam(name = "subarea_cid", required = false, defaultValue = "0.001.001.001-0.001") String subareaCid,
//            @RequestParam(name = "energy_type_cid", required = false, defaultValue = "0.001") String energyTypeCid,
//            @RequestParam(name = "device_name", required = false) String deviceName,
//            @RequestParam(name = "org_total_flag", required = false) String org_total_fla) {
//
//        Map<String, Object> inMap = new HashMap<String, Object>() {{
//            put("org_id", orgId);
//            put("subarea_cid", subareaCid);
//            put("energy_type_cid", energyTypeCid);
//            put("device_name", deviceName);
//            put("org_total_flag", org_total_fla);
//        }};
//        return iemsClient.listDevice(inMap);
//    }


//    @Operation(summary = "查询指定月份的日统计")
//    @GetMapping("/out/getDayData")
//    public List getDayData(@RequestParam(value = "org_id", defaultValue = "3") String org_id,
//                           @RequestParam(value = "energy_type", defaultValue = "0.001") String energyTypeCid,
//                           @RequestParam(value = "dateTimes", defaultValue = "202605") List<String> dateTimes) {
//        return iemsClient.getDayData(org_id, energyTypeCid, dateTimes);
//    }
//
//    ;
//
//    @Operation(summary = "查询指定年的月能耗")
//    @GetMapping("/out/getMonthData")
//    public List getMonthData(@RequestParam(value = "org_id", defaultValue = "3") String org_id,
//                             @RequestParam(value = "energy_type", defaultValue = "0.001") String energyTypeCid,
//                             @RequestParam(value = "dateTimes", defaultValue = "2026") List<String> dateTimes) {
//        return iemsClient.getMonthData(org_id, energyTypeCid, dateTimes);
//    }
//
//    @Operation(summary = "查询指定年的年能耗")
//    @GetMapping("/out/getYearData")
//    public List getYearData(@RequestParam(value = "org_id", defaultValue = "3") String org_id,
//                            @RequestParam(value = "energy_type", defaultValue = "0.001") String energyTypeCid,
//                            @RequestParam(value = "dateTimes", defaultValue = "2025,2026") List<String> dateTimes) {
//        return iemsClient.getYearData(org_id, energyTypeCid, dateTimes);
//    }

    /**
     * 设备能耗年统计
     *
     * @param dto 查询参数
     * @return 统计数据
     */
    @Operation(summary = "设备能耗年统计")
    @PostMapping("/device/contrast/querySumYearData")
    AjaxResultVO getYearDataV2(@RequestBody EnergyContrastYearDto dto) {
        return iemsClient.getYearDataV2(dto);
    }

    /**
     * 设备能耗时统计
     *
     * @param dto 查询参数
     * @return 统计数据
     */
    @Operation(summary = "设备能耗时统计")
    @PostMapping("/device/contrast/querySumHourData")
    AjaxResultVO getHourDataV2(@RequestBody EnergyContrastHourDto dto) {
        return iemsClient.getHourDataV2(dto);
    }

    /**
     * 设备能耗日统计
     *
     * @param dto 查询参数
     * @return 统计数据
     */
    @Operation(summary = "设备能耗日统计")
    @PostMapping("/device/contrast/querySumDayData")
    AjaxResultVO getDayDataV2(@RequestBody EnergyContrastDayDto dto) {
        return iemsClient.getDayDataV2(dto);
    }

    /**
     * 设备能耗月统计
     *
     * @param dto 查询参数
     * @return 统计数据
     */
    @Operation(summary = "设备能耗月统计")
    @PostMapping("/device/contrast/querySumMonthData")
    AjaxResultVO getMonthDataV2(@RequestBody EnergyContrastMonthDto dto) {
        return iemsClient.getMonthDataV2(dto);
    }

    /**
     * 分页查询能耗设备列表
     *
     * @param dto 查询参数
     * @return 设备列表
     */
    @Operation(summary = "分页查询能耗设备列表")
    @PostMapping("/device/listPage")
    AjaxResultVO devicePage(@RequestBody ItemDeviceQueryDto dto) {
        Map<String, Object> map = BeanUtil.beanToMap(dto, true, true);
        return iemsClient.devicePage(map);
    }

    /**
     * 综合查询区域能耗
     *
     * @param dto 查询参数
     * @return 区域能耗统计数据
     */
    @Operation(summary = "综合查询区域能耗")
    @PostMapping("/comprehensive/getSubAllEnergy")
    AjaxResultVO subareaAllEnergy(@RequestBody SubAllEnergyQueryDto dto) {
        return iemsClient.subareaAllEnergy(dto);
    }
}
