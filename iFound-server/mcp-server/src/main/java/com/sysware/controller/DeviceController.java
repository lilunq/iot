package com.sysware.controller;

import com.sysware.service.DeviceService;
import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.SwEnumEntity;
import com.sysware.swcloud.domian.scada.ScadaInfoQueryDto;
import com.sysware.swcloud.domian.scada.ScadaEventQueryDto;
import com.sysware.swcloud.domian.scada.ScadaPageQueryDto;
import com.sysware.swcloud.domian.scada.ScadaYkSendCommand;
import com.sysware.swcloud.feign.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hutool.core.bean.BeanUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@RestController
@Tag(name = "设备接口", description = "使用原生iot接口")
public class DeviceController {
    @Autowired
    private ScadaClient scadaClient;
    @Autowired
    private AdminClient adminClient;
    @Autowired
    private FepClient fepClient;
    @Autowired
    private DeviceService deviceService;

    /**
     * 通过指定的key获取对应字典数据集合
     *
     * @param key 机电设备系统列表 device_system_type
     * @return
     */
    @Operation(summary = "获取机电设备系统列表")
    @GetMapping(value = "/device/enum/getEnumKeyValues")
    public AjaxResultVO getEnumKeyValues(@RequestParam(value = "key", defaultValue = "device_system_type") String key) {
        return AjaxResultVO.ok().data(adminClient.getEnumKeyValues(key).stream()
                .collect(Collectors.toMap(SwEnumEntity::getElement_key, SwEnumEntity::getElement_value)));
    }

    /**
     * 根据条件查询设备列表与点位信息，不支持过滤告警和故障设备
     * @param page 1 页码
     *             limit 10 每页数量
     *             org_id 组织ID
     *             subarea_cid 0.001.001.001-0.001 区域CID
     *             fault_state 1 故障
     *             alarm_state 1 告警
     */
    @Operation(summary = "查询设备,聚合设备数据，不支持过滤告警和故障设备")
    @PostMapping("/base/device/StateListPageV2")
    public AjaxResultVO StateListPageV2(@RequestBody @Valid ScadaInfoQueryDto queryDto){
        return scadaClient.StateListPageV2(queryDto);
    };

    /**
     * 查询设备列表和状态。
     *
     * 支持按组织、区域、系统类型、设备类型、设备名称、故障状态、告警状态等条件筛选。
     * 当 addChangeStateTime 为 1 且查询故障/告警设备时，会在返回设备数据中补充最近变更时间。
     *
     * @param dto 查询参数
     * @return 设备列表和分页总数
     */
    @Operation(summary = "查询设备列表和状态")
    @PostMapping("/base/device/StateListPage")
    public AjaxResultVO StateListPage(@RequestBody ScadaPageQueryDto dto) {
        return deviceService.StateListPage(dto);
    }

    @Operation(summary = "查询设备状态统计")
    @GetMapping("/getDeviceStateCount")
    public AjaxResultVO getDeviceStateCount(
            @RequestParam(value = "org_id") String orgId,
            @RequestParam(value = "subarea_cid", required = false) String subareaCid) {
        return AjaxResultVO.ok().data(deviceService.getDeviceStateCount(orgId, subareaCid));
    }

    /**
     * 查询指定设备的相关scada数据参数以及状态
     * device_id 1203022680538746880
     * page
     * limit
     */
    @Operation(summary = "查询指定设备相关的scada数据参数以及状态")
    @GetMapping("/base/device/data/listOneDeviceDataOrState")
    public AjaxResultVO listOneDeviceDataOrState(
            @RequestParam(value = "page") @Schema(example = "1") String page,
            @RequestParam(value = "limit") @Schema(description = "分页大小", example = "50") String limit,
            @RequestParam(value = "device_id") @Schema(description = "设备id", example = "1203022680400334848") String device_id) {
        Map<String, Object> params = new HashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        params.put("device_id", device_id);
        return scadaClient.listOneDeviceDataOrState(params);
    }

    /**
     * 下发遥控
     */
    @Operation(summary = "下发遥控")
    @PostMapping("/ykyt/sendCommand")
    public AjaxResultVO sendYkYtCommand(@RequestBody ScadaYkSendCommand command) {
        Map<String, Object> map = BeanUtil.beanToMap(command, true, true);
        return fepClient.sendYkYtCommand(map);
    }

    /**
     * 根据条件查询设备信息列表
     */
    @Operation(summary = "查询设备事件列表", description = "必选:org_id单位id,start_time起始时间;可选:system_type系统类型,subarea_cid设备分区,event_level事项等级,event_state事项状态0恢复1异常,device_id设备id,process_state处理状态0,1,end_time截止时间")
    @PostMapping("/base/device/event/listPage")
    public AjaxResultVO eventListPage(@RequestBody ScadaEventQueryDto eventQueryDto) {
        Map<String, Object> map = BeanUtil.beanToMap(eventQueryDto, true, true);
//        {"Status":"Ok","msg":"操作成功","code":"200","data":[],"count":0}
        return AjaxResultVO.ok().data(new ArrayList<>());
//        return scadaClient.eventListPage(map);
    }

    /**
     * 处理设备事件
     * ["1245310205466513408"]
     */
    @Operation(summary = "处理设备事件", description = "处理指定的设备事件")
    @PostMapping("/base/device/event/processEvent")
    public AjaxResultVO processEvent(@RequestParam(value = "ids", defaultValue = "[\"1245310205466513408\"]") String ids) {
        System.out.println("处理设备事件" + ids);
        return scadaClient.processEvent(ids);
    }

    /**
     * 统计单位设备故障次数
     */
    @Operation(summary = "统计单位设备故障次数", description = "统计指定时间范围内单位或隧道的设备故障次数")
    @GetMapping("/tunnel/statistics/getTunnelsFaultCount")
    public AjaxResultVO getTunnelFaultCount(
            @RequestParam(value = "startDate", defaultValue = "20260401") String startDate,
            @RequestParam(value = "endDate", defaultValue = "20260531") String endDate,
            @RequestParam(value = "tunnelCidList", defaultValue = "0.001.001.001") List<String> tunnelCidList) {
        return scadaClient.getTunnelFaultCount(startDate, endDate, tunnelCidList);
    }

    /**
     * 获取设备健康率统计。
     *
     * @param orgId 组织 ID，默认 3
     * @param device_type 设备类型，非必填
     * @param system_type 系统类型，非必填
     * @param is_env 是否按环境系统过滤，1 表示只查询环境系统
     * @return 设备健康率、总数、故障数、告警数
     */
    @Operation(summary = "获取总设备健康率", description = "获取所有设备的健康率、故障数、告警数")
    @GetMapping("/base/device/healthRate")
    public AjaxResultVO getDeviceHealthRate(
            @RequestParam(value = "org_id", defaultValue = "3") String orgId,
            @RequestParam(value = "device_type", required = false) String device_type,
            @RequestParam(value = "system_type", required = false) String system_type,
            @RequestParam(value = "is_env", defaultValue = "0") String is_env) {
        return deviceService.getDeviceHealthRate(orgId, device_type, system_type, is_env);
    }

    /**
     * 查询设备关联的摄像机
     * @param deviceId 设备ID
     * @return
     */
    @Operation(summary = "查询设备关联的摄像机")
    @GetMapping("/device/camera/listCameraAndPresetByDevice")
    public AjaxResultVO listCameraAndPresetByDevice(@RequestParam(value = "device_id") String deviceId) {
        return scadaClient.listCameraAndPresetByDevice(deviceId);
    }

}
