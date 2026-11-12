package com.sysware.xian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.swcloud.common.core.vo.Res;
import com.sysware.xian.domain.AgentConfig;
import com.sysware.xian.domain.AiDiagnosisRecord;
import com.sysware.xian.domain.DispatchTask;
import com.sysware.xian.mapper.DispatchTaskMapper;
import com.sysware.xian.mapper.TunnelRunScoreMapper;
import com.sysware.xian.swcloud.domain.AjaxResultVO;
import com.sysware.xian.swcloud.domain.ScadaPageQueryDto;
import com.sysware.xian.swcloud.domain.ScadaYkSendCommand;
import com.sysware.xian.swcloud.domain.issues.RepairOrderCloseCommand;
import com.sysware.xian.swcloud.fegin.MCPXClient;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchTaskService extends ServiceImpl<DispatchTaskMapper, DispatchTask> {

    private final AgentConfigService agentConfigService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ChatClient.Builder chatClientBuilder;
    private final List<McpSyncClient> mcpSyncClients;
    private final MCPXClient mcpXClient;
    private final AiDiagnosisRecordService aiDiagnosisRecordService;
    private final TunnelRunScoreMapper tunnelRunScoreMapper;
    @Value("${ai.tts-api-url}")
    private String ttsApiUrl;

    /** taskType合法取值: hint-提示, collaboration-协同方案(Agent协作), normal-正常/误报 */
    private static final Set<String> TASK_TYPES = Set.of("hint", "collaboration", "normal");
    private static final String TASK_TYPE_COLLABORATION = "collaboration";
    private static final String TASK_TYPE_NORMAL = "normal";
    /** 算力服务器监控业务域 */
    public static final String SERVER_MONITOR_BUSINESS_DOMAIN = "算力服务器监控";
    /** 算力服务器高温阈值（℃），最高温度达到该值即生成提示任务 */
    public static final int SERVER_HIGH_TEMPERATURE_THRESHOLD = 90;
    /**
     * 查询任务列表（带筛选条件，分页，按创建时间倒序）
     */
    public Page<DispatchTask> list(String creatorAgentName, String targetAgentId, String targetAgentName, Integer dispatched, String businessDomain, String taskType, String adoptStatus, String resultExecuteStatus, String subareaCid, String decisionContent, String decisionData, int pageNum, int pageSize) {
        LambdaQueryWrapper<DispatchTask> wrapper = new LambdaQueryWrapper<>();
        if (creatorAgentName != null && !creatorAgentName.isEmpty()) {
            wrapper.like(DispatchTask::getCreatorAgentName, creatorAgentName);
        }
        if (targetAgentId != null && !targetAgentId.isEmpty()) {
            wrapper.eq(DispatchTask::getTargetAgentId, targetAgentId);
        }
        if (targetAgentName != null && !targetAgentName.isEmpty()) {
            wrapper.like(DispatchTask::getTargetAgentName, targetAgentName);
        }
        if (dispatched != null) {
            wrapper.eq(DispatchTask::getDispatched, dispatched);
        }
        if (businessDomain != null && !businessDomain.isEmpty()) {
            wrapper.eq(DispatchTask::getBusinessDomain, businessDomain);
        }
        if (taskType != null && !taskType.isEmpty()) {
            wrapper.eq(DispatchTask::getTaskType, taskType);
        }
        if (resultExecuteStatus != null && !resultExecuteStatus.isEmpty()) {
            wrapper.eq(DispatchTask::getResultExecuteStatus, resultExecuteStatus);
        }
        if (adoptStatus != null) {
            wrapper.eq(DispatchTask::getAdoptStatus, adoptStatus);
        }
        if (subareaCid != null && !subareaCid.isEmpty()) {
            wrapper.eq(DispatchTask::getSubareaCid, subareaCid);
        }
        if (decisionContent != null && !decisionContent.isEmpty()) {
            wrapper.like(DispatchTask::getDecisionContent, decisionContent);
        }
        if (decisionData != null && !decisionData.isEmpty()) {
            wrapper.eq(DispatchTask::getDecisionData, decisionData);
        }
        wrapper.orderByDesc(DispatchTask::getCreateTime);

        Page<DispatchTask> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 传了decisionData但未查到数据时，回退为固定businessDomain=维修工单、去掉decisionData，查最新一条
        if (decisionData != null && !decisionData.isEmpty()
                && (page.getRecords() == null || page.getRecords().isEmpty())) {
            wrapper.clear();
            if (creatorAgentName != null && !creatorAgentName.isEmpty()) {
                wrapper.like(DispatchTask::getCreatorAgentName, creatorAgentName);
            }
            if (targetAgentId != null && !targetAgentId.isEmpty()) {
                wrapper.eq(DispatchTask::getTargetAgentId, targetAgentId);
            }
            if (targetAgentName != null && !targetAgentName.isEmpty()) {
                wrapper.like(DispatchTask::getTargetAgentName, targetAgentName);
            }
            if (dispatched != null) {
                wrapper.eq(DispatchTask::getDispatched, dispatched);
            }
            wrapper.eq(DispatchTask::getBusinessDomain, "维修工单");
            if (taskType != null && !taskType.isEmpty()) {
                wrapper.eq(DispatchTask::getTaskType, taskType);
            }
            if (resultExecuteStatus != null && !resultExecuteStatus.isEmpty()) {
                wrapper.eq(DispatchTask::getResultExecuteStatus, resultExecuteStatus);
            }
            if (adoptStatus != null) {
                wrapper.eq(DispatchTask::getAdoptStatus, adoptStatus);
            }
            if (subareaCid != null && !subareaCid.isEmpty()) {
                wrapper.eq(DispatchTask::getSubareaCid, subareaCid);
            }
            if (decisionContent != null && !decisionContent.isEmpty()) {
                wrapper.like(DispatchTask::getDecisionContent, decisionContent);
            }
            wrapper.orderByDesc(DispatchTask::getCreateTime);
            page = page(new Page<>(1, 1), wrapper);
        }

        // 填充目标Agent名称
        for (DispatchTask task : page.getRecords()) {
            if (task.getTargetAgentId() != null && task.getTargetAgentName() == null) {
                AgentConfig agent = agentConfigService.getById(task.getTargetAgentId());
                if (agent != null) {
                    task.setTargetAgentName(agent.getAgentName());
                }
            }
        }
        return page;
    }

    public List<DispatchTask> listUndispatched() {
        return getBaseMapper().selectUndispatchedTasks();
    }

    public DispatchTask getById(String id) {
        return getOne(new LambdaQueryWrapper<DispatchTask>().eq(DispatchTask::getId, id));
    }

    /**
     * 当 targetAgentId=ops-agent 且 decisionData(设备ID)不为空时，
     * 查询该设备是否已存在未完成的任务（resultExecuteStatus=0），
     * 存在则覆盖该任务（用新任务数据更新旧记录），不存在则继续新建。
     */
    public void handleExistingTask(DispatchTask task) {
        if (!"ops-agent".equals(task.getTargetAgentId())) {
            return;
        }
        if (task.getDecisionData() == null || task.getDecisionData().isBlank()) {
            return;
        }
        DispatchTask existing = getOne(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(DispatchTask::getTargetAgentId, "ops-agent")
                        .eq(DispatchTask::getDecisionData, task.getDecisionData())
                        .eq(DispatchTask::getResultExecuteStatus, "0")
                        .last("LIMIT 1"));
        if (existing != null) {
            task.setId(existing.getId());
            task.setCreateTime(existing.getCreateTime());
            log.info("设备[{}]已存在未完成任务，覆盖更新: taskId={}", task.getDecisionData(), existing.getId());
        }
    }

    /**
     * 创建派发任务
     */
    public DispatchTask createTask(DispatchTask task) {
        if (task.getCreateTime() == null) {
            task.setCreateTime(LocalDateTime.now());
        }
        if (task.getDispatched() == null) {
            task.setDispatched(0);
        }
// ops-agent 且 decisionData(设备ID)不为空时，查重覆盖
handleExistingTask(task);
saveOrUpdate(task);
return task;
    }

    /**
     * 算力服务器高温检查（由ScheduledService每半小时调用一次）
     * 最高温度达到{@link #SERVER_HIGH_TEMPERATURE_THRESHOLD}时，检查是否已存在未完结的
     * 算力服务器监控任务（business_domain='算力服务器监控'且result_execute_status='0'）：
     * 存在则不重复新建，不存在则按提示(hint)模板入库dispatch_task。
     *
     * @param maxTemperature 当前服务器最高温度（℃），来自/npu/detail接口
     */
    public void checkServerHighTemperature(int maxTemperature) {
        if (maxTemperature < SERVER_HIGH_TEMPERATURE_THRESHOLD) {
            return;
        }
        DispatchTask existing = getOne(new LambdaQueryWrapper<DispatchTask>()
                .eq(DispatchTask::getBusinessDomain, SERVER_MONITOR_BUSINESS_DOMAIN)
                .eq(DispatchTask::getResultExecuteStatus, "0")
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("算力服务器温度过高({}℃)，已存在未完成任务{}，不重复新建", maxTemperature, existing.getId());
            return;
        }
        DispatchTask task = new DispatchTask();
        task.setSubareaCid("0.001.001.001-0");
        task.setCreatorAgentId("commander");
        task.setCreatorAgentName("系统Agent");
        task.setBusinessDomain(SERVER_MONITOR_BUSINESS_DOMAIN);
        task.setLevel("高");
        task.setProblemDesc("服务器温度过高，当前" + maxTemperature + "度");
        task.setDispatched(0);
        task.setDecisionContent("及时查看服务器/机房运行情况");
        task.setDecisionData("");
        task.setTargetAgentId("commander");
        task.setTargetAgentName("系统Agent");
        task.setTaskType("hint");
        task.setAdoptStatus("0");
        task.setResultExecuteStatus(0);
        createTask(task);
        log.warn("算力服务器温度过高({}℃)，已创建提示任务: {}", maxTemperature, task.getId());
    }

    /**
     * 派发任务到子Agent
     */
    public boolean dispatchTask(DispatchTask taskParam) {
        DispatchTask task = getById(taskParam.getId());
        if (task == null) {
            log.warn("任务不存在: {}", taskParam.getId());
            return false;
        }
        task.setDispatchedUser(taskParam.getDispatchedUser());
        if (task.getDispatched() != null && task.getDispatched() == 1) {
            log.warn("任务已派发: {}", taskParam.getId());
            return false;
        }
        if (task.getTargetAgentId() == null) {
            log.warn("任务未指定目标Agent: {}", taskParam.getTargetAgentId());
            return false;
        }

        AgentConfig targetAgent = agentConfigService.getById(task.getTargetAgentId());
        if (targetAgent == null) {
            log.warn("目标Agent配置不存在: {}", task.getTargetAgentId());
            return false;
        }

        // 如果目标Agent是commander，不发送HTTP请求，直接执行AI任务
        if ("commander".equals(targetAgent.getAgentId())) {
            // 更新派发状态、派发时间、派发人
//            getBaseMapper().markReceived(taskParam.getId(), LocalDateTime.now(), taskParam.getDispatchedUser());
            return  true;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<DispatchTask> entity = new HttpEntity<>(task, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    targetAgent.getReceiveTaskUrl(),
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                // 更新派发状态、派发时间、派发人
                getBaseMapper().markReceived(taskParam.getId(), LocalDateTime.now(), taskParam.getDispatchedUser());
                log.debug("任务派发成功: {} -> {}", taskParam.getId(), targetAgent.getAgentName());
                return true;
            } else {
                log.error("派发任务失败，状态码: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("派发任务异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 更新任务采纳状态及关联字段（businessDomain、thinkingOverview、level、decisionContent等）
     * 使用 MyBatis-Plus updateById 仅更新非空字段
     */
    public boolean adoptTask(DispatchTask task) {
        DispatchTask existing = getById(task.getId());
        if (existing == null) {
            log.warn("任务不存在: {}", task.getId());
            return false;
        }
        // 自动设置采纳时间
        task.setAdoptTime(LocalDateTime.now());
        // 默认采纳状态为 '1'（已采纳）
        if (task.getAdoptStatus() == null) {
            task.setAdoptStatus("1");
        }
        boolean updated = updateById(task);
        if (updated) {
            log.debug("任务采纳状态已更新: {} -> {}, adoptUser: {}, 额外字段已更新", task.getId(), task.getAdoptStatus(), task.getAdoptUser());
        }
        return updated;
    }

    /**
     * 用户采纳/驳回：必传校验、落库dispatched+adopt_status，collaboration类额外HTTP回调子Agent的receiveTaskUrl
     */
    public AjaxResultVO adoptAndNotify(DispatchTask task) {
        if (task.getAdoptStatus() == null || task.getAdoptStatus().isBlank()) {
            return AjaxResultVO.error("adoptStatus不能为空（1采纳/2驳回）");
        }
        if (!"1".equals(task.getAdoptStatus()) && !"2".equals(task.getAdoptStatus())) {
            return AjaxResultVO.error("adoptStatus只能为1（采纳）或2（驳回）");
        }
        if (task.getAdoptUser() == null || task.getAdoptUser().isBlank()) {
            return AjaxResultVO.error("adoptUser不能为空");
        }
        DispatchTask existing = getById(task.getId());
        if (existing == null) {
            return AjaxResultVO.error("任务不存在");
        }
        task.setAdoptTime(LocalDateTime.now());
        // 未派发任务：采纳即视为派发(dispatched=1)，驳回视为取消(dispatched=2)，派发人=采纳人
        if (existing.getDispatched() == null || existing.getDispatched() == 0) {
            task.setDispatched("1".equals(task.getAdoptStatus()) ? 1 : 2);
            task.setDispatchedTime(LocalDateTime.now());
            task.setDispatchedUser(task.getAdoptUser());
        }
        if (!updateById(task)) {
            return AjaxResultVO.error("采纳状态更新失败");
        }
        String action = "1".equals(task.getAdoptStatus()) ? "采纳" : "驳回";
        log.debug("任务已{}: {}, adoptUser: {}", action, task.getId(), task.getAdoptUser());
        // 仅collaboration类任务回调子Agent：POST完整任务体到子Agent的adoptFeedbackUrl
        if (!TASK_TYPE_COLLABORATION.equals(existing.getTaskType())) {
            return AjaxResultVO.ok().data(action + "成功");
        }
        String notifyAgentId = "commander".equals(existing.getCreatorAgentId())
                ? existing.getTargetAgentId() : existing.getCreatorAgentId();
        // 回调前重查更新后的任务：报文须携带本次采纳结果（adoptStatus/adoptUser/adoptTime/dispatched等），子Agent依据adoptStatus决定是否执行
        DispatchTask latest = getById(task.getId());
        if (latest != null) {
            existing = latest;
        }
        String notifyMsg = notifyAgentReceiveTask(existing, notifyAgentId);
        return AjaxResultVO.ok().data(notifyMsg == null ? action + "成功，已通知子Agent" : action + "成功，" + notifyMsg);
    }

    /**
     * POST完整任务体到子Agent的adoptFeedbackUrl（失败重试3次，间隔1秒）；
     * 与dispatchTask的HTTP调用逻辑一致。返回null表示通知成功，否则返回提示信息
     */
    private String notifyAgentReceiveTask(DispatchTask existing, String notifyAgentId) {
        if (notifyAgentId == null || "commander".equals(notifyAgentId)) {
            return "无需通知子Agent";
        }
        AgentConfig agent = agentConfigService.getById(notifyAgentId);
        if (agent == null || agent.getAdoptFeedbackUrl() == null || agent.getAdoptFeedbackUrl().isBlank()) {
            log.warn("Agent[{}]未配置adoptFeedbackUrl，无法通知, taskId={}", notifyAgentId, existing.getId());
            return "子Agent未配置接收地址(receiveTaskUrl)，未通知";
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<DispatchTask> entity = new HttpEntity<>(existing, headers);
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        agent.getAdoptFeedbackUrl(), HttpMethod.POST, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.debug("任务已通知子Agent[{}]: taskId={}", notifyAgentId, existing.getId());
                    return null;
                }
                log.warn("通知子Agent失败(第{}次)，状态码: {}", attempt, response.getStatusCode());
            } catch (Exception e) {
                log.warn("通知子Agent异常(第{}次): {}", attempt, e.getMessage());
            }
            if (attempt < 3) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.error("通知子Agent[{}]最终失败: taskId={}", notifyAgentId, existing.getId());
        return "通知子Agent失败（已重试3次），请人工同步";
    }

    /**
     * 子Agent上报"问题+决策"：必传与分类校验；normal类入库即完结，collaboration类置待采纳
     */
    public AjaxResultVO reportTask(DispatchTask task) {
        if (task.getCreatorAgentId() == null || task.getCreatorAgentId().isBlank()) {
            return AjaxResultVO.error("creatorAgentId不能为空");
        }
        if (task.getBusinessDomain() == null || task.getBusinessDomain().isBlank()) {
            return AjaxResultVO.error("businessDomain不能为空");
        }
        if (task.getProblemDesc() == null || task.getProblemDesc().isBlank()) {
            return AjaxResultVO.error("problemDesc不能为空");
        }
        if (task.getTaskType() == null || !TASK_TYPES.contains(task.getTaskType())) {
            return AjaxResultVO.error("taskType必须为hint/collaboration/normal之一");
        }
        if (task.getCreatorAgentName() == null) {
            AgentConfig creator = agentConfigService.getById(task.getCreatorAgentId());
            if (creator != null) {
                task.setCreatorAgentName(creator.getAgentName());
            }
        }
        task.setCreateTime(LocalDateTime.now());
        task.setDispatched(0);
        if (TASK_TYPE_NORMAL.equals(task.getTaskType())) {
            // 正常/误报：留痕即可，入库直接完结
            task.setCompleteTime(LocalDateTime.now());
            task.setResultExecuteStatus(1);
            if (task.getResultDesc() == null) {
                task.setResultDesc("正常/误报，无需处理，自动完结");
            }
        } else if (TASK_TYPE_COLLABORATION.equals(task.getTaskType())) {
            task.setAdoptStatus("0");
        }
// ops-agent 且 decisionData(设备ID)不为空时，查重覆盖
handleExistingTask(task);
saveOrUpdate(task);
log.debug("子Agent[{}]上报任务成功: {}, taskType={}", task.getCreatorAgentId(), task.getId(), task.getTaskType());
        return AjaxResultVO.ok().data(task);
    }

    /**
     * 子Agent回传AI分析结果：按taskType分类分支流转
     * collaboration-置待采纳；hint-仅存档；normal-自动完结
     */
    public AjaxResultVO saveAnalysisResult(String id, DispatchTask body) {
        if (body.getTaskType() == null || !TASK_TYPES.contains(body.getTaskType())) {
            return AjaxResultVO.error("taskType必须为hint/collaboration/normal之一");
        }
        if (body.getDecisionContent() == null || body.getDecisionContent().isBlank()) {
            return AjaxResultVO.error("decisionContent不能为空");
        }
        DispatchTask existing = getById(id);
        if (existing == null) {
            return AjaxResultVO.error("任务不存在");
        }
        if (existing.getDispatched() == null || existing.getDispatched() != 1) {
            return AjaxResultVO.error("任务未派发，不能回传分析结果");
        }
        DispatchTask update = new DispatchTask();
        update.setId(id);
        update.setTaskType(body.getTaskType());
        update.setDecisionContent(body.getDecisionContent());
        update.setDecisionData(body.getDecisionData());
        update.setThinkingOverview(body.getThinkingOverview());
        update.setLevel(body.getLevel());
        update.setExpectedEffect(body.getExpectedEffect());
        if (TASK_TYPE_COLLABORATION.equals(body.getTaskType())) {
            update.setAdoptStatus("0");
        } else if (TASK_TYPE_NORMAL.equals(body.getTaskType())) {
            update.setCompleteTime(LocalDateTime.now());
            update.setResultExecuteStatus(1);
            update.setResultDesc("正常/误报，无需处理，自动完结");
        }
        boolean updated = updateById(update);
        if (updated) {
            log.debug("任务分析结果已回传: {}, taskType={}", id, body.getTaskType());
        }
        return updated ? AjaxResultVO.ok().data("回传成功") : AjaxResultVO.error("回传失败");
    }

    /**
     * 用户大屏回填：更新用户反馈、执行结果状态，执行人=username
     */
    public AjaxResultVO userFeedback(String id, String username, String userFeedback, Integer resultExecuteStatus) {
        if (id == null || id.isBlank()) {
            return AjaxResultVO.error("id不能为空");
        }
        if (username == null || username.isBlank()) {
            return AjaxResultVO.error("username不能为空");
        }
        if (resultExecuteStatus != null && resultExecuteStatus!=1 && resultExecuteStatus!=2) {
            return AjaxResultVO.error("resultExecuteStatus只能为1（处理完成）或2（处理失败）");
        }
        DispatchTask existing = getById(id);
        if (existing == null) {
            return AjaxResultVO.error("任务不存在");
        }
        DispatchTask update = new DispatchTask();
        update.setId(id);
        update.setUserFeedback(userFeedback);
        update.setResultExecuteStatus(resultExecuteStatus);
        update.setResultExecuteUser(username);
        update.setCompleteTime(LocalDateTime.now());
        boolean success = updateById(update);
        if (success) {
            log.info("用户大屏回填成功: {}, username={}, resultExecuteStatus={}", id, username, resultExecuteStatus);

            // 查询是否有AI诊断记录，有则关闭对应设备的工单
            try {
                AiDiagnosisRecord diagnosisRecord = aiDiagnosisRecordService.getBySourceDataId(id);
                if (diagnosisRecord != null) {
                    String assetId = existing.getDecisionData();
                    if (assetId != null && !assetId.isBlank()) {
                        RepairOrderCloseCommand closeCommand = RepairOrderCloseCommand.builder()
                                .closeReason(userFeedback != null ? userFeedback : "用户回填关闭")
                                .assetUsageStatus("IN_USE")
                                .build();
                        Res closeResult = mcpXClient.closeRepairOrders(assetId, closeCommand);
                        log.info("用户回填触发关闭工单: assetId={}, result={}", assetId, closeResult);
                    } else {
                        log.warn("用户回填: 任务 {} 的 decisionData 为空，无法关闭工单", id);
                    }
                }
            } catch (Exception e) {
                log.warn("用户回填关闭工单异常: taskId={}, error={}", id, e.getMessage());
            }
        }
        
        return success ? AjaxResultVO.ok().data("回填成功") : AjaxResultVO.error("回填失败");
    }

    /**
     * 接收任务：若任务已存在则更新派发状态，否则新增
     */
    public boolean receiveTask(DispatchTask task) {
        if (task.getId() != null) {
            DispatchTask existing = getById(task.getId());
            if (existing != null) {
                // 任务已存在，更新派发状态
                int rows = getBaseMapper().markReceived(task.getId(), LocalDateTime.now(), task.getDispatchedUser());
                if (rows > 0) {
                    log.debug("任务接收(更新派发状态)成功: {}", task.getId());
                    return true;
                }
                log.warn("任务接收(更新派发状态)失败: {}", task.getId());
                return false;
            }
        }
        // 任务不存在，新增
        task.setCreateTime(LocalDateTime.now());
        task.setDispatched(1);
        task.setDispatchedTime(LocalDateTime.now());
        save(task);
        log.debug("任务接收(新增)成功: {}", task.getId());
        return true;
    }

    /**
     * 根据ID接收任务：仅更新派发状态
     */
    public boolean receiveTaskById(String taskId, String dispatchedUser) {
        DispatchTask existing = getById(taskId);
        if (existing != null) {
            int rows = getBaseMapper().markReceived(taskId, LocalDateTime.now(), dispatchedUser);
            if (rows > 0) {
                log.debug("任务接收(更新派发状态)成功: {}", taskId);
                return true;
            }
            log.warn("任务接收(更新派发状态)失败: {}", taskId);
            return false;
        }
        log.warn("任务不存在: {}", taskId);
        return false;
    }

    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId, String dispatchedUser) {
        DispatchTask task = getById(taskId);
        if (task == null) {
            log.warn("任务不存在: {}", taskId);
            return false;
        }
        if (task.getDispatched() != null && task.getDispatched() == 2) {
            log.warn("任务已取消: {}", taskId);
            return false;
        }
        int rows = getBaseMapper().markCancelled(taskId, dispatchedUser);
        if (rows > 0) {
            log.debug("任务已取消: {}", taskId);
            return true;
        }
        return false;
    }

    /**
     * 获取Agent协作统计信息
     */
    public Map<String, Object> getAgentCollabStats() {
        Map<String, Object> result = new HashMap<>();

        // 总任务数
        long totalTasks = count();
        result.put("totalTasks", totalTasks);

        // 已派发任务数
        long dispatchedTasks = count(new LambdaQueryWrapper<DispatchTask>().eq(DispatchTask::getDispatched, 1));
        result.put("dispatchedTasks", dispatchedTasks);

        // 派发率
        double dispatchRate = totalTasks > 0 ? (double) dispatchedTasks / totalTasks : 0;
        result.put("dispatchRate", dispatchRate);

        // 获取所有Agent配置
        List<AgentConfig> allAgents = agentConfigService.list();
        Map<String, String> agentNameMap = allAgents.stream()
                .collect(Collectors.toMap(AgentConfig::getAgentId, AgentConfig::getAgentName, (a, b) -> a));

        // 近7日任务趋势
        List<Map<String, Object>> dailyStats = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        // 查询近7日所有任务
        LocalDateTime sevenDaysAgo = today.minusDays(6).atStartOfDay();
        List<DispatchTask> recentTasks = list(new LambdaQueryWrapper<DispatchTask>()
                .ge(DispatchTask::getCreateTime, sevenDaysAgo));

        // 按日期分组统计
        Map<String, List<DispatchTask>> tasksByDate = recentTasks.stream()
                .collect(Collectors.groupingBy(t -> t.getCreateTime().toLocalDate().format(formatter)));

        // 收集所有涉及的Agent名称
        Set<String> allAgentNames = new LinkedHashSet<>();
        for (AgentConfig agent : allAgents) {
            allAgentNames.add(agent.getAgentName());
        }

        for (int i = 6; i >= 0; i--) {
            String date = today.minusDays(i).format(formatter);
            List<DispatchTask> dayTasks = tasksByDate.getOrDefault(date, Collections.emptyList());

            Map<String, Object> dayStat = new HashMap<>();
            dayStat.put("date", date);
            // 生成任务总数
            dayStat.put("generatedCount", dayTasks.size());
            // 目标任务总数
            dayStat.put("targetCount", dayTasks.stream().filter(t -> t.getTargetAgentId() != null).count());

            // 每个Agent的生成任务数
            Map<String, Long> generatedByAgent = dayTasks.stream()
                    .filter(t -> t.getCreatorAgentName() != null)
                    .collect(Collectors.groupingBy(DispatchTask::getCreatorAgentName, Collectors.counting()));

            // 每个Agent的目标任务数
            Map<String, Long> targetByAgent = dayTasks.stream()
                    .filter(t -> t.getTargetAgentId() != null)
                    .collect(Collectors.groupingBy(
                            t -> agentNameMap.getOrDefault(t.getTargetAgentId(), t.getTargetAgentId()),
                            Collectors.counting()));

            Map<String, Long> agentGenerated = new LinkedHashMap<>();
            Map<String, Long> agentTarget = new LinkedHashMap<>();
            for (String name : allAgentNames) {
                agentGenerated.put(name, generatedByAgent.getOrDefault(name, 0L));
                agentTarget.put(name, targetByAgent.getOrDefault(name, 0L));
            }

            dayStat.put("agentGenerated", agentGenerated);
            dayStat.put("agentTarget", agentTarget);

            dailyStats.add(dayStat);
        }

        result.put("dailyStats", dailyStats);
        result.put("agentNames", new ArrayList<>(allAgentNames));
        return result;
    }

    /**
     * 获取各分区未完成任务洞察：每个 subarea_cid 最新的3条 result_execute_status='0' 的任务及 count 总数
     * <p>
     * 优化策略：避免一次性将所有未完成任务加载到Java内存中筛选，
     * 改为先 GROUP BY subarea_cid 获取分区列表及各分区count总数（1次查询），
     * 再轮询每个分区查最新3条任务（N次查询，每次LIMIT 3），显著降低内存和算力消耗。
     * <p>
     * 用于大屏"AI洞察"展示"发现多少处设备故障"与最新任务列表
     */
    public Map<String, Object> getSubareaPendingInsights() {
        // 1. 先GROUP BY subarea_cid获取分区列表和各分区未完成任务总数（单次查询）
        List<Map<String, Object>> subareaCounts = getBaseMapper().selectSubareaPendingCounts();
        if (subareaCounts == null || subareaCounts.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("total", 0);
            empty.put("groups", Collections.emptyList());
            return empty;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<Map<String, Object>> groups = new ArrayList<>();
        int total = 0;

        // 2. 轮询每个分区，查询最新3条未完成任务
        for (Map<String, Object> row : subareaCounts) {
            Object cidValue = mapValue(row, "subareaCid", "subarea_cid");
            if (cidValue == null) continue;
            String subareaCid = String.valueOf(cidValue);
            int count = toInt(mapValue(row, "cnt"));
            total += count;

            List<DispatchTask> top3Tasks = getBaseMapper().selectTopPendingBySubarea(subareaCid);
            List<Map<String, Object>> top3 = top3Tasks.stream().map(t -> {
                Map<String, Object> task = new LinkedHashMap<>();
                task.put("id", t.getId());
                task.put("problemDesc", t.getProblemDesc());
                task.put("decisionContent", t.getDecisionContent());
                task.put("creatorAgentId", t.getCreatorAgentId());
                task.put("targetAgentId", t.getTargetAgentId());
                task.put("createTime", t.getCreateTime() == null ? null : t.getCreateTime().format(formatter));
                return task;
            }).collect(Collectors.toList());

            Map<String, Object> group = new LinkedHashMap<>();
            group.put("subareaCid", subareaCid);
            group.put("count", count);
            group.put("tasks", top3);
            groups.add(group);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("groups", groups);
        return result;
    }

    /**
     * 获取站房运行水平与 Agent 协作关系的真实时间序列。
     */
    public Map<String, Object> getHealthCollabEvolution(String orgId, int hours, int bucketMinutes, Integer dispatched) {
        int safeHours = Math.max(1, hours);
        int safeBucketMinutes = 1440;
        LocalDateTime endTime = LocalDateTime.now();
        int bucketCount = (int) Math.ceil((double) safeHours / 24);
        LocalDateTime startTime = endTime.toLocalDate()
                .minusDays(Math.max(0, bucketCount - 1L))
                .atStartOfDay();

        // 查询原始运行水平记录，不再使用统计查询
        List<com.sysware.xian.domain.TunnelRunScore> scoreRecords = tunnelRunScoreMapper
                .selectByOrgAndTimeRange(orgId, startTime, endTime);
        List<Map<String, Object>> dailyAgentDetails = getBaseMapper()
                .selectDailyAgentDetailStats(startTime, endTime, dispatched);

        List<EvolutionBucket> buckets = new ArrayList<>(bucketCount);
        Map<LocalDate, EvolutionBucket> bucketsByDate = new LinkedHashMap<>();
        for (int i = 0; i < bucketCount; i++) {
            EvolutionBucket bucket = new EvolutionBucket(startTime.plusDays(i));
            buckets.add(bucket);
            bucketsByDate.put(bucket.time.toLocalDate(), bucket);
        }

        // 按日期聚合运行水平：一天内如果有多个记录，取最新一条的totalLevel
        Map<LocalDate, String> dailyLevelMap = new LinkedHashMap<>();
        Map<LocalDate, Integer> dailyCountMap = new LinkedHashMap<>();
        for (com.sysware.xian.domain.TunnelRunScore record : scoreRecords) {
            LocalDate date = record.getCreateTime().toLocalDate();
            // 更新为最新的记录
            dailyLevelMap.put(date, record.getTotalLevel());
            dailyCountMap.put(date, dailyCountMap.getOrDefault(date, 0) + 1);
        }

        for (Map.Entry<LocalDate, String> entry : dailyLevelMap.entrySet()) {
            EvolutionBucket bucket = bucketsByDate.get(entry.getKey());
            if (bucket != null) {
                bucket.totalLevel = entry.getValue();
                bucket.scoreCount = dailyCountMap.getOrDefault(entry.getKey(), 0);
            }
        }

        for (Map<String, Object> row : dailyAgentDetails) {
            LocalDate date = toLocalDate(mapValue(row, "statDate", "stat_date"));
            EvolutionBucket bucket = bucketsByDate.get(date);
            if (bucket == null) continue;
            String agentName = String.valueOf(mapValue(row, "agentName", "agent_name"));
            if (agentName == null || agentName.isBlank() || "null".equals(agentName)) continue;
            int taskCount = toInt(mapValue(row, "taskCount", "task_count"));
            int dispatchedCount = toInt(mapValue(row, "dispatchedCount", "dispatched_count"));
            bucket.agentDetails.computeIfAbsent(agentName, k -> new int[]{0, 0});
            bucket.agentDetails.get(agentName)[0] += taskCount;
            bucket.agentDetails.get(agentName)[1] += dispatchedCount;
        }

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<Map<String, Object>> timeline = buckets.stream()
                .map(bucket -> bucket.toMap(timeFormatter))
                .toList();

        Set<String> allAgents = new LinkedHashSet<>();
        buckets.forEach(bucket -> allAgents.addAll(bucket.agentDetails.keySet()));
        EvolutionBucket latestLevelBucket = buckets.stream()
                .filter(bucket -> bucket.totalLevel != null)
                .reduce((first, second) -> second)
                .orElse(null);

        int totalTaskCount = buckets.stream()
                .flatMap(b -> b.agentDetails.values().stream())
                .mapToInt(arr -> arr[0]).sum();
        int totalDispatchedCount = buckets.stream()
                .flatMap(b -> b.agentDetails.values().stream())
                .mapToInt(arr -> arr[1]).sum();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskCount", totalTaskCount);
        summary.put("dispatchedCount", totalDispatchedCount);
        summary.put("agentCount", allAgents.size());
        summary.put("agentNames", new ArrayList<>(allAgents));
        summary.put("latestLevel", latestLevelBucket == null ? null : latestLevelBucket.totalLevel);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orgId", orgId);
        result.put("startTime", startTime.format(timeFormatter));
        result.put("endTime", endTime.format(timeFormatter));
        result.put("bucketMinutes", safeBucketMinutes);
        result.put("summary", summary);
        result.put("timeline", timeline);
        return result;
    }

    // ==================== 设备故障恢复定时检查 ====================

    /**
     * 定时任务：每小时检查运维Agent未完成任务的设备是否已恢复
     * <p>
     * 策略（方案B-批量查询）：一次性查询出当前所有故障设备ID集合，
     * 再逐条检查 DispatchTask 中 decisionData 携带的 deviceId 是否仍在故障集合中。
     * 若设备已不在故障列表中，则更新任务为已完成。
     */
    public void scheduledCheckDeviceFaultRecovery() {
        log.info("开始定时检查设备故障恢复状态...");
        try {
            checkDeviceFaultRecovery();
        } catch (Exception e) {
            log.error("定时检查设备故障恢复状态失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 设备故障恢复检查核心逻辑
     */
    public void checkDeviceFaultRecovery() {
        // 1. 查询运维Agent未完成的任务
        List<DispatchTask> unfinishedTasks = getBaseMapper().selectUnfinishedOpsAgentTasks();
        if (unfinishedTasks == null || unfinishedTasks.isEmpty()) {
            log.info("无运维Agent未完成任务，跳过设备故障恢复检查");
            return;
        }

        // 2. 一次性查询当前所有故障设备（方案B：批量查询，仅需1次API调用）
        Set<String> faultDeviceIds = queryAllFaultDeviceIds();
        if (faultDeviceIds == null) {
            // 查询故障设备失败，跳过本次检查
            return;
        }
        log.info("当前故障设备 {} 台，运维Agent未完成任务 {} 条", faultDeviceIds.size(), unfinishedTasks.size());

        // 3. 逐条检查任务中的设备是否已恢复
        int recoveredCount = 0;
        for (DispatchTask task : unfinishedTasks) {
            try {
                List<String> deviceIds = extractDeviceIds(task.getDecisionData());
                if (deviceIds.isEmpty()) {
                    log.debug("任务[{}]的decisionData中未提取到设备ID，跳过", task.getId());
                    continue;
                }

                // 检查是否所有设备都已恢复（不在故障集合中）
                boolean anyStillInFault = false;
                for (String deviceId : deviceIds) {
                    if (faultDeviceIds.contains(deviceId)) {
                        anyStillInFault = true;
                        break;
                    }
                }

                if (!anyStillInFault) {
                    // 所有设备都已恢复，更新任务状态为已完成
                    DispatchTask update = new DispatchTask();
                    update.setId(task.getId());
                    update.setResultExecuteStatus(1);
                    update.setResultExecuteUser("设备运维Agent");
                    update.setResultDesc("监测到设备已恢复");
                    update.setCompleteTime(LocalDateTime.now());
                    updateById(update);
                    recoveredCount++;
                    log.info("任务[{}]设备已恢复，更新为已完成, deviceIds={}", task.getId(), deviceIds);
                }
            } catch (Exception e) {
                log.error("处理任务[{}]异常: {}", task.getId(), e.getMessage(), e);
            }
        }
        log.info("设备故障恢复检查完成，共恢复任务 {} 条", recoveredCount);
    }

    /**
     * 一次性查询当前所有故障设备ID集合
     * 调用 mcp-server 的 StateListPage 接口，按 faultState=1 过滤
     *
     * @return 故障设备ID集合；查询失败返回null
     */
    private Set<String> queryAllFaultDeviceIds() {
        ScadaPageQueryDto queryDto = ScadaPageQueryDto.builder()
                .page("1")
                .limit("500")
                .orgId("3")
                .faultState("1")
                .build();

        AjaxResultVO result = mcpXClient.StateListPage(queryDto);
        if (result == null || !result.isOk()) {
            log.warn("查询故障设备列表失败: {}", result != null ? result.getMsg() : "无响应");
            return null;
        }

        List<?> dataList = result.getData();
        if (dataList == null || dataList.isEmpty()) {
            log.info("当前无故障设备");
            return new HashSet<>();
        }

        Set<String> faultDeviceIds = new HashSet<>();
        for (Object item : dataList) {
            if (item instanceof Map) {
                Object id = ((Map<?, ?>) item).get("id");
                if (id != null) {
                    faultDeviceIds.add(String.valueOf(id));
                }
            }
        }

        // 如果返回总数大于当前页数据量，说明有更多故障设备，分页补查
        int totalCount = 0;
        try {
            totalCount = result.getCount();
        } catch (Exception ignored) {
        }
        if (totalCount > dataList.size()) {
            log.warn("故障设备总数({})超过单页返回量({})，执行分页补查", totalCount, dataList.size());
            int totalPages = (int) Math.ceil((double) totalCount / 500);
            for (int p = 2; p <= totalPages; p++) {
                ScadaPageQueryDto pageDto = ScadaPageQueryDto.builder()
                        .page(String.valueOf(p))
                        .limit("500")
                        .orgId("3")
                        .faultState("1")
                        .build();
                AjaxResultVO pageResult = mcpXClient.StateListPage(pageDto);
                if (pageResult == null || !pageResult.isOk()) {
                    log.warn("分页查询故障设备第{}页失败", p);
                    break;
                }
                List<?> pageData = pageResult.getData();
                if (pageData == null || pageData.isEmpty()) break;
                for (Object item : pageData) {
                    if (item instanceof Map) {
                        Object id = ((Map<?, ?>) item).get("id");
                        if (id != null) {
                            faultDeviceIds.add(String.valueOf(id));
                        }
                    }
                }
            }
        }

        return faultDeviceIds;
    }

    /**
     * 从decisionData中提取设备ID列表
     * decisionData 统一为纯设备ID字符串，如 "1226586214233018368"
     *
     * @param decisionData 设备ID字符串
     * @return 设备ID列表，空值返回空列表
     */
    private List<String> extractDeviceIds(String decisionData) {
        if (decisionData == null || decisionData.isBlank()) {
            return List.of();
        }
        return List.of(decisionData.trim());
    }

    private Object mapValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) return row.get(key);
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
            }
        }
        return null;
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        if (value instanceof LocalDate date) return date;
        return LocalDate.parse(value.toString().substring(0, 10));
    }


    private int toInt(Object value) {
        return value == null ? 0 : Integer.parseInt(value.toString());
    }

    private static class EvolutionBucket {
        private final LocalDateTime time;
        // 运行水平：正常、注意、紧急
        private String totalLevel;
        private int scoreCount;
        private final Map<String, int[]> agentDetails = new LinkedHashMap<>();

        private EvolutionBucket(LocalDateTime time) {
            this.time = time;
        }

        private Map<String, Object> toMap(DateTimeFormatter formatter) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("time", time.format(formatter));
            map.put("totalLevel", totalLevel);
            map.put("agentCount", agentDetails.size());
            List<Map<String, Object>> agentsList = new ArrayList<>();
            for (Map.Entry<String, int[]> entry : agentDetails.entrySet()) {
                Map<String, Object> agentMap = new LinkedHashMap<>();
                agentMap.put("name", entry.getKey());
                agentMap.put("taskCount", entry.getValue()[0]);
                agentMap.put("dispatchedCount", entry.getValue()[1]);
                agentsList.add(agentMap);
            }
            map.put("agents", agentsList);
            return map;
        }
    }
}
