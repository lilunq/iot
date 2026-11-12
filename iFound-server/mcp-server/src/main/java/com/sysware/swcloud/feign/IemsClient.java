package com.sysware.swcloud.feign;

import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.Dto;
import com.sysware.swcloud.domian.item.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(
        name = "iotBuilder-iems",
        fallback = IemsClientHystrix.class
)
public interface IemsClient {
    /**
     * 电表查询
     *
     * @param inMap *  org_id 组织id
     *              *  id 设备id
     *              *  subarea_cid  区域cid
     *              *  energy_type_cid  能源类型cid
     *              *  name 设备名称
     *              *  device_type 设备类型
     *              *  org_total_flag 是否用于统计单位总用电量：1是，0否
     * @return
     */
    @RequestMapping(value = "/out/deviceList")
    public List listDevice(@RequestParam Map<String, Object> inMap);

    /**
     * 能耗年统计
     * org_id
     * energy_type
     *
     * @param dateTimes 2025,2026
     * @return
     */
    @RequestMapping("/out/getYearData")
    public List getYearData(@RequestParam(value = "org_id") String org_id,
                            @RequestParam(value = "energy_type", defaultValue = "0.001") String energyTypeCid,
                            @RequestParam(value = "dateTimes") List<String> dateTimes);

    /**
     * 设备能耗年统计
     *
     * @param dto 查询参数
     * @return 统计数据
     */
    @GetMapping("/device/contrast/querySumYearData")
    AjaxResultVO getYearDataV2(@SpringQueryMap EnergyContrastYearDto dto);

    /**
     * 设备能耗时统计
     *
     * @param dto 查询参数
     * @return 统计数据
     */
    @GetMapping("/device/contrast/querySumHourData")
    AjaxResultVO getHourDataV2(@SpringQueryMap EnergyContrastHourDto dto);

    /**
     * 设备能耗日统计
     *
     * @param dto 查询参数
     * @return 统计数据
     */
    @GetMapping("/device/contrast/querySumDayData")
    AjaxResultVO getDayDataV2(@SpringQueryMap EnergyContrastDayDto dto);

    /**
     * 设备能耗月统计
     *
     * @param dto 查询参数
     * @return 统计数据
     */
    @GetMapping("/device/contrast/querySumMonthData")
    AjaxResultVO getMonthDataV2(@SpringQueryMap EnergyContrastMonthDto dto);

    /**
     * 能耗月统计
     * org_id
     * energy_type
     *
     * @param dateTimes 2025,2026
     * @return
     */
    @RequestMapping("/out/getMonthData")
    List getMonthData(@RequestParam(value = "org_id") String org_id,
                      @RequestParam(value = "energy_type", defaultValue = "0.001") String energyTypeCid,
                      @RequestParam(value = "dateTimes") List<String> dateTimes);

    /**
     * 能耗日统计
     *
     * @param org_id
     * @param energy_type
     * @param dateTimes   202602,202603
     * @return
     */
    @RequestMapping("/out/getDayData")
    List getDayData(@RequestParam(value = "org_id") String org_id,
                    @RequestParam(value = "energy_type", defaultValue = "0.001") String energyTypeCid,
                    @RequestParam(value = "dateTimes") List<String> dateTimes);

    /**
     * 查询指定天的小时能耗
     * dateTimes=20260320,20260321
     * org_id=3
     * energy_type=0.001
     */
    @RequestMapping("/out/getHourData")
    Map<String, Object> getHourData(@RequestParam(value = "org_id") String org_id,
                                    @RequestParam(value = "energy_type", defaultValue = "0.001") String energyTypeCid,
                                    @RequestParam(value = "dateTimes") List<String> dateTimes);


    /**
     * 根据设备查询日月年数据
     */
    @RequestMapping("/device/history/getDmyDataByDevice")
    AjaxResultVO getDmyDataByDevice(@RequestParam Map<String, Object> inMap);

    /**
     * 获取历史能耗：区域、分类、时间
     * orgId 3
     * subCids 0.001.001.001-0.001
     * typeCids 0.001
     * groupCid JSON.stringify([{ subCid: "0.001.001.001", typeCid: "0.001" }]))
     * dateTimes 202604  日期分別对应格式：202604/2026/2026。,分割
     * timeType day/month/year
     */
    @RequestMapping("/report/listData")
    AjaxResultVO listData(@RequestParam Map<String, Object> inMap,
                          @RequestParam(value = "dateTimes") List<String> dateTimes,
                          @RequestParam(value = "subCids") List<String> subCids,
                          @RequestParam(value = "typeCids") List<String> typeCids,
                          @RequestParam(value = "timeType") String timeType);


    /**
     * 分页查询能耗设备列表
     * @param inMap 查询参数
     * @return 设备列表
     */
    @PostMapping("/device/listPage")
    AjaxResultVO devicePage(Map<String,Object> inMap);

    /**
     * 综合查询区域能耗
     */
    @GetMapping("/comprehensive/getSubAllEnergy")
    AjaxResultVO subareaAllEnergy(@SpringQueryMap SubAllEnergyQueryDto dto);
}
