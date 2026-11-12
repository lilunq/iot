package com.sysware.xian.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sysware.xian.domain.DispatchTask;
import com.sysware.xian.domain.DispatchTaskPageResponse;
import com.sysware.xian.domain.UserFeedbackRequest;
import com.sysware.xian.service.DispatchTaskService;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/dispatch")
@RequiredArgsConstructor
@Tag(name = "派发任务管理", description = "分析派发任务的查询和派发操作")
public class DispatchTaskController {

    private final DispatchTaskService dispatchTaskService;

    @GetMapping("/list")
    @Operation(summary = "获取任务列表（分页）")
    @ApiResponse(responseCode = "200", description = "成功，data为分页结果（records-任务列表、total-总数、size-每页条数、current-当前页、pages-总页数）",
            content = @Content(schema = @Schema(implementation = DispatchTaskPageResponse.class)))
    public AjaxResultVO list(@Parameter(description = "创建者Agent名称，模糊匹配", example = "系统Agent") @RequestParam(required = false) String creatorAgentName,
                             @Parameter(description = "目标Agent ID，精确匹配", example = "ops-agent") @RequestParam(required = false) String targetAgentId,
                             @Parameter(description = "目标Agent名称，模糊匹配", example = "设备运维Agent") @RequestParam(required = false) String targetAgentName,
                             @Parameter(description = "派发状态：0-未派发，1-已派发，2-已取消", example = "1") @RequestParam(required = false) Integer dispatched,
                             @Parameter(description = "业务域，精确匹配", example = "能耗") @RequestParam(required = false) String businessDomain,
                             @Parameter(description = "任务类型：hint-提示，collaboration-协同方案，normal-正常/误报", example = "collaboration") @RequestParam(required = false) String taskType,
                             @Parameter(description = "采纳状态：0-未采纳，1-已采纳，2-已驳回", example = "0") @RequestParam(required = false) String adoptStatus,
                             @Parameter(description = "执行结果状态：0-未完成，1-成功，2-失败", example = "1") @RequestParam(required = false) String resultExecuteStatus,
                             @Parameter(description = "区域级联ID，精确匹配", example = "0.001.001.001-0") @RequestParam(required = false) String subareaCid,
                             @Parameter(description = "决策内容，模糊匹配", example = "消防泵房") @RequestParam(required = false) String decisionContent,
                             @Parameter(description = "决策数据，精确匹配（如设备ID）", example = "1226586210521059328") @RequestParam( required = false) String decisionData,
                             @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") Integer pageNum,
                             @Parameter(description = "每页条数，默认10") @RequestParam(defaultValue = "10") Integer pageSize) {
        return AjaxResultVO.ok().data(dispatchTaskService.list(creatorAgentName, targetAgentId, targetAgentName, dispatched, businessDomain, taskType, adoptStatus, resultExecuteStatus, subareaCid, decisionContent, decisionData, pageNum, pageSize));
    }

    @GetMapping("/undispatched")
    @Operation(summary = "获取未派发任务列表")
    public AjaxResultVO undispatched() {
        return AjaxResultVO.ok().data(dispatchTaskService.listUndispatched());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取任务详情")
    public AjaxResultVO getById(@Parameter(description = "任务ID") @PathVariable String id) {
        DispatchTask task = dispatchTaskService.getById(id);
        return task != null ? AjaxResultVO.ok().data(task) : AjaxResultVO.error("任务不存在");
    }

    @PostMapping
    @Operation(summary = "创建任务")
    public AjaxResultVO create(@RequestBody DispatchTask task) {
        DispatchTask created = dispatchTaskService.createTask(task);
        return AjaxResultVO.ok().data(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新任务（根据传入字段部分更新）",
            description = "子Agent执行结果反馈等场景使用，仅更新非空字段。典型字段：completeTime-完成时间、resultExecuteUser-执行人、resultExecuteStatus-执行结果(1成功/2失败)、resultDesc-结果描述、resultData-结果原始数据(JSON)")
    public AjaxResultVO update(@Parameter(description = "任务ID") @PathVariable String id, @RequestBody DispatchTask task) {
        task.setId(id);
        boolean success = dispatchTaskService.updateById(task);
        return success ? AjaxResultVO.ok().data("更新成功") : AjaxResultVO.error("更新失败");
    }

    @PostMapping("/dispatch")
    @Operation(summary = "派发任务到子Agent",
            description = "复用采纳逻辑(adoptStatus=1)：落库dispatched=1+adopt_status=1，并POST完整任务体到子Agent的receiveTaskUrl。请求体字段：id-任务ID(必传)、dispatchedUser-派发人姓名")
    public AjaxResultVO dispatch(@RequestBody DispatchTask task) {
        DispatchTask body = new DispatchTask();
        body.setId(task.getId());
        body.setAdoptStatus("1");
        body.setAdoptUser(task.getDispatchedUser());
        return dispatchTaskService.adoptAndNotify(body);
    }

    @PostMapping("/cancel/{id}")
    @Operation(summary = "取消任务", description = "复用驳回逻辑(adoptStatus=2)：落库dispatched=2+adopt_status=2，并POST完整任务体到子Agent的receiveTaskUrl。请求体(可选)仅需传 dispatchedUser-操作人姓名")
    public AjaxResultVO cancel(@Parameter(description = "任务ID") @PathVariable String id, @RequestBody(required = false) DispatchTask body) {
        String user = body != null ? body.getDispatchedUser() : null;
        DispatchTask param = new DispatchTask();
        param.setId(id);
        param.setAdoptStatus("2");
        param.setAdoptUser(user);
        return dispatchTaskService.adoptAndNotify(param);
    }

    @GetMapping("/pending-insights")
    @Operation(summary = "获取各分区未完成任务洞察",
            description = "按subarea_cid分组返回最新的3条result_execute_status='0'的任务(id、problemDesc、decisionContent、creatorAgentId、targetAgentId)及各分区count总数，用于大屏AI洞察")
    public AjaxResultVO pendingInsights() {
        return AjaxResultVO.ok().data(dispatchTaskService.getSubareaPendingInsights());
    }

    @GetMapping("/agent-collab-stats")
    @Operation(summary = "获取Agent协作统计信息")
    public AjaxResultVO agentCollabStats() {
        return AjaxResultVO.ok().data(dispatchTaskService.getAgentCollabStats());
    }

    @GetMapping("/health-collab-evolution")
    @Operation(summary = "获取站房评分与Agent协作关系演变")
    public AjaxResultVO healthCollabEvolution(
            @Parameter(description = "组织ID，默认3") @RequestParam(value = "org_id", defaultValue = "3") String orgId,
            @Parameter(description = "统计时长（小时），默认48") @RequestParam(defaultValue = "48") Integer hours,
            @Parameter(description = "时间桶大小（分钟），默认1440即按天") @RequestParam(value = "bucket_minutes", defaultValue = "1440") Integer bucketMinutes,
            @Parameter(description = "派发状态筛选：0-未派发，1-已派发，2-已取消，不传查全部") @RequestParam(required = false) Integer dispatched) {
        return AjaxResultVO.ok().data(dispatchTaskService.getHealthCollabEvolution(
                orgId,
                hours == null ? 48 : hours,
                bucketMinutes == null ? 1440 : bucketMinutes,
                dispatched));
    }



    @PostMapping("/adopt/{id}")
    @Operation(summary = "采纳/驳回任务（仅collaboration类）",
            description = "adoptStatus(1采纳/2驳回)、adoptUser必传；可同步修订businessDomain、thinkingOverview、level、decisionContent、decisionData、expectedEffect、remark；落库后回调子Agent的采纳结果接收接口(adoptFeedbackUrl)")
    public AjaxResultVO adopt(@Parameter(description = "任务ID") @PathVariable String id,
                              @RequestBody DispatchTask body) {
        body.setId(id);
        return dispatchTaskService.adoptAndNotify(body);
    }

    //接收任务
    @PostMapping("/receive")
    @Operation(summary = "接收任务", description = "请求体仅需传 id-任务ID(必传)、dispatchedUser-派发人姓名，其余字段忽略。仅更新已存在任务的派发状态为已派发")
    public AjaxResultVO receive(@RequestBody DispatchTask task) {
        if (task.getId() != null) {
            boolean success = dispatchTaskService.receiveTaskById(task.getId(), task.getDispatchedUser());
            return success ? AjaxResultVO.ok().data("接收成功") : AjaxResultVO.error("接收失败");
        }
        return AjaxResultVO.error("任务ID不能为空");
    }

    @PostMapping("/user-feedback")
    @Operation(summary = "用户大屏回填",
            description = "如果是运维则会联动关闭工单。id、username必传；更新任务的user_feedback（用户反馈）、result_execute_status（1-处理完成/2-处理失败），并将result_execute_user置为username")
    public AjaxResultVO userFeedback(@RequestBody UserFeedbackRequest req) {
        return dispatchTaskService.userFeedback(req.getId(), req.getUsername(), req.getUserFeedback(), req.getResultExecuteStatus());
    }

    @PostMapping("/report")
    @Operation(summary = "子Agent上报问题+决策",
            description = "creatorAgentId(不能为commander)、businessDomain、problemDesc、taskType必传；taskType枚举：hint-提示、collaboration-协同方案、normal-正常/误报；normal类入库即完结，collaboration类置待采纳")
    public AjaxResultVO report(@RequestBody DispatchTask task) {
        return dispatchTaskService.reportTask(task);
    }

    @PostMapping("/analysis-result/{id}")
    @Operation(summary = "子Agent回传AI分析结果",
            description = "taskType、decisionContent必传；taskType枚举：hint-提示(存档)、collaboration-协同方案(置待采纳)、normal-正常/误报(自动完结)；可选字段：decisionData、thinkingOverview、level、expectedEffect")
    public AjaxResultVO analysisResult(@Parameter(description = "任务ID") @PathVariable String id, @RequestBody DispatchTask body) {
        return dispatchTaskService.saveAnalysisResult(id, body);
    }



}
