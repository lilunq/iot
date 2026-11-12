package com.sysware.xian.swcloud.fegin;

import com.sysware.swcloud.common.core.vo.Res;
import com.sysware.xian.domain.videoAnalytics.PassengerAreaVO;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import com.sysware.xian.swcloud.domain.KnowledgeRequest;
import com.sysware.xian.swcloud.domain.KnowledgeResponse;
import com.sysware.xian.swcloud.domain.R;
import com.sysware.xian.swcloud.domain.ScadaInfoQueryDto;
import com.sysware.xian.swcloud.domain.ScadaPageQueryDto;
import com.sysware.xian.swcloud.domain.ScadaYkSendCommand;
import com.sysware.xian.swcloud.domain.issues.RepairIssueCreateCommand;
import com.sysware.xian.swcloud.domain.issues.RepairOrderCloseCommand;
import com.sysware.xian.swcloud.domain.issues.RepairOrderCreateCommand;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MCPClientHystrix implements MCPXClient {
    @Override
    @PostMapping("/ykyt/sendCommand")
    public AjaxResultVO sendYkYtCommand(@RequestBody ScadaYkSendCommand command) {
        return AjaxResultVO.error("下发失败");
    }

    ;

    @Override
    @GetMapping("/subareaState")
    public R<List<HashMap<String, Object>>> getSubareaState(
            @RequestParam(value = "org_id", defaultValue = "3", required = false) String orgId,
            @RequestParam(value = "subarea_cid", required = false) String subareaCid) {
        return R.error("请求失败");
    }

    ;

    @Override
    @GetMapping("/base/device/healthRate")
    public AjaxResultVO getDeviceHealthRate(
            @RequestParam(value = "org_id", defaultValue = "3") String orgId,
            @RequestParam(value = "device_type", required = false) String device_type,
            @RequestParam(value = "system_type", required = false) String system_type,
            @RequestParam(value = "is_env", defaultValue = "0") String is_env) {
        return AjaxResultVO.error();
    }

    @Override
    public AjaxResultVO createRepairIssueAiDiag(@RequestBody RepairIssueCreateCommand repairIssueCreateCommand) {
        return AjaxResultVO.error("服务降级：AI诊断工单创建失败");
    }

    @Override
    public KnowledgeResponse opsKnowledge(@RequestBody KnowledgeRequest request) {
        return KnowledgeResponse.error("服务降级：应急预案知识库问答失败");
    }

    @Override
    public KnowledgeResponse deviceFault(@RequestBody KnowledgeRequest request) {
        return KnowledgeResponse.error("服务降级：机电设备运维规范知识库问答失败");
    }

    @Override
    public AjaxResultVO StateListPageV2(@RequestBody ScadaInfoQueryDto queryDto) {
        return AjaxResultVO.error("查询设备列表失败");
    }

    @Override
    public AjaxResultVO StateListPage(@RequestBody ScadaPageQueryDto dto) {
        return AjaxResultVO.error("查询设备列表和状态失败");
    }

    @Override
    public AjaxResultVO listOneDeviceDataOrState(
            @RequestParam(value = "page") String page,
            @RequestParam(value = "limit") String limit,
            @RequestParam(value = "device_id") String deviceId) {
        return AjaxResultVO.error("查询指定设备scada数据参数及状态失败");
    }

    @Override
    public AjaxResultVO getPassengerAreas(@RequestParam("code") String code) {
        return AjaxResultVO.error("查询客流区域信息失败");
    }

    @Override
    @GetMapping("/getReportData")
    public R<Map<String, Object>> getReportData() {
        return R.error("获取报表数据失败");
    }

    @Override
    public Res<List<PassengerAreaVO>> getALLPassengerAreas() {
        return Res.failed();
    }

    @Override
    public Res closeRepairOrders(String assetId, RepairOrderCloseCommand command) {
        return Res.failed("服务降级：强制关闭设备工单失败");
    }

    @Override
    public Res createRepairOrder(RepairOrderCreateCommand command) {
        return Res.failed("服务降级：直接创建维修工单失败");
    }

    @Override
    public AjaxResultVO listCameraPage(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam("page") String page,
            @RequestParam("limit") String limit) {
        return AjaxResultVO.error("查询摄像头列表失败");
    }

    @Override
    public AjaxResultVO getDeviceSystemEnums(@RequestParam(value = "key", defaultValue = "device_system_type") String key) {
        return AjaxResultVO.error("查询设备系统枚举失败");
    }

}
