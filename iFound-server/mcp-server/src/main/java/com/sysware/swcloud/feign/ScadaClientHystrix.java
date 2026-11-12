package com.sysware.swcloud.feign;

import com.sysware.swcloud.domian.*;
import com.sysware.swcloud.domian.scada.ScadaInfoQueryDto;
import jakarta.validation.Valid;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ScadaClientHystrix implements ScadaClient {
//
//    /**
//     *
//     * @param inMap name名称
//     * @return
//     */
    @Override
    @GetMapping("/scene/scenePageList")
    public AjaxResultVO listScene(@RequestParam Map<String, Object> inMap){
        return AjaxResultVO.error("查询失败");
    }
    /**
     *查询场景的控制参数
     * @param inMap name名称
     * @return
     */
    @GetMapping("/scene/sceneParamList")
    public AjaxResultVO sceneParamList(@RequestParam Map<String, Object> inMap){
        return AjaxResultVO.error("查询失败");
    }
//
//    @Override
//    @PostMapping("/out/sceneEdit")
//    public String sceneEdit(ScadaSceneAllEntity entity) {
//        return "失败！";
//    }
//
//
//    /**
//     * 查询控制模式
//     * @param inMap name名称,time_mode=1 时间模式,logic_mode=1 逻辑模式,is_enabled=1 启用
//     */
//    @Override
//    @GetMapping("/out/controlModelList")
//    public List listControlModel(@RequestParam Map<String, Object> inMap){
//        return new ArrayList<>() ;
//    };
//
//    @Override
//    @PostMapping("/out/controlModelAdd")
//    public String controlModelAdd(@RequestBody ScadaControlModelEntity entity){return "失败！";};
//    /**
//     * 查询所有遥测遥信参数
//     * @param inMap rtu_no,name,data_no,is_enabled,yk_flag,save_curve
//     */
//    @Override
//    @GetMapping("/out/ycyxDataList")
//    public List ycyxDataList(@RequestParam Map<String, Object> inMap){
//        return new ArrayList<>() ;
//    };
//
//    /**
//     * 查询遥测遥信实时值
//     */
//    @Override
//    @PostMapping("/out/ycYxRealDataList")
//    public YcYxParam ycYxRealDataList(@RequestBody YcYxParam inMap){
//        return null;
//    }
    @Override
    @PostMapping("/base/device/StateListPageV2")
    public AjaxResultVO StateListPageV2(@RequestBody @Valid ScadaInfoQueryDto queryDto){
        return AjaxResultVO.error();
    };
//    @Override
//    @GetMapping("/out/bDeviceAllInfo")
//    public Map<String, Object> BDeviceAllInfo(@RequestParam Map<String, Object> inMap){
//        return null;
//    }
//
//    @Override
//    @GetMapping("/out/deviceAlarmCount")
//    public List<BDeviceEventCountEntity> deviceAlarmCount(Map<String, Object> inMap) {
//        return null;
//    }

    @Override
    @RequestMapping("/StateListPage")
    public AjaxResultVO StateListPage(@RequestParam Map<String, Object> inMap){return AjaxResultVO.error();};

    @Override
    @RequestMapping("/base/device/data/listOneDeviceDataOrState")
    public AjaxResultVO listOneDeviceDataOrState(@RequestParam Map<String, Object> inMap){return AjaxResultVO.error();};

    @Override
    @RequestMapping("/base/device/event/listPage")
    public AjaxResultVO eventListPage(@RequestParam Map<String, Object> inMap){return AjaxResultVO.error();};

    @Override
    @RequestMapping("/base/device/event/processEvent")
    public AjaxResultVO processEvent(@RequestParam String ids){return AjaxResultVO.error();};

    @Override
    @GetMapping("/tunnel/statistics/getTunnelsFaultCount")
    public AjaxResultVO getTunnelFaultCount(@RequestParam String startDate,
                                            @RequestParam String endDate,
                                            @RequestParam List<String> tunnelCidList){return AjaxResultVO.error();};
    @GetMapping("/device/camera/listCameraAndPresetByDevice")
    public AjaxResultVO listCameraAndPresetByDevice(@RequestParam(value = "device_id") String deviceId){
        return AjaxResultVO.error();
    };

}
