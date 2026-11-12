package com.sysware.swcloud.feign;

import com.sysware.swcloud.common.core.vo.Res;
import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.ifim.RepairStaffListDto;
import com.sysware.swcloud.domian.issues.RepairIssueCreateCommand;
import com.sysware.swcloud.domian.issues.RepairOrderCloseCommand;
import com.sysware.swcloud.domian.issues.RepairOrderCreateCommand;
import org.springframework.stereotype.Component;

@Component
public class IfimClientHystrix implements IfimClient {

    @Override
    public AjaxResultVO getRepairStaffList(RepairStaffListDto repairListDto) {
        return AjaxResultVO.error("分页查询维修人员列表失败");
    }
//
//    @Override
//    public AjaxResultVO creatRepairIssue(RepairIssueCreateCommand repairIssueCreateCommand) {
//        return AjaxResultVO.error("设备维修创建问题上报失败");
//    }

    @Override
    public Res createRepairIssueAiDiag(RepairIssueCreateCommand repairIssueCreateCommand) {
        return Res.failed("设备维修创建问题上报失败");
    }

    @Override
    public Res getOrderById(String id) {
        return Res.failed("查询工单详情失败");
    }

    @Override
    public Res getRepairOrderPage(String current, String size, String assetId, String executeUserId, String status, String repairResult, String createTimeBegin, String createTimeEnd, String areaCid, String specSystemCid) {
        return Res.failed("获取工单失败");
    }

    @Override
    public Res closeRepairOrders(String assetId, RepairOrderCloseCommand command) {
        return Res.failed("强制关闭设备工单失败");
    }

    @Override
    public Res createRepairOrder(RepairOrderCreateCommand command) {
        return Res.failed("直接创建维修工单失败");
    }
}
