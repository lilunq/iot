package com.sysware.swcloud.feign;

import com.sysware.swcloud.common.core.vo.Res;
import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.ifim.RepairStaffListDto;
import com.sysware.swcloud.domian.issues.RepairIssueCreateCommand;
import com.sysware.swcloud.domian.issues.RepairOrderCloseCommand;
import com.sysware.swcloud.domian.issues.RepairOrderCreateCommand;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "iotBuilder-ifim",
        fallback = IfimClientHystrix.class
)
public interface IfimClient {
    /**
     * 分页查询维修人员列表
     *
     * @param repairListDto 查询参数
     * @return 查询结果
     */
    @GetMapping("/v1/repair/staff")
    AjaxResultVO getRepairStaffList(RepairStaffListDto repairListDto);


    /**
     * 创建问题上报的同时AI分析并生成工单
     {
     "issueLevel": "CRITICAL",  LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '紧急'
     "issueTitle": "故障",
     "issueContent": "设备故障",
     "assetId": "1203022680048013312",
     "foundTime": "2026-06-14 00:00:00",
     "userId": "9",
     "userName": "开发",
     "orgCid": "0.001.001.001"
     }
     * @param command 设备维修问题参数
     * @return 创建的工单信息，包括创建人，关联问题ID，关联工单ID，优先级，预计到达时间，AI匹配的维修人员ID等
     */
    @PostMapping(value = "/v1/repair/issue/action/create-and-ai-diagnose-order", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Res createRepairIssueAiDiag(RepairIssueCreateCommand command);

    /**
     * 根据工单查询详情数据
     * @param id 工单id
     * @return 工单详情数据
     */
    @GetMapping("/v1/repair/orders/{id}")
    Res getOrderById(@PathVariable String id);

    /**
     * 分页查询维修工单列表
     * <p>
     * 请求方式：GET /v1/repair/orders，参数通过 query 传递（application/x-www-form-urlencoded）
     * <p>
     * 查询参数（均为非必填）：
     * <ul>
     *   <li>current：当前页码</li>
     *   <li>size：每页显示条数</li>
     *   <li>assetId：资产ID（即设备ID），可查出该设备下的维修工单</li>
     *   <li>executeUserId：责任人ID</li>
     *   <li>status：工单状态，多个用逗号分隔（如 10,20,30）。含义：10-待派单、20-待确认、30-处理中、40-待验收、50-已完成</li>
     *   <li>repairResult：维修结果。含义：10-故障排除、20-无法维修、30-报废、40-其他</li>
     *   <li>createTimeBegin：创建开始时间，格式 yyyy-MM-dd HH:mm:ss（例如 2026-11-06 10:01:51）</li>
     *   <li>createTimeEnd：创建结束时间，格式 yyyy-MM-dd HH:mm:ss（例如 2026-11-07 10:00:00）</li>
     *   <li>areaCid：区域级联ID</li>
     *   <li>specSystemCid：系统级联ID</li>
     * </ul>
     * <p>
     * 响应：data 为 PageRepairOrderVO，records 为工单列表（RepairOrderVO，含 orderNo 工单编号、
     * orderTitle 工单标题、status 工单状态、executeUserName 责任人姓名、createTime 创建时间、
     * finishTime 完成时间、repairResult 维修结果、orderProgress 工单进度百分比、overdueFlag 超期标记等），
     * total 总记录数、pages 总页数、current 当前页码、size 每页条数。
     *
     * @param current        当前页码
     * @param size           每页显示条数
     * @param assetId        资产ID（即设备ID）
     * @param executeUserId  责任人ID
     * @param status         工单状态，多个用逗号分隔（10-待派单、20-待确认、30-处理中、40-待验收、50-已完成）
     * @param repairResult   维修结果（10-故障排除、20-无法维修、30-报废、40-其他）
     * @param createTimeBegin 创建开始时间，格式 yyyy-MM-dd HH:mm:ss
     * @param createTimeEnd   创建结束时间，格式 yyyy-MM-dd HH:mm:ss
     * @param areaCid        区域级联ID
     * @param specSystemCid  系统级联ID
     * @return 工单分页数据（data.records + data.total/pages）
     */
    @GetMapping("/v1/repair/orders")
    Res getRepairOrderPage(
            @RequestParam(value = "current", required = false) String current,
            @RequestParam(value = "size", required = false) String size,
            @RequestParam(value = "assetId", required = false) String assetId,
            @RequestParam(value = "executeUserId", required = false) String executeUserId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "repairResult", required = false) String repairResult,
            @RequestParam(value = "createTimeBegin", required = false) String createTimeBegin,
            @RequestParam(value = "createTimeEnd", required = false) String createTimeEnd,
            @RequestParam(value = "areaCid", required = false) String areaCid,
            @RequestParam(value = "specSystemCid", required = false) String specSystemCid);

    /**
     * 强制关闭某设备的所有工单
     *
     * @param assetId 设备资产ID
     * @param command 关闭参数，包含关闭原因和资产使用状态
     * @return 操作结果
     */
    @PutMapping(value = "/v1/repair/orders/{assetId}/action/close")
    Res closeRepairOrders(@PathVariable("assetId") String assetId, @RequestBody RepairOrderCloseCommand command);

    /**
     * 直接创建维修工单（无AI诊断）
     *
     * @param command 创建工单参数，包含问题等级、问题标题、问题描述、资产ID、发现时间、上报人信息、故障原因、处理建议、排查步骤等
     * @return 创建结果，包含问题ID、工单ID和工单编号
     */
    @PostMapping(value = "/v1/repair/orders")
    Res createRepairOrder(@RequestBody RepairOrderCreateCommand command);
}
