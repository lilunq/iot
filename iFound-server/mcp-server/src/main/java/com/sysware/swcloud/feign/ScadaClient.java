package com.sysware.swcloud.feign;

import com.sysware.swcloud.domian.*;
import com.sysware.swcloud.domian.scada.ScadaInfoQueryDto;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(
        name = "iotBuilder-scada-server",
        fallback = ScadaClientHystrix.class
)
public interface ScadaClient {
    /**
     *查询场景
     * @param inMap name名称
     * @return
     */
    @GetMapping("/scene/scenePageList")
    public AjaxResultVO listScene(@RequestParam Map<String, Object> inMap);
    /**
     *查询场景的控制参数
     * @param inMap name名称
     * @return
     */
    @GetMapping("/scene/sceneParamList")
    public AjaxResultVO sceneParamList(@RequestParam Map<String, Object> inMap);

    /**
     * 场景新增或修改
     */
    /**
     * 场景新增或修改
     */
//    @PostMapping("/out/sceneEdit")
//    public String sceneEdit(@RequestBody ScadaSceneAllEntity entity);

    /**
     * 查询控制模式
     * @param inMap name名称,time_mode=1 时间模式,logic_mode=1 逻辑模式,is_enabled=1 启用
     */
//    @GetMapping("/out/controlModelList")
//    public List listControlModel(@RequestParam Map<String, Object> inMap);

    /**
     * 新增控制模式（自动控制）
     * name，scene_ids : {"in":["1"],"out":[]} ,
     * 逻辑控制模式 logic_mode:1，logic_data_type：1遥测 2遥信，logic_data_key，logic_trigger_value
     * 时间控制模式 time_mode:1,time_json:[{"months":[0,1,2,3,4,5,6,7,8,9,10,11],"weeks":[0,1,2,3,4,5,6],"time":"09:00:00~09:00:30","type":1,"remark":"每天9点"}]
     */
//    @PostMapping("/out/controlModelAdd")
//    public String controlModelAdd(@RequestBody ScadaControlModelEntity entity);

    /**
     * 查询所有遥测遥信参数
     * @param inMap rtu_no,name,data_no,is_enabled,yk_flag,save_curve
     */
//    @GetMapping("/out/ycyxDataList")
//    public List ycyxDataList(@RequestParam Map<String, Object> inMap);

    /**
     * 查询遥测遥信实时值
     * {
     *   "ycList": [
     *     {
     *       "id": "2"
     *     }
     *   ],
     *   "yxList": [
     *     {
     *       "id": "1"
     *     }
     *   ]
     * }
     */
//    @PostMapping("/out/ycYxRealDataList")
//    public YcYxParam ycYxRealDataList(@RequestBody YcYxParam inMap);


    /**
     * 根据条件查询设备列表与点位信息，不支持过滤告警和故障设备
     * @param page 1 页码
     *             limit 10 每页数量
     *             org_id 组织ID
     *             subarea_cid 0.001.001.001-0.001 区域CID
     *             fault_state 1 故障
     *             alarm_state 1 告警
     */
    @PostMapping("/base/device/StateListPageV2")
    public AjaxResultVO StateListPageV2(@RequestBody @Valid ScadaInfoQueryDto queryDto);
    /**
     * 根据条件查询设备列表与设备状态
     * @param page 1 页码
     *             limit 10 每页数量
     *             org_id 组织ID
     *             subarea_cid 0.001.001.001-0.001 区域CID
     *             fault_state 1 故障
     *             alarm_state 1 告警
     */
    @GetMapping("/base/device/StateListPage")
    public AjaxResultVO StateListPage(@RequestParam Map<String, Object> inMap);
    /**
     * 查询指定设备的相关scada数据参数以及状态
     * device_id 1203022680538746880
     * page
     * limit
     */
    @GetMapping("/base/device/data/listOneDeviceDataOrState")
    public AjaxResultVO listOneDeviceDataOrState(@RequestParam Map<String, Object> inMap);
    /**
     * 设备报警统计
     * subarea_cid
     * device_id
     * date
     * start_date
     * end_date
     */
//    @GetMapping("/out/deviceAlarmCount")
//    public List<BDeviceEventCountEntity> deviceAlarmCount(@RequestParam Map<String, Object> inMap);

    /**
     * 根据条件查询设备事件列表:
     * @param inMap
     *  必选条件：
     *      org_id ：单位id
     *      start_time：起始时间
     *
     *  可选：
     *      system_type：系统类型
     *      subarea_cid：设备分区
     *      event_level：事项等级
     *      event_state：事项状态0（恢复正常）,1（异常）
     *      device_id：设备id
     *      process_state：处理状态0,1
     *      end_time：截止时间
     *
     * @return
     */
    @RequestMapping("/base/device/event/listPage")
    public AjaxResultVO eventListPage(@RequestParam Map<String, Object> inMap);

    /**
     * 处理设备事件
     * @param ids
     * @return
     */
    @RequestMapping("/base/device/event/processEvent")
    public AjaxResultVO processEvent(@RequestParam("ids") String ids);

    /**
     * 统计单位设备故障次数
     *
     * @param startDate     起始日期
     * @param endDate       截止日期
     * @param tunnelCidList 单位cid队列0.001.001.001 通过","分割的字符串
     * @return
     */
    @GetMapping("/tunnel/statistics/getTunnelsFaultCount")
    public AjaxResultVO getTunnelFaultCount(@RequestParam String startDate,
                                            @RequestParam String endDate,
                                            @RequestParam List<String> tunnelCidList);

    /**
     * 查询设备关联摄像机及预置位组织树
     * @param deviceId 设备ID
     * @return
     */
    @GetMapping("/device/camera/listCameraAndPresetByDevice")
    public AjaxResultVO listCameraAndPresetByDevice(@RequestParam(value = "device_id") String deviceId);

}
