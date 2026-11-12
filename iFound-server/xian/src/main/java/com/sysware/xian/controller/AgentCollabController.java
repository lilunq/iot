package com.sysware.xian.controller;

import com.sysware.xian.domain.AdoptNotifyRequest;
import com.sysware.xian.domain.DispatchTask;
import com.sysware.xian.domain.collab.AutoControlRequest;
import com.sysware.xian.domain.collab.CollabAdoptRequest;
import com.sysware.xian.domain.collab.CollabAnalysisResultRequest;
import com.sysware.xian.domain.collab.CollabCreateTaskRequest;
import com.sysware.xian.domain.collab.CollabDispatchRequest;
import com.sysware.xian.domain.collab.CollabReportRequest;
import com.sysware.xian.domain.collab.CollabResultFeedbackRequest;
import com.sysware.xian.service.DispatchTaskService;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Agent协作Controller：按步骤顺序提供两种协作场景的接口
 * 场景1（系统Agent → 子Agent）：创建 → 派发 → 子Agent回传分析结果 → 用户采纳/驳回(回调子Agent) → 子Agent执行结果反馈
 * 场景2（子Agent → 系统Agent）：子Agent上报 → 用户采纳/驳回(回调子Agent) → 子Agent执行结果反馈
 */
@Slf4j
@RestController
@RequestMapping("/collab")
@RequiredArgsConstructor
@Tag(name = "Agent协作", description = "按步骤执行Agent协作全流程。场景1：系统Agent派发给子Agent（step1创建→step2派发→step3回传分析→step4采纳→step5结果反馈）；场景2：子Agent上报（step1上报→step2采纳→step3结果反馈）")
public class AgentCollabController {

    private final DispatchTaskService dispatchTaskService;
    private final StringRedisTemplate redisTemplate;

    /** taskType合法取值: hint-提示, collaboration-协同方案, normal-正常/误报 */
    private static final Set<String> TASK_TYPES = Set.of("hint", "collaboration", "normal");

    /** 自动控制开关的Redis Key（value: "1"开启/"0"关闭） */
    private static final String AUTO_CONTROL_ENABLED_KEY = "collab:auto-control:enabled";
    /** 自动控制操作人的Redis Key（开启自动采纳时作为任务采纳人） */
    private static final String AUTO_CONTROL_USERNAME_KEY = "collab:auto-control:username";
    /** 开启自动控制时，目标Agent为该ID的上报任务自动采纳 */
    private static final String AUTO_ADOPT_AGENT_ID = "energy-agent";
    /** 自动采纳首次下发延迟（秒） */
    private static final long AUTO_ADOPT_INITIAL_DELAY_SECONDS = 2L;
    /** 自动采纳下发失败后的最大重试次数 */
    private static final int AUTO_ADOPT_MAX_RETRIES = 3;
    /** 自动采纳下发失败后的重试间隔（秒） */
    private static final long AUTO_ADOPT_RETRY_INTERVAL_SECONDS = 1L;

    /** 自动采纳延迟下发调度器：上报接口先返回结果，延迟2秒后由该后台线程调度采纳并回调子Agent，失败按间隔重试 */
    private final ScheduledExecutorService autoAdoptScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "collab-auto-adopt");
        t.setDaemon(true);
        return t;
    });

    // ==================== 场景1：系统Agent → 子Agent ====================

    @PostMapping("/scene1/step1-create")
    @Operation(summary = "场景1-①系统Agent创建决策任务",
            description = "系统Agent(commander)分析生成决策任务并入库（dispatched=0未派发）。返回完整任务（含id），后续步骤都用该id")
    public AjaxResultVO scene1Step1Create(@RequestBody CollabCreateTaskRequest req) {
        if (req.getTargetAgentId() == null || req.getTargetAgentId().isBlank()) {
            return AjaxResultVO.error("targetAgentId不能为空");
        }
        if (req.getBusinessDomain() == null || req.getBusinessDomain().isBlank()) {
            return AjaxResultVO.error("businessDomain不能为空");
        }
        if (req.getProblemDesc() == null || req.getProblemDesc().isBlank()) {
            return AjaxResultVO.error("problemDesc不能为空");
        }
        if (req.getTaskType() == null || !TASK_TYPES.contains(req.getTaskType())) {
            return AjaxResultVO.error("taskType必须为hint/collaboration/normal之一");
        }
        DispatchTask task = new DispatchTask();
        task.setCreatorAgentId("commander");
        task.setCreatorAgentName("系统Agent");
        task.setTargetAgentId(req.getTargetAgentId());
        task.setTargetAgentName(req.getTargetAgentName());
        task.setBusinessDomain(req.getBusinessDomain());
        task.setProblemDesc(req.getProblemDesc());
        task.setTaskType(req.getTaskType());
        task.setDecisionContent(req.getDecisionContent());
        task.setDecisionData(req.getDecisionData());
        DispatchTask created = dispatchTaskService.createTask(task);
        return AjaxResultVO.ok().data(created);
    }

    @PostMapping("/scene1/step2-dispatch")
    @Operation(summary = "场景1-②派发问题到子Agent",
            description = "读取目标Agent配置的receiveTaskUrl并POST完整任务体（真实HTTP调用），成功后任务置为已派发。需子Agent接收接口可达")
    public AjaxResultVO scene1Step2Dispatch(@RequestBody CollabDispatchRequest req) {
        if (req.getId() == null || req.getId().isBlank()) {
            return AjaxResultVO.error("id不能为空");
        }
        DispatchTask param = new DispatchTask();
        param.setId(req.getId());
        param.setDispatchedUser(req.getDispatchedUser());
        boolean success = dispatchTaskService.dispatchTask(param);
        return success ? AjaxResultVO.ok().data("派发成功") : AjaxResultVO.error("派发失败（任务不存在/已派发/Agent未配置接收地址/子Agent不可达）");
    }

    @PostMapping("/scene1/step3-analysis-result")
    @Operation(summary = "场景1-③子Agent回传AI分析结果",
            description = "子Agent AI分析后回传决策建议。taskType：collaboration-置待采纳（走step4）、hint-仅存档、normal-自动完结（流程结束）")
    public AjaxResultVO scene1Step3AnalysisResult(@RequestBody CollabAnalysisResultRequest req) {
        if (req.getId() == null || req.getId().isBlank()) {
            return AjaxResultVO.error("id不能为空");
        }
        DispatchTask body = new DispatchTask();
        body.setTaskType(req.getTaskType());
        body.setDecisionContent(req.getDecisionContent());
        body.setDecisionData(req.getDecisionData());
        body.setThinkingOverview(req.getThinkingOverview());
        body.setLevel(req.getLevel());
        body.setExpectedEffect(req.getExpectedEffect());
        return dispatchTaskService.saveAnalysisResult(req.getId(), body);
    }

    @PostMapping("/scene1/step4-adopt")
    @Operation(summary = "场景1-④用户采纳/驳回（回调子Agent）",
            description = "仅collaboration类任务可操作。落库后同步回调子Agent的adoptFeedbackUrl（重试3次）；场景1回调targetAgent。驳回(2)时子Agent不执行，流程结束")
    public AjaxResultVO scene1Step4Adopt(@RequestBody CollabAdoptRequest req) {
        return adopt(req);
    }

    @PostMapping("/scene1/step5-result-feedback")
    @Operation(summary = "场景1-⑤子Agent执行结果反馈（闭环）",
            description = "子Agent执行决策后回传执行结果，写入resultExecuteStatus/resultDesc/resultData/completeTime，完成闭环")
    public AjaxResultVO scene1Step5ResultFeedback(@RequestBody CollabResultFeedbackRequest req) {
        return resultFeedback(req);
    }

    // ==================== 场景2：子Agent → 系统Agent ====================

    @PostMapping("/scene2/step1-report")
    @Operation(summary = "场景2-①子Agent上报问题+决策",
            description = "子Agent发现问题并上报（creatorAgentId=子Agent，不能为commander）。taskType：collaboration-置待采纳（走step2）、hint-仅存档、normal-正常/误报入库即完结（流程结束）。开启自动控制且targetAgentId=energy-agent时，上报成功后接口先返回，延迟2秒再异步自动采纳并回调子Agent，下发失败每间隔1秒重试最多3次")
    public AjaxResultVO scene2Step1Report(@RequestBody CollabReportRequest req) {
        DispatchTask task = new DispatchTask();
        task.setCreatorAgentId(req.getCreatorAgentId());
        task.setCreatorAgentName(req.getCreatorAgentName());
        task.setBusinessDomain(req.getBusinessDomain());
        task.setTaskType(req.getTaskType());
        task.setProblemDesc(req.getProblemDesc());
        task.setDecisionContent(req.getDecisionContent());
        task.setDecisionData(req.getDecisionData());
        task.setThinkingOverview(req.getThinkingOverview());
        task.setLevel(req.getLevel());
        task.setExpectedEffect(req.getExpectedEffect());
        task.setTargetAgentId(req.getTargetAgentId());
        task.setTargetAgentName(req.getTargetAgentName());
        AjaxResultVO reportResult = dispatchTaskService.reportTask(task);
        // 自动控制：开启且目标Agent为energy-agent时，上报成功后延迟2秒异步自动采纳（adoptStatus=1，采纳人=Redis中存的操作人），
        // 并回调子Agent；下发失败每间隔1秒重试，最多重试3次。采纳任务在后台调度执行，不阻塞本次上报，接口先返回上报结果
        if (reportResult.isOk() && isAutoControlEnabled() && AUTO_ADOPT_AGENT_ID.equals(req.getTargetAgentId())) {
            DispatchTask reported = reportResult.getData();
            if (reported != null && reported.getId() != null) {
                String taskId = reported.getId();
                String autoUser = redisTemplate.opsForValue().get(AUTO_CONTROL_USERNAME_KEY);
                String adoptUser = autoUser == null || autoUser.isBlank() ? "系统" : autoUser;
                scheduleAutoAdopt(taskId, adoptUser, 0);
            }
        }
        return reportResult;
    }

    @PostMapping("/scene2/step2-adopt")
    @Operation(summary = "场景2-②用户采纳/驳回（回调上报的子Agent）",
            description = "仅collaboration类任务可操作。采纳同步置派发状态1-已派发，驳回置2-已取消，派发人=采纳人；落库后同步回调子Agent的adoptFeedbackUrl（重试3次）；场景2回调creatorAgent（上报者）。驳回(2)时子Agent不执行，流程结束")
    public AjaxResultVO scene2Step2Adopt(@RequestBody CollabAdoptRequest req) {
        return adopt(req);
    }

    @PostMapping("/scene2/step3-result-feedback")
    @Operation(summary = "场景2-③子Agent执行结果反馈（闭环）",
            description = "子Agent执行决策后回传执行结果，写入resultExecuteStatus/resultDesc/resultData/completeTime，完成闭环")
    public AjaxResultVO scene2Step3ResultFeedback(@RequestBody CollabResultFeedbackRequest req) {
        return resultFeedback(req);
    }

    // ==================== 自动控制开关 ====================

    @PostMapping("/auto-control/set")
    @Operation(summary = "设置是否启用自动控制",
            description = "enabled-是否启用自动控制，username-操作人，均存入Redis。开启后，场景2子Agent上报且targetAgentId=energy-agent的任务将在上报成功后延迟2秒异步自动采纳并回调子Agent（下发失败每间隔1秒重试最多3次），无需人工操作")
    public AjaxResultVO setAutoControl(@RequestBody AutoControlRequest req) {
        if (req.getEnabled() == null) {
            return AjaxResultVO.error("enabled不能为空");
        }
        boolean enabled = Boolean.TRUE.equals(req.getEnabled());
        redisTemplate.opsForValue().set(AUTO_CONTROL_ENABLED_KEY, enabled ? "1" : "0");
        // username未传时沿用Redis中已存的操作人；均无则记为"系统"
        String username = (req.getUsername() == null || req.getUsername().isBlank())
                ? redisTemplate.opsForValue().get(AUTO_CONTROL_USERNAME_KEY) : req.getUsername();
        username = (username == null || username.isBlank()) ? "系统" : username;
        redisTemplate.opsForValue().set(AUTO_CONTROL_USERNAME_KEY, username);
        log.info("自动控制已设置为{}，操作人: {}", enabled ? "开启" : "关闭", username);
        return AjaxResultVO.ok().data(enabled ? "已开启自动控制" : "已关闭自动控制");
    }

    @GetMapping("/auto-control/status")
    @Operation(summary = "查询当前是否自动控制",
            description = "从Redis读取当前自动控制状态：enabled-是否开启（无记录默认关闭），username-最近一次操作人")
    public AjaxResultVO getAutoControlStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", isAutoControlEnabled());
        status.put("username", redisTemplate.opsForValue().get(AUTO_CONTROL_USERNAME_KEY));
        return AjaxResultVO.ok().data(status);
    }

    /** 自动控制是否开启（Redis无记录时默认关闭） */
    private boolean isAutoControlEnabled() {
        return "1".equals(redisTemplate.opsForValue().get(AUTO_CONTROL_ENABLED_KEY));
    }

    /** 服务关闭时停止自动采纳调度器 */
    @PreDestroy
    public void shutdownAutoAdoptScheduler() {
        autoAdoptScheduler.shutdownNow();
    }

    /**
     * 自动采纳下发：首次延迟{@link #AUTO_ADOPT_INITIAL_DELAY_SECONDS}秒执行，
     * 失败（异常或返回错误）后每间隔{@link #AUTO_ADOPT_RETRY_INTERVAL_SECONDS}秒重试，最多重试{@link #AUTO_ADOPT_MAX_RETRIES}次。
     * 失败后通过重新排期实现重试，重试等待期间不占用调度线程
     */
    private void scheduleAutoAdopt(String taskId, String adoptUser, int attempt) {
        long delaySeconds = attempt == 0 ? AUTO_ADOPT_INITIAL_DELAY_SECONDS : AUTO_ADOPT_RETRY_INTERVAL_SECONDS;
        autoAdoptScheduler.schedule(() -> {
            try {
                CollabAdoptRequest adoptReq = new CollabAdoptRequest();
                adoptReq.setId(taskId);
                adoptReq.setAdoptStatus("1");
                adoptReq.setAdoptUser(adoptUser);
                AjaxResultVO adoptResult = adopt(adoptReq);
                if (adoptResult.isOk()) {
                    log.info("自动控制自动采纳上报任务成功: taskId={}, adoptUser={}, 第{}次尝试, 结果: {}",
                            taskId, adoptUser, attempt + 1, adoptResult.getMsg());
                    return;
                }
                log.warn("自动控制自动采纳失败: taskId={}, 第{}次尝试, 结果: {}",
                        taskId, attempt + 1, adoptResult.getMsg());
            } catch (Exception e) {
                log.error("自动控制自动采纳异常: taskId={}, 第{}次尝试", taskId, attempt + 1, e);
            }
            if (attempt < AUTO_ADOPT_MAX_RETRIES) {
                scheduleAutoAdopt(taskId, adoptUser, attempt + 1);
            } else {
                log.error("自动控制自动采纳最终失败（已重试{}次）: taskId={}, adoptUser={}",
                        AUTO_ADOPT_MAX_RETRIES, taskId, adoptUser);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    // ==================== 模拟子Agent接收端（Mock） ====================

    @PostMapping("/mock/receive-task")
    @Operation(summary = "Mock-子Agent接收任务接口（作为agent_config.receive_task_url）",
            description = "模拟子Agent的接收任务接口：只接收数据并打印日志，不做任何处理、不修改数据库，直接返回HTTP成功。将子Agent的receive_task_url配为 http://本服务/collab-test/mock/receive-task 即可联调场景1-②派发")
    public AjaxResultVO mockReceiveTask(@RequestBody(required = false) DispatchTask task) {
        log.info("[Mock子Agent] 收到派发任务: {}", task);
        return AjaxResultVO.ok().data("Mock子Agent已接收任务");
    }

    @PostMapping("/mock/adopt-notify")
    @Operation(summary = "Mock-子Agent采纳结果接收接口（作为agent_config.adopt_feedback_url）",
            description = "模拟子Agent的采纳结果接收接口：只接收数据并打印日志，不做任何处理、不修改数据库，直接返回HTTP成功。将子Agent的adopt_feedback_url配为 http://本服务/collab-test/mock/adopt-notify 即可联调场景1-④/场景2-②采纳回调")
    public AjaxResultVO mockAdoptNotify(@RequestBody(required = false) AdoptNotifyRequest notify) {
        log.info("[Mock子Agent] 收到采纳结果通知: {}", notify);
        return AjaxResultVO.ok().data("Mock子Agent已接收采纳结果");
    }

    /**
     * 用户采纳/驳回（场景1-④/场景2-②共用），必传校验在Service完成
     */
    private AjaxResultVO adopt(CollabAdoptRequest req) {
        DispatchTask body = new DispatchTask();
        body.setId(req.getId());
        body.setAdoptStatus(req.getAdoptStatus());
        body.setAdoptUser(req.getAdoptUser());
        return dispatchTaskService.adoptAndNotify(body);
    }

    /**
     * 子Agent执行结果反馈（场景1-⑤/场景2-③共用）
     */
    private AjaxResultVO resultFeedback(CollabResultFeedbackRequest req) {
        if (req.getId() == null || req.getId().isBlank()) {
            return AjaxResultVO.error("id不能为空");
        }
        DispatchTask existing = dispatchTaskService.getById(req.getId());
        if (existing == null) {
            return AjaxResultVO.error("任务不存在");
        }
        DispatchTask update = new DispatchTask();
        update.setId(req.getId());
        update.setResultExecuteStatus(req.getResultExecuteStatus());
        update.setResultDesc(req.getResultDesc());
        update.setResultExecuteUser(req.getResultExecuteUser());
        update.setResultData(req.getResultData());
        update.setCompleteTime(LocalDateTime.now());
        boolean success = dispatchTaskService.updateById(update);
        return success ? AjaxResultVO.ok().data("反馈成功，任务闭环") : AjaxResultVO.error("反馈失败");
    }
}
