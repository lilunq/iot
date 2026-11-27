package com.sysware.tools;

import com.sysware.service.DeviceService;
import com.sysware.service.StationEntitySearchService;
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
import java.util.List;
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
    @Autowired
    private StationEntitySearchService entitySearchService;

    /**
     * 分页查询设备列表和设备状态（平铺参数，供 MCP/LLM 调用）。
     */
    @Tool(name = "getDeviceStatePage", description = "底层分页查询设备列表和设备状态，支持设备ID集合及状态条件过滤。自然语言设备查询优先使用 queryStationDevices；仅在已有明确设备筛选参数或兼容旧流程时直接调用本工具。返回设备列表和状态，不查询设备点位详情。")
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
     * 面向 Agent 的完整设备查询：自然语言实体定位 + 一次性实时状态查询。
     * ES 候选 ID 只在 MCP 服务内部流转，不要求 Agent 分步调用实体检索和状态查询。
     */
    @Tool(name = "queryStationDevices", description = "根据用户完整自然语言查询西安东站设备及实时状态。内部先定位真实 DEVICE 候选，再一次性按候选设备ID查询实时状态；支持只查故障或只查告警设备。适用于设备列表、区域/站台设备集合查询。不要把普通位置线索擅自转成区域硬过滤。")
    public AjaxResultVO queryStationDevices(
            @ToolParam(description = "用户完整的原始问题，例如：查看5站台所有故障设备") String prompt,
            @ToolParam(description = "故障状态，传1表示只查询故障设备，非必填", required = false) String faultState,
            @ToolParam(description = "告警状态，传1表示只查询告警设备，非必填", required = false) String alarmState,
            @ToolParam(description = "页码，从1开始，默认1", required = false) String pageNum,
            @ToolParam(description = "每页条数，默认20", required = false) String pageSize,
            @ToolParam(description = "组织ID，默认3（西安东站）", required = false) String orgId) {
        if (!isValidStateFilter(faultState) || !isValidStateFilter(alarmState)) {
            return AjaxResultVO.error(400, "故障/告警状态仅允许传1或不传");
        }
        if (faultState != null && !faultState.isBlank()
                && alarmState != null && !alarmState.isBlank()) {
            return AjaxResultVO.error(400, "故障和告警条件请分别查询，避免将底层条件组合误解为“故障或告警”");
        }
        try {
            StationEntitySearchService.SearchResult search =
                    entitySearchService.searchIds(prompt, "DEVICE");
            List<String> ids = search.getIds();
            if (ids == null || ids.isEmpty()) {
                return AjaxResultVO.ok("未找到符合条件的设备")
                        .addQueryPageData(List.of(), 0)
                        .put("entityCandidateCount", 0)
                        .put("entitySearchStatus", search.getStatus());
            }

            ScadaPageQueryDto dto = ScadaPageQueryDto.builder()
                    .page(pageNum == null || pageNum.isBlank() ? "1" : pageNum)
                    .limit(pageSize == null || pageSize.isBlank() ? "10" : pageSize)
                    .orgId(orgId == null || orgId.isBlank() ? "3" : orgId)
                    .faultState(faultState)
                    .alarmState(alarmState)
                    .addChangeStateTime(0)
                    .ids(ids)
                    .build();

            AjaxResultVO result = deviceService.StateListPage(dto);
            if (result == null) {
                return AjaxResultVO.error("设备实时状态查询未返回结果，请稍后重试");
            }
            List<HashMap<String, Object>> data = result.getData();
            result.put("entityType", "DEVICE");
            result.put("entitySearchStatus", search.getStatus());
            result.put("entityCandidateCount", ids.size());
            result.put("count", result.getCount());
            result.put("page", pageNum == null || pageNum.isBlank() ? "1" : pageNum);
            result.put("limit", pageSize == null || pageSize.isBlank() ? "10" : pageSize);
            result.put("returnedCount", data == null ? 0 : data.size());
            return result;
        } catch (IllegalArgumentException ex) {
            return AjaxResultVO.error(400, ex.getMessage());
        } catch (IllegalStateException ex) {
            log.warn("设备实体定位失败：{}", ex.getMessage());
            return AjaxResultVO.error(503, ex.getMessage());
        } catch (Exception ex) {
            log.error("设备自然语言查询失败", ex);
            return AjaxResultVO.error("设备查询失败，请稍后重试");
        }
    }

    private boolean isValidStateFilter(String value) {
        return value == null || value.isBlank() || "1".equals(value.trim());
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
