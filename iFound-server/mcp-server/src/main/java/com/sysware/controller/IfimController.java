package com.sysware.controller;

import com.sysware.swcloud.common.core.vo.Res;
import com.sysware.swcloud.domian.issues.RepairIssueCreateCommand;
import com.sysware.swcloud.domian.issues.RepairOrderCloseCommand;
import com.sysware.swcloud.domian.issues.RepairOrderCreateCommand;
import com.sysware.swcloud.feign.IfimClient;
import com.sysware.swcloud.feign.ScadaClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@Tag(name = "设备管理接口", description = "mcp接口")
public class IfimController {

    @Autowired
    private IfimClient ifimClient;

    @Autowired
    private ScadaClient scadaClient;


    /**
     * 分页查询维修人员列表
     *
     * @param repairStaffListDto 查询参数，支持当前页、每页数量、用户ID、启用状态、技能描述、用户ID集合和组织CID过滤
     * @return 维修人员分页结果
     */
//        @Operation(summary = "分页查询维修人员列表", description = "支持按用户ID、启用状态、技能经验描述、用户ID集合、组织CID等条件筛选")
//        @GetMapping("/ifim/repair/staff")
//        public AjaxResultVO getRepairStaffList(RepairStaffListDto repairStaffListDto) {
//            return ifimClient.getRepairStaffList(repairStaffListDto);
//        }
    /**
     * 创建设备维修问题上报
     *
     * @param repairIssueCreateCommand 设备维修问题参数，包含问题等级、问题标题、问题描述、资产ID、发现时间和上报人信息
     * @return 问题上报结果
     */
//        @Operation(summary = "创建设备维修问题上报", description = "提交维修问题信息，创建一条问题上报记录")
//        @PostMapping("/ifim/repair/issue")
//        public AjaxResultVO creatRepairIssue(@RequestBody RepairIssueCreateCommand repairIssueCreateCommand) {
//            return ifimClient.creatRepairIssue(repairIssueCreateCommand);
//        }

    /**
     * 创建问题上报并触发AI诊断生成工单
     *
     * @param repairIssueCreateCommand 设备维修问题参数，包含问题等级、问题标题、问题描述、资产ID、发现时间和上报人信息
     * @return AI诊断及工单创建结果
     */
    @Operation(summary = "创建问题上报并AI诊断生成工单", description = "创建维修问题后同步触发AI分析，自动生成维修工单并返回工单相关信息")
    @PostMapping("/ifim/repair/issue/action/create-and-ai-diagnose-order")
    public Res createRepairIssueAiDiag(@RequestBody RepairIssueCreateCommand repairIssueCreateCommand) {
        return ifimClient.createRepairIssueAiDiag(repairIssueCreateCommand);
    }

    /**
     * 分页查询维修工单列表
     *
     * @param current         当前页码
     * @param size            每页显示条数
     * @param assetId         资产ID（即设备ID），可查出该设备下的维修工单
     * @param executeUserId   责任人ID
     * @param status          工单状态，多个用逗号分隔（10-待派单、20-待确认、30-处理中、40-待验收、50-已完成）
     * @param repairResult    维修结果（10-故障排除、20-无法维修、30-报废、40-其他）
     * @param createTimeBegin 创建开始时间，格式 yyyy-MM-dd HH:mm:ss
     * @param createTimeEnd   创建结束时间，格式 yyyy-MM-dd HH:mm:ss
     * @param areaCid         区域级联ID
     * @param specSystemCid   系统级联ID
     * @return 工单分页数据（data.records 工单列表 + data.total 总记录数 + data.pages 总页数）
     */
    @Operation(summary = "分页查询维修工单", description = "支持按资产/设备ID、责任人ID、工单状态、维修结果、创建时间范围、区域级联ID、系统级联ID筛选，返回工单分页数据")
    @GetMapping("/ifim/repair/orders")
    public Res getRepairOrderPage(
            @RequestParam(value = "current", required = false) String current,
            @RequestParam(value = "size", required = false) String size,
            @RequestParam(value = "assetId", required = false) String assetId,
            @RequestParam(value = "executeUserId", required = false) String executeUserId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "repairResult", required = false) String repairResult,
            @RequestParam(value = "createTimeBegin", required = false) String createTimeBegin,
            @RequestParam(value = "createTimeEnd", required = false) String createTimeEnd,
            @RequestParam(value = "areaCid", required = false) String areaCid,
            @RequestParam(value = "specSystemCid", required = false) String specSystemCid) {
        return ifimClient.getRepairOrderPage(current, size, assetId, executeUserId, status, repairResult,
                createTimeBegin, createTimeEnd, areaCid, specSystemCid);
    }

    /**
     * 强制关闭某设备的所有工单
     *
     * @param assetId 设备资产ID
     * @param command 关闭参数，包含关闭原因和资产使用状态
     * @return 操作结果
     */
    @Operation(summary = "强制关闭设备所有工单", description = "根据设备资产ID强制关闭该设备的所有维修工单，需指定关闭原因和资产使用状态")
    @PutMapping("/ifim/repair/orders/{assetId}/action/close")
    public Res closeRepairOrders(@PathVariable("assetId") String assetId, @RequestBody RepairOrderCloseCommand command) {
        return ifimClient.closeRepairOrders(assetId, command);
    }

    /**
     * 直接创建维修工单（无AI诊断）
     *
     * @param command 创建工单参数，包含问题等级、问题标题、问题描述、资产ID、发现时间、上报人信息、故障原因、处理建议、排查步骤等
     * @return 创建结果，包含问题ID、工单ID和工单编号
     */
    @Operation(summary = "直接创建维修工单", description = "无需AI诊断，直接创建维修工单并返回问题ID、工单ID和工单编号")
    @PostMapping("/ifim/repair/orders")
    public Res createRepairOrder(@RequestBody RepairOrderCreateCommand command) {
        return ifimClient.createRepairOrder(command);
    }

}
