package com.sysware.xian.controller;

import com.sysware.swcloud.common.core.vo.Res;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import com.sysware.xian.swcloud.domain.issues.RepairIssueCreateCommand;
import com.sysware.xian.swcloud.domain.issues.RepairOrderCloseCommand;
import com.sysware.xian.swcloud.domain.issues.RepairOrderCreateCommand;
import com.sysware.xian.swcloud.fegin.MCPXClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 设备维修接口
 */
@Slf4j
@RestController
@RequestMapping("/ifim")
@Tag(name = "设备维修接口", description = "设备维修问题上报与AI诊断工单")
public class IfimController {

    @Autowired
    private MCPXClient mcpClient;

    /**
     * 创建问题上报并AI诊断生成工单
     *
     * @param repairIssueCreateCommand 设备维修问题参数，包含问题等级、问题标题、问题描述、资产ID、发现时间和上报人信息
     * @return AI诊断及工单创建结果
     */
    @Operation(summary = "创建问题上报并AI诊断生成工单", description = "创建维修问题后同步触发AI分析，自动生成维修工单并返回工单相关信息")
    @PostMapping("/repair/issue/action/create-and-ai-diagnose-order")
    public AjaxResultVO createRepairIssueAiDiag(@RequestBody RepairIssueCreateCommand repairIssueCreateCommand) {
        log.debug("接收到AI诊断工单创建请求: {}", repairIssueCreateCommand);
        return mcpClient.createRepairIssueAiDiag(repairIssueCreateCommand);
    }

    /**
     * 强制关闭某设备的所有工单
     *
     * @param assetId 设备资产ID
     * @param command 关闭参数，包含关闭原因和资产使用状态
     * @return 操作结果
     */
    @Operation(summary = "强制关闭设备所有工单", description = "根据设备资产ID强制关闭该设备的所有维修工单，需指定关闭原因和资产使用状态")
    @PutMapping("/repair/orders/{assetId}/action/close")
    public Res closeRepairOrders(@PathVariable("assetId") String assetId, @RequestBody RepairOrderCloseCommand command) {
        log.debug("接收到强制关闭设备工单请求: assetId={}, {}", assetId, command);
        return mcpClient.closeRepairOrders(assetId, command);
    }

    /**
     * 直接创建维修工单（无AI诊断）
     *
     * @param command 创建工单参数，包含问题等级、问题标题、问题描述、资产ID、发现时间、上报人信息、故障原因、处理建议、排查步骤等
     * @return 创建结果，包含问题ID、工单ID和工单编号
     */
    @Operation(summary = "直接创建维修工单", description = "无需AI诊断，直接创建维修工单并返回问题ID、工单ID和工单编号")
    @PostMapping("/repair/orders")
    public Res createRepairOrder(@RequestBody RepairOrderCreateCommand command) {
        log.debug("接收到直接创建工单请求: {}", command);
        return mcpClient.createRepairOrder(command);
    }
}
