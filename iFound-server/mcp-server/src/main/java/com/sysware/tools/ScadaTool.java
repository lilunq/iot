package com.sysware.tools;

import com.sysware.service.DeviceService;
import com.sysware.swcloud.domian.*;
import com.sysware.swcloud.domian.scada.ScadaEventQueryDto;
import com.sysware.swcloud.domian.scada.ScadaInfoQueryDto;
import com.sysware.swcloud.domian.scada.ScadaPageQueryDto;
import com.sysware.swcloud.domian.scada.ScadaYkSendCommand;
import com.sysware.swcloud.feign.FepClient;
import com.sysware.swcloud.feign.ScadaClient;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hutool.core.bean.BeanUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ScadaTool {

    @Autowired
    private ScadaClient scadaClient;
    @Autowired
    private FepClient fepClient;
    @Autowired
    private DeviceService deviceService;

    /**
     * 分页查询设备列表和设备状态（平铺参数，供 MCP/LLM 调用）。
     */
    @Tool(name = "getDeviceStatePage", description = "分页查询设备列表和设备状态，可按组织、区域、系统类型、设备类型、设备名称、故障状态、告警状态筛选，返回原始数据")
    public AjaxResultVO getDeviceStatePage(
            @ToolParam(description = "页码，从1开始") String pageNum,
            @ToolParam(description = "每页多少条") String pageSize,
            @ToolParam(description = "组织ID，固定为3") String orgId,
            @ToolParam(description = "设备ID，非必填", required = false) String deviceId,
            @ToolParam(description = "设备名称，模糊查询，非必填", required = false) String deviceName,
            @ToolParam(description = "区域CID，右模糊查询，非必填", required = false) String subareaCid,
//            @ToolParam(description = "设备类型，右模糊查询，非必填", required = false) String deviceType,
            @ToolParam(description = "系统类型，非必填", required = false) String systemType,
            @ToolParam(description = "故障状态，传1表示只查询故障设备，非必填", required = false) String faultState,
            @ToolParam(description = "告警状态，传1表示只查询告警设备，非必填", required = false) String alarmState,
            @ToolParam(description = "是否添加告警和故障设备的变更时间，1表示添加，默认0", required = false) Integer addChangeStateTime) {

        ScadaPageQueryDto dto = ScadaPageQueryDto.builder()
                .page(pageNum)
                .limit(MyUtil.isEmpty(pageSize)?"10":pageSize)
                .orgId(orgId)
                .deviceId(deviceId)
                .deviceName(deviceName)
                .subareaCid(subareaCid)
//                .deviceType(deviceType)
                .systemType(systemType)
                .faultState(faultState)
                .alarmState(alarmState)
                .addChangeStateTime(addChangeStateTime == null ? 0 : addChangeStateTime)
                .build();

        return deviceService.StateListPage(dto);
    }

    /**
     * 分页查询设备事项（平铺参数）。
     */
    @Tool(name = "getDeviceEventPage", description = "分页查询设备事项，可根据区域、事件等级、事件状态、设备ID、时间范围筛选；事件等级：0=操作，1=通知，2=告警，3=故障，10=火警")
    public AjaxResultVO eventListPage(
            @ToolParam(description = "页码，从1开始") String pageNum,
            @ToolParam(description = "每页多少条") String pageSize,
            @ToolParam(description = "单位ID，固定为3（西安东站）") String orgId,
            @ToolParam(description = "事件范围查询的开始时间，格式yyyyMMddHHmmss，例如20260511101308，默认近7天") String startTime,
            @ToolParam(description = "事件范围查询的结束时间，格式yyyyMMddHHmmss，例如20260511101308，非必填", required = false) String endTime,
            @ToolParam(description = "区域ID，查询整个站台时传0.001.001.001-0") String subareaCid,
            @ToolParam(description = "系统类型，非必填", required = false) String systemType,
            @ToolParam(description = "事件等级，0 操作事项，1 通知事项，2 告警事项，3 故障事项，10 火警事项，非必填", required = false) String eventLevel,
            @ToolParam(description = "事件状态，0=恢复，1=异常，非必填", required = false) String eventState,
            @ToolParam(description = "设备ID，非必填", required = false) String deviceId,
            @ToolParam(description = "处理状态，1=已处理/恢复，0=未处理/异常，非必填", required = false) String processState) {

        ScadaEventQueryDto eventQueryDto = ScadaEventQueryDto.builder()
                .page(pageNum)
                .limit(pageSize)
                .orgId(orgId)
                .startTime(startTime)
                .subareaCid(subareaCid)
                .systemType(systemType)
                .eventLevel(eventLevel)
                .eventState(eventState)
                .deviceId(deviceId)
                .processState(processState)
                .endTime(endTime)
                .build();

        Map<String, Object> map = BeanUtil.beanToMap(eventQueryDto, true, true);
        return scadaClient.eventListPage(map);
    }

    /**
     * 获取设备健康率统计。
     */
//    @Tool(name = "getDeviceHealthRate", description = "获取设备健康率统计，返回设备总数、故障数、告警数和健康率")
    public AjaxResultVO getDeviceHealthRate(
            @ToolParam(description = "组织ID，默认3（西安东站）", required = false) String orgId,
            @ToolParam(description = "设备类型，非必填", required = false) String deviceType,
            @ToolParam(description = "系统类型，非必填", required = false) String systemType,
            @ToolParam(description = "是否按环境系统过滤，1表示只查询环境系统，默认0", required = false) String isEnv) {
        String finalOrgId = (orgId == null || orgId.isBlank()) ? "3" : orgId;
        String finalIsEnv = (isEnv == null || isEnv.isBlank()) ? "0" : isEnv;
        log.debug("mcp 调用获取设备健康率: orgId={}, deviceType={}, systemType={}, isEnv={}", finalOrgId, deviceType, systemType, finalIsEnv);
        return deviceService.getDeviceHealthRate(finalOrgId, deviceType, systemType, finalIsEnv);
    }

    /**
     * 查询设备信息及点位信息（平铺参数）。
     */
    @Tool(name = "getDeviceInfoWithPoints", description = "查询设备信息及点位信息，可按组织、设备、区域、系统类型、设备名称筛选，不支持故障/告警状态过滤，返回原始数据")
    public AjaxResultVO getDeviceWithPoints(
            @ToolParam(description = "设备ID，必填", required = true) Long deviceId) {
        ScadaInfoQueryDto queryDto = ScadaInfoQueryDto.builder()
                .page(1)
                .limit(5)
                .orgId(3L)
                .deviceId(deviceId)
//                .systemType(systemType)
//                .subareaCid(subareaCid)
//                .deviceName(deviceName)
                .build();

        return scadaClient.StateListPageV2(queryDto);
    }

//    @Tool(name = "listCameraAndPresetByDevice", description = "查询设备关联的摄像机及预置位组织树，返回设备关联的摄像机列表及每个摄像机的预置位信息")
    public AjaxResultVO listCameraAndPresetByDevice(
            @ToolParam(description = "设备ID（必填）") String deviceId) {
        log.debug("mcp 调用查询设备关联摄像机及预置位: deviceId={}", deviceId);
        return scadaClient.listCameraAndPresetByDevice(deviceId);
    }

    @Tool(name = "listScene", description = "查看控制场景")
    public AjaxResultVO listScene(
            @ToolParam(description = "场景名称") String name){
        return scadaClient.listScene(name==null?new HashMap<>():new HashMap<String, Object>(){{put("name",name);}});
    }

    @Tool(name = "sceneParamList", description = "查看某个控制场景中的控制参数")
    public AjaxResultVO sceneParamList(
            @ToolParam(description = "场景id") String scene_id){
        return scadaClient.sceneParamList(scene_id==null?new HashMap<>():new HashMap<String, Object>(){{put("scene_id",scene_id);}});
    }
    @Tool(name = "sendYkYtCommand", description = "下发控制命令")
    public AjaxResultVO sendYkYtCommand(
                @ToolParam(description = "遥控点key（必填）") String key,
                @ToolParam(description = "遥控点的类型：遥测是1，遥信是2（必填：1或2）") String dataType,
                @ToolParam(description = "遥控值（必填填）") String value
                ) {
            ScadaYkSendCommand scadaYkSendCommand =new ScadaYkSendCommand();
            scadaYkSendCommand.setKey(key);
            scadaYkSendCommand.setValue(Double.parseDouble(value));
            scadaYkSendCommand.setPackageType(dataType.equals("1")?1026:1004);
            scadaYkSendCommand.setUserId("1024");
        Map<String, Object> map = BeanUtil.beanToMap(scadaYkSendCommand, true, true);
        return fepClient.sendYkYtCommand(map);
    }
}
