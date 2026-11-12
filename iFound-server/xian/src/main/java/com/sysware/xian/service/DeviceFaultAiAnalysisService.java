package com.sysware.xian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.xian.domain.AiAnalysisResult;
import com.sysware.xian.domain.DeviceFaultAnalysisMessage;
import com.sysware.xian.domain.DispatchTask;
import com.sysware.xian.swcloud.domain.KnowledgeRequest;
import com.sysware.xian.swcloud.domain.KnowledgeResponse;
import com.sysware.xian.swcloud.fegin.MCPXClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备故障AI分析服务
 * <p>
 * 派单任务生成（DeviceEventReceiver）与AI分析解耦：
 * <ul>
 *   <li>任务立即生成，decisionContent 先用固定文案；同设备已有分析结果时复用（取最新一条）拼接建议；</li>
 *   <li>无缓存结果时发送分析请求到 {@link #ANALYSIS_QUEUE}，由 DeviceFaultAnalysisReceiver 异步分析；</li>
 *   <li>分析结果落库 ai_analysis_result（type=device_fault_repair, o_data=deviceId）供同设备复用，
 *       历史记录由定时任务清理兜底，读侧不做新鲜度过滤；</li>
 *   <li>分析完成后把该设备所有未完成派单任务的 decisionContent 覆盖为「固定文案 + AI处置建议」。</li>
 * </ul>
 *
 * @author sysware
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceFaultAiAnalysisService {

    /** 分析结果类型：设备故障维修建议 */
    public static final String RESULT_TYPE = "device_fault_repair";
    /** AI分析请求队列名（默认交换机直发，路由键=队列名，由分析消费者自动声明） */
    public static final String ANALYSIS_QUEUE = "queue.ai.device.fault.analysis";

    /** decisionContent 最大长度（截断兜底），与 DeviceEventReceiver 的 problemDesc 上限口径一致 */
    private static final int DECISION_CONTENT_MAX_LENGTH = 2000;

    private final AiAnalysisResultService aiAnalysisResultService;
    private final DispatchTaskService dispatchTaskService;
    private final MCPXClient mcpxClient;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 查询指定设备最新的AI分析建议（同设备复用，无新鲜度窗口）
     */
    public AiAnalysisResult findLatestResult(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return null;
        }
        return aiAnalysisResultService.getOne(
                new LambdaQueryWrapper<AiAnalysisResult>()
                        .eq(AiAnalysisResult::getType, RESULT_TYPE)
                        .eq(AiAnalysisResult::getOData, deviceId)
                        .orderByDesc(AiAnalysisResult::getCreateTime)
                        .last("LIMIT 1"));
    }

    /**
     * 拼接派单任务的 decisionContent：固定文案 + AI处置建议。
     * 覆盖式写入（整体 set 而非追加），重复回写天然幂等。
     */
    public String buildDecisionContent(String deviceName, String advice) {
        String content = "设备[" + deviceName + "]故障，派单维修。";
        if (advice != null && !advice.isBlank()) {
            content = content + "\n—— AI处置建议 ——\n" + advice.trim();
        }
        if (content.length() > DECISION_CONTENT_MAX_LENGTH) {
            content = content.substring(0, DECISION_CONTENT_MAX_LENGTH);
        }
        return content;
    }

    /**
     * 发送异步AI分析请求到分析队列。
     * 发布失败不影响主流程（任务已生成），同设备下次告警会再次触发分析。
     */
    public void requestAnalysis(String deviceId, String deviceName) {
        try {
            DeviceFaultAnalysisMessage message = new DeviceFaultAnalysisMessage(deviceId, deviceName);
            rabbitTemplate.convertAndSend(ANALYSIS_QUEUE, objectMapper.writeValueAsString(message));
            log.info("【设备故障AI分析】已发送异步分析请求 - 设备: {}, deviceId: {}", deviceName, deviceId);
        } catch (Exception e) {
            log.error("【设备故障AI分析】发送分析请求失败 - 设备: {}, deviceId: {}", deviceName, deviceId, e);
        }
    }

    /**
     * 执行AI分析并落库、回写任务（由分析消费者调用，约10秒级耗时，不要在MQ监听线程直接调用）
     *
     * @return true=已获得分析结果（本次分析成功或缓存命中），false=调用失败或知识库未命中（任务保持固定文案，等下次告警重试）
     */
    public boolean analyzeAndApply(String deviceId, String deviceName) {
        // 双重去重：堆内已按设备去重，这里再查一次库，重投/重复消息命中缓存时直接跳过，避免重复分析
        AiAnalysisResult cached = findLatestResult(deviceId);
        if (cached != null) {
            log.debug("【设备故障AI分析】缓存命中，跳过分析 - deviceId: {}", deviceId);
            return true;
        }

        String question = deviceName + "故障，怎么办，含工具";
        KnowledgeResponse response = mcpxClient.deviceFault(new KnowledgeRequest(question, false, true, true, null));
        if (response == null || Boolean.FALSE.equals(response.getSuccess())) {
            log.warn("【设备故障AI分析】知识库调用失败 - 设备: {}, deviceId: {}, error: {}",
                    deviceName, deviceId, response != null ? response.getError() : "响应为空");
            return false;
        }
        String answer = response.answerText();
        if (answer.isBlank()) {
            // 知识库未命中：不缓存空结果，也不往任务里追加空建议
            log.info("【设备故障AI分析】知识库未命中，无建议 - 设备: {}, deviceId: {}", deviceName, deviceId);
            return false;
        }

        // 结果落库，同设备后续告警直接复用
        AiAnalysisResult record = new AiAnalysisResult();
        record.setType(RESULT_TYPE);
        record.setOData(deviceId);
        record.setResultData(answer.trim());
        record.setRemake("设备故障分析（" + deviceName + "）");
        record.setCreateTime(LocalDateTime.now());
        aiAnalysisResultService.save(record);

        updateUnfinishedTasks(deviceId, deviceName, answer.trim());
        log.info("【设备故障AI分析】分析完成并回写任务 - 设备: {}, deviceId: {}, 建议长度: {}",
                deviceName, deviceId, answer.length());
        return true;
    }

    /**
     * 将AI建议覆盖写入该设备所有未完成的派单任务（decisionData=deviceId, resultExecuteStatus='0', ops-agent）
     */
    private void updateUnfinishedTasks(String deviceId, String deviceName, String advice) {
        try {
            List<DispatchTask> unfinishedTasks = dispatchTaskService.list(
                    new LambdaQueryWrapper<DispatchTask>()
                            .eq(DispatchTask::getDecisionData, deviceId)
                            .eq(DispatchTask::getResultExecuteStatus, "0")
                            .eq(DispatchTask::getTargetAgentId, "ops-agent"));
            for (DispatchTask task : unfinishedTasks) {
                DispatchTask update = new DispatchTask();
                update.setId(task.getId());
                update.setDecisionContent(buildDecisionContent(deviceName, advice));
                dispatchTaskService.updateById(update);
            }
            if (!unfinishedTasks.isEmpty()) {
                log.info("【设备故障AI分析】已回写 {} 个未完成任务的处置建议 - deviceId: {}",
                        unfinishedTasks.size(), deviceId);
            }
        } catch (Exception e) {
            // 回写失败不影响结果落库：同设备下次告警时会在任务生成/追加路径中补充建议
            log.error("【设备故障AI分析】回写任务处置建议失败 - 设备: {}, deviceId: {}", deviceName, deviceId, e);
        }
    }
}
