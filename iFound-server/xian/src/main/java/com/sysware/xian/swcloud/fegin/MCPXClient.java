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
import io.swagger.v3.oas.annotations.Operation;
import org.dromara.hutool.core.bean.BeanUtil;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@FeignClient(
        name = "mcp-server",
        fallback = MCPClientHystrix.class
)
public interface MCPXClient {


    /**
     * 下发遥控
     */
    @PostMapping("/ykyt/sendCommand")
    public AjaxResultVO sendYkYtCommand(@RequestBody ScadaYkSendCommand command);

    /**
     * 获取区域态势，添加了区域坐标（未使用）
     *
     * @param orgId
     * @param subareaCid
     * @return
     */
    @GetMapping("/subareaState")
    public R<List<HashMap<String, Object>>> getSubareaState(
            @RequestParam(value = "orgId", defaultValue = "3", required = false) String orgId,
            @RequestParam(value = "subareaCid", required = false) String subareaCid);

    /**
     * 获取总设备健康率
     */
    @Operation(summary = "获取总设备健康率", description = "获取所有设备的健康率、故障数、告警数")
    @GetMapping("/base/device/healthRate")
    public AjaxResultVO getDeviceHealthRate(
            @RequestParam(value = "org_id", defaultValue = "3") String orgId,
            @RequestParam(value = "device_type", required = false) String device_type,
            @RequestParam(value = "system_type", required = false) String system_type,
            @RequestParam(value = "is_env", defaultValue = "0") String is_env);

    /**
     * 创建问题上报并AI诊断生成工单
     */
    @Operation(summary = "创建问题上报并AI诊断生成工单", description = "创建维修问题后同步触发AI分析，自动生成维修工单并返回工单相关信息")
    @PostMapping(value = "/ifim/repair/issue/action/create-and-ai-diagnose-order")
    AjaxResultVO createRepairIssueAiDiag(@RequestBody RepairIssueCreateCommand repairIssueCreateCommand);

    /**
     * 应急预案知识库问答
     */
    @Operation(summary = "应急预案知识库问答", description = "向应急预案知识库提问并获取答案")
    @PostMapping("/rag/ops-knowledge")
    KnowledgeResponse opsKnowledge(@RequestBody KnowledgeRequest request);

    /**
     * 机电设备运维规范与故障处理知识库问答
     */
    @Operation(summary = "机电设备运维规范与故障处理问答", description = "向机电设备运维规范与故障处理知识库提问并获取答案")
    @PostMapping("/rag/device-fault")
    KnowledgeResponse deviceFault(@RequestBody KnowledgeRequest request);

    /**
     * 查询设备列表（聚合设备数据，不支持过滤告警和故障设备）
     */
    @Operation(summary = "查询设备,聚合设备数据，不支持过滤告警和故障设备")
    @PostMapping("/base/device/StateListPageV2")
    AjaxResultVO StateListPageV2(@RequestBody ScadaInfoQueryDto queryDto);

    /**
     * 查询设备列表和状态
     * 支持按组织、区域、系统类型、设备类型、设备名称、故障状态、告警状态等条件筛选。
     * 当 addChangeStateTime 为 1 且查询故障/告警设备时，会在返回设备数据中补充最近变更时间。
     */
    @Operation(summary = "查询设备列表和状态", description = "支持按组织、区域、系统类型、设备类型、设备名称、故障状态、告警状态等条件筛选")
    @PostMapping("/base/device/StateListPage")
    AjaxResultVO StateListPage(@RequestBody ScadaPageQueryDto dto);

    /**
     * 查询指定设备相关的scada数据参数以及状态
     * device_id 1203022680538746880
     * page 1
     * limit 50
     */
    @Operation(summary = "查询指定设备相关的scada数据参数以及状态", description = "根据设备ID分页查询该设备相关的scada数据参数以及状态")
    @GetMapping("/base/device/data/listOneDeviceDataOrState")
    AjaxResultVO listOneDeviceDataOrState(
            @RequestParam(value = "page") String page,
            @RequestParam(value = "limit") String limit,
            @RequestParam(value = "device_id") String deviceId);

    /**
     * 根据区域编码查询客流区域信息（含摄像头流地址等）
     */
    @Operation(summary = "查询客流区域信息", description = "根据区域编码查询客流区域信息，含摄像头流地址、标定矩阵等")
    @GetMapping("/passenger-flow/areas")
    AjaxResultVO getPassengerAreas(@RequestParam("code") String code);

    /**
     * 获取报表聚合数据（7 路接口并发聚合：设备健康率/区域态势/故障设备/严重事件/实时客流/累计进出站客流/拥堵区域）
     * <p>
     * 返回 data 包含以下模块：
     * <ul>
     *   <li>subareaState：区域态势列表，首元素为全站设备/环境/能耗数据</li>
     *   <li>stationRealtime：车站实时客流概览（stationCurrPeo 在站总人数）</li>
     *   <li>securityCheckCurrent：当前安检人数（allCount 站内人数、checkedCount 已安检、uncheckedCount 未安检）</li>
     *   <li>stationInOutTotal：累计进出站人数（entryCount 进站/exitCount 出站，统计区间 2026-07-01 至当前）</li>
     *   <li>crowdedAreas：拥堵区域列表</li>
     *   <li>crowdedAreaCount：拥堵区域数量</li>
     * </ul>
     */
    @Operation(summary = "报表数据", description = "后端并发聚合 7 路接口数据：设备健康率、区域态势、故障设备、严重事件、实时客流、累计进出站客流、拥堵区域")
    @GetMapping("/getReportData")
    R<Map<String, Object>> getReportData();


    /**
     * 获取所有客流区域
     *
     * @return 客流区域
     */
    @GetMapping("/passenger-areas/all")
    Res<List<PassengerAreaVO>> getALLPassengerAreas();

    /**
     * 强制关闭某设备的所有工单
     */
    @Operation(summary = "强制关闭设备所有工单", description = "根据设备资产ID强制关闭该设备的所有维修工单，需指定关闭原因和资产使用状态")
    @PutMapping("/ifim/repair/orders/{assetId}/action/close")
    Res closeRepairOrders(@PathVariable("assetId") String assetId, @RequestBody RepairOrderCloseCommand command);

    /**
     * 直接创建维修工单（无AI诊断）
     */
    @Operation(summary = "直接创建维修工单", description = "无需AI诊断，直接创建维修工单并返回问题ID、工单ID和工单编号")
    @PostMapping("/ifim/repair/orders")
    Res createRepairOrder(@RequestBody RepairOrderCreateCommand command);

    /**
     * 查询摄像头目录。数据来自 mcp-server 的 VideoController。
     */
    @GetMapping("/camera/listPage")
    AjaxResultVO listCameraPage(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam("page") String page,
            @RequestParam("limit") String limit);

    /**
     * 获取设备系统枚举；data 中以 element_key 为键、element_value 为值。
     */
    @GetMapping("/device/enum/getEnumKeyValues")
    AjaxResultVO getDeviceSystemEnums(@RequestParam(value = "key", defaultValue = "device_system_type") String key);

}
