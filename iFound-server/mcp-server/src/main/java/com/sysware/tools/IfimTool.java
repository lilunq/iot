package com.sysware.tools;

import com.sysware.swcloud.common.core.vo.Res;
import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.R;
import com.sysware.swcloud.domian.issues.RepairIssueCreateCommand;
import com.sysware.swcloud.domian.ifim.RepairStaffListDto;
import com.sysware.swcloud.feign.IfimClient;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hutool.core.text.StrUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class IfimTool {

    @Autowired
    private IfimClient ifimClient;

    /**
     * 分页查询维修人员列表（平铺参数，供 MCP/LLM 调用）。
     * <p>
     * 仅暴露查询条件字段；records/total 等响应字段不要求模型传入。
     */
//    @Tool(name = "get_repair_staff_list", description = "分页查询维修人员列表")
    public AjaxResultVO getRepairStaffList(
            @ToolParam(description = "当前页码，从1开始") Integer current,
            @ToolParam(description = "每页显示条数") Integer size,
            @ToolParam(description = "组织Cid过滤（支持前缀匹配），非必填", required = false) String orgCid,
            @ToolParam(description = "启用状态，非必填", required = false) String activeFlag,
            @ToolParam(description = "系统用户ID，非必填", required = false) String userId,
            @ToolParam(description = "技能经验描述（模糊查询），非必填", required = false) String skillsDesc,
            @ToolParam(description = "用户ID集合过滤，非必填", required = false) String userIds) {

        RepairStaffListDto repairListDto = RepairStaffListDto.builder()
                .current(current)
                .size(size)
                .orgCid(orgCid)
                .activeFlag(activeFlag)
                .userId(userId)
                .skillsDesc(skillsDesc)
                .userIds(userIds)
                .build();
        log.info("mcp 调用分页查询维修人员列表");
        return ifimClient.getRepairStaffList(repairListDto);
    }

    /**
     * 设备维修创建问题上报
     */
//    @Tool(name = "create_repair_issue", description = "设备维修创建问题上报")
//    public AjaxResultVO creatRepairIssue(RepairIssueCreateCommand repairIssueCreateCommand) {
//        System.out.println("mcp 调用设备维修创建问题上报");
//        return ifimClient.creatRepairIssue(repairIssueCreateCommand);
//    }

    /**
     * 创建问题上报的同时AI分析并生成工单（平铺参数）。
     */
    @Tool(name = "createRepairIssueAiDiag", description = "创建问题上报的同时AI分析并生成工单，返回创建的工单信息，包括创建人，关联问题ID，关联工单ID，优先级，预计到达时间，AI匹配的维修人员ID等")
    public Res createRepairIssueAiDiag(
            @ToolParam(description = "问题等级,可用值:LOW,MEDIUM,HIGH,CRITICAL") String issueLevel,
            @ToolParam(description = "问题名称") String issueTitle,
            @ToolParam(description = "问题详细描述") String issueContent,
            @ToolParam(description = "关联资产ID") String assetId,
            @ToolParam(description = "上报人ID") String userId,
            @ToolParam(description = "上报人姓名") String userName,
            @ToolParam(description = "上报人组织CID") String orgCid,
            @ToolParam(description = "发现问题时间，格式 yyyy-MM-dd HH:mm:ss，不传则取当前时间", required = false) String foundTime) {

        RepairIssueCreateCommand repairIssueCreateCommand = RepairIssueCreateCommand.builder()
                .issueLevel(issueLevel)
                .issueTitle(issueTitle)
                .issueContent(issueContent)
                .assetId(assetId)
                .userId(userId)
                .userName(userName)
                .orgCid(orgCid)
                .foundTime(foundTime)
                .build();

        log.debug("mcp 调用创建问题上报的同时AI分析并生成工单");
        if (StrUtil.isBlank(repairIssueCreateCommand.getFoundTime())) {
            repairIssueCreateCommand.setFoundTime(
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
        }
        return ifimClient.createRepairIssueAiDiag(repairIssueCreateCommand);
    }

    /**
     * 根据工单查询详情数据
     *
     * @param id 工单id
     * @return 工单详情数据
     */
   @Tool(name = "getOrderById", description = "根据id查询工单详情信息")
    public Res getOrderById(@ToolParam(description = "工单ID") String id) {
        log.debug("mcp 调用根据id查询工单详情信息");
        return ifimClient.getOrderById(id);
    }

    /**
     * 分页查询维修工单列表（平铺参数，供 MCP/LLM 调用）。
     * <p>
     * 可用于查询工单、某设备（资产）下的维修工单、历史工单、按工单状态/维修结果/创建时间范围筛选。
     * 返回原始分页数据：data.records 为工单列表（orderNo 工单编号、orderTitle 工单标题、status 状态、
     * executeUserName 责任人、createTime 创建时间、finishTime 完成时间、repairResult 维修结果、
     * orderProgress 进度百分比等），data.total 总记录数，data.pages 总页数。
     */
    @Tool(name = "getRepairOrderPage", description = "分页查询维修工单列表，可按资产/设备ID、责任人、工单状态、维修结果、创建时间范围、区域、系统筛选。"
            + "工单状态：10=待派单，20=待确认，30=处理中，40=待验收，50=已完成；维修结果：10=故障排除，20=无法维修，30=报废，40=其他。"
            + "返回原始分页数据（data.records 工单列表 + data.total 总记录数 + data.pages 总页数）")
    public Res getRepairOrderPage(
            @ToolParam(description = "当前页码，从1开始，非必填", required = false) String current,
            @ToolParam(description = "每页显示条数，非必填", required = false) String size,
            @ToolParam(description = "资产ID（即设备ID），传值时查询该设备下的维修工单，非必填", required = false) String assetId,
            @ToolParam(description = "责任人ID，非必填", required = false) String executeUserId,
            @ToolParam(description = "工单状态，多个用逗号分隔，如10,20；10=待派单，20=待确认，30=处理中，40=待验收，50=已完成，非必填", required = false) String status,
            @ToolParam(description = "维修结果：10=故障排除，20=无法维修，30=报废，40=其他，非必填", required = false) String repairResult,
            @ToolParam(description = "创建开始时间，格式 yyyy-MM-dd HH:mm:ss，例如2026-11-06 10:01:51，非必填", required = false) String createTimeBegin,
            @ToolParam(description = "创建结束时间，格式 yyyy-MM-dd HH:mm:ss，例如2026-11-07 10:00:00，非必填", required = false) String createTimeEnd,
            @ToolParam(description = "区域级联ID，非必填", required = false) String areaCid,
            @ToolParam(description = "系统级联ID，非必填", required = false) String specSystemCid) {
        log.debug("mcp 调用分页查询维修工单: current={}, size={}, assetId={}, status={}, repairResult={}, "
                        + "createTimeBegin={}, createTimeEnd={}, areaCid={}, specSystemCid={}",
                current, size, assetId, status, repairResult, createTimeBegin, createTimeEnd, areaCid, specSystemCid);
        return ifimClient.getRepairOrderPage(current, size, assetId, executeUserId, status, repairResult,
                createTimeBegin, createTimeEnd, areaCid, specSystemCid);
    }
}
