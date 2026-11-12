package com.sysware.xian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.sysware.xian.domain.AiAnalysisResult;
import com.sysware.xian.domain.BDeviceEventEntity;
import com.sysware.xian.domain.DeviceEventTaskMapping;
import com.sysware.xian.domain.DispatchTask;
import com.sysware.xian.mapper.DeviceEventTaskMappingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 设备事件广播消费者（iFound 独立队列）
 * <p>
 * 绑定到 exchange.device.event.message (TOPIC)，路由 key 为 0.#
 * 队列名 queue.scada.device.eventIFound 与其他消费者不同，
 * 确保每个消费者各收一份广播消息（而非负载均衡）。
 * <p>
 * 当事件级别为 3（故障）时，自动创建维修工单派发任务，并复用同设备最新AI分析建议
 * （ai_analysis_result，type=device_fault_repair）拼接 decisionContent；
 * 无缓存结果时发送异步分析请求（见 DeviceFaultAnalysisReceiver），分析完成后回写任务。
 * 同一设备已有未完成派单任务时，追加 problemDesc 而非新建；
 * problemDesc 恒以“设备{设备名}故障：”开头，追加事项以换行分隔，超2000字覆盖为最新事件。
 * 每次故障事件都会记录到 device_event_task_mapping 映射表，便于追溯。
 *
 * @author sysware
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DeviceEventReceiver {

    private final ObjectMapper objectMapper;
    private final DispatchTaskService dispatchTaskService;
    private final DeviceEventTaskMappingMapper eventTaskMappingMapper;
    private final DeviceFaultAiAnalysisService deviceFaultAiAnalysisService;

    /** 事件级别：3 = 故障 */
    private static final int EVENT_LEVEL_FAULT = 3;
    /** 追加 problemDesc 时使用的时间格式 */
    private static final DateTimeFormatter APPEND_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** problemDesc 最大长度（追加超过则覆盖为最新事件） */
    private static final int PROBLEM_DESC_MAX_LENGTH = 2000;

    @RabbitListener(bindings = @QueueBinding(
            // 关键：队列名必须不同，否则会和消费者1共享一个队列（变成负载均衡，而不是各收一份）
            value = @Queue(value = "queue.scada.device.eventIFound", durable = "true", autoDelete = "false"),
            exchange = @Exchange(value = "exchange.device.event.message", type = ExchangeTypes.TOPIC, durable = "true"),
            // 路由 key 与消费者1保持一致，这样同一条消息两个队列都能匹配到
            key = "0.#"))
    @RabbitHandler
    public void onMessage(Message message, Channel channel) throws Exception {
        Long deliveryTag = (Long) message.getMessageProperties().getDeliveryTag();
        // MQ 消息体以 ISO-8859-1 传输，实际内容为 UTF-8，直接按 UTF-8 解码
        String strBody = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
//            log.debug("【iFound消费者】设备事件: {}", strBody);
            BDeviceEventEntity deviceEvent = objectMapper.readValue(strBody, BDeviceEventEntity.class);
            process(deviceEvent);
        } catch (Exception e) {
            log.error("【iFound消费者】设备事件处理异常: {}", strBody, e);
        } finally {
            channel.basicAck(deliveryTag, false);
        }
    }

    /**
     * 设备事件处理逻辑
     */
    private void process(BDeviceEventEntity deviceEvent) {
        // 清洗 eventInfo：去掉"遥信变位："前缀，避免在工单描述中出现冗余字样
        String eventInfo = deviceEvent.getEventInfo();
        if (eventInfo != null && eventInfo.startsWith("遥信变位：")) {
            deviceEvent.setEventInfo(eventInfo.substring(5));
        }
        // 事件级别 3 = 故障，自动创建/追加维修工单派发任务
        if (deviceEvent.getEventLevel() != null && deviceEvent.getEventLevel() == EVENT_LEVEL_FAULT) {
            try {
            log.debug("【iFound消费者】故障设备事件: {}", deviceEvent);
                String deviceId = deviceEvent.getDeviceId();
                String deviceName = deviceEvent.getDeviceName();
                // 查询该设备是否已存在未完成的派单任务（decisionData = deviceId, resultExecuteStatus = '0'）
                DispatchTask existingTask = dispatchTaskService.getOne(
                        new LambdaQueryWrapper<DispatchTask>()
                                .eq(DispatchTask::getDecisionData, deviceId)
                                .eq(DispatchTask::getResultExecuteStatus, "0")
                                .eq(DispatchTask::getTargetAgentId, "ops-agent")
                                .last("LIMIT 1"));

                if (existingTask != null) {
                    String oldDesc = existingTask.getProblemDesc();
                    String newSegment = deviceEvent.getEventInfo() + " " + LocalDateTime.now().format(APPEND_FORMATTER);
                    String faultPrefix = "设备" + deviceName + "故障：";
                    // 兜底历史数据：problemDesc 必须以“设备X故障：”开头，缺失时补齐前缀
                    if (oldDesc != null && !oldDesc.isBlank() && !oldDesc.startsWith(faultPrefix)) {
                        oldDesc = faultPrefix + "\n" + oldDesc;
                    }
                    // 追加事项以换行分隔；追加后超过2000字则直接覆盖为“设备X故障：”+最新事件
                    String appendDesc;
                    if (oldDesc != null && !oldDesc.isBlank()
                            && oldDesc.length() + newSegment.length() + 1 <= PROBLEM_DESC_MAX_LENGTH) {
                        appendDesc = oldDesc + "\n" + newSegment;
                    } else {
                        appendDesc = faultPrefix + "\n" + newSegment;
                        log.warn("【iFound消费者】problemDesc超过上限({}字)或缺失，覆盖为最新事件 - 设备: {}, taskId: {}",
                                PROBLEM_DESC_MAX_LENGTH, deviceName, existingTask.getId());
                    }
                    DispatchTask update = new DispatchTask();
                    update.setId(existingTask.getId());
                    update.setProblemDesc(appendDesc);
                    // 顺手补充AI建议：该设备已有分析结果时覆盖式写入（幂等），避免等异步回写
                    AiAnalysisResult cachedAdvice = deviceFaultAiAnalysisService.findLatestResult(deviceId);
                    String advice = cachedAdvice != null ? cachedAdvice.getResultData() : null;
                    if (advice != null && !advice.isBlank()) {
                        update.setDecisionContent(deviceFaultAiAnalysisService.buildDecisionContent(deviceName, advice));
                    }
                    dispatchTaskService.updateById(update);
                    log.info("【iFound消费者】设备故障追加问题描述 - 设备: {}, taskId: {}",
                            deviceName, existingTask.getId());

                    // 无缓存建议时异步分析，完成后由分析消费者回写本任务的decisionContent
                    if (advice == null || advice.isBlank()) {
                        deviceFaultAiAnalysisService.requestAnalysis(deviceId, deviceName);
                    }

                    // 保存事件-任务映射记录
                    saveEventTaskMapping(deviceEvent, existingTask.getId());
                    return;
                }

                // 不存在未完成任务，新建；复用同设备最新AI分析建议，无则保持固定文案
                AiAnalysisResult cachedAdvice = deviceFaultAiAnalysisService.findLatestResult(deviceId);
                String advice = cachedAdvice != null ? cachedAdvice.getResultData() : null;
                DispatchTask task = new DispatchTask();
                task.setSubareaCid(deviceEvent.getSubareaCid());
                task.setProblemDesc("设备" + deviceName + "故障：\n" + deviceEvent.getEventInfo()
                        + " " + LocalDateTime.now().format(APPEND_FORMATTER));
                // 数据快照：BDeviceEventEntity 的 JSON 字符串
                task.setDataSnapshot(objectMapper.writeValueAsString(deviceEvent));
                task.setCreatorAgentId("commander");
                task.setCreatorAgentName("系统Agent");
                task.setBusinessDomain("维修工单");
                task.setLevel("高");
                task.setCreateTime(LocalDateTime.now());
                task.setThinkingOverview("设备故障需立即派单维修");
                // decisionData 直接存纯设备ID字符串，便于去重查询
                task.setDecisionData(deviceId);
                task.setTargetAgentId("ops-agent");
                task.setTargetAgentName("设备运维Agent");
                task.setTaskType("collaboration");
                task.setResultExecuteStatus(0);
                task.setProblemSummary(deviceName + "故障，需人工介入维修。");
                task.setDecisionContent(deviceFaultAiAnalysisService.buildDecisionContent(deviceName, advice));
                task.setExpectedEffect("修复" + deviceName + "故障，恢复设备功能");

                dispatchTaskService.createTask(task);
                log.info("【iFound消费者】设备故障派单任务已创建 - 设备: {}, deviceId: {}, taskId: {}",
                        deviceName, deviceId, task.getId());

                // 无缓存建议时异步分析，完成后由分析消费者回写本任务的decisionContent
                if (advice == null || advice.isBlank()) {
                    deviceFaultAiAnalysisService.requestAnalysis(deviceId, deviceName);
                }

                // 保存事件-任务映射记录
                saveEventTaskMapping(deviceEvent, task.getId());
            } catch (Exception e) {
                log.error("【iFound消费者】创建/追加设备故障派单任务失败 - 设备: {}",
                        deviceEvent.getDeviceName(), e);
            }
        }
    }

    /**
     * 保存设备事件与派单任务的映射记录
     */
    private void saveEventTaskMapping(BDeviceEventEntity deviceEvent, String taskId) {
        try {
            DeviceEventTaskMapping mapping = new DeviceEventTaskMapping();
            mapping.setEventId(deviceEvent.getId());
            mapping.setTaskId(taskId);
            mapping.setDeviceId(deviceEvent.getDeviceId());
            mapping.setDeviceName(deviceEvent.getDeviceName());
            mapping.setEventInfo(deviceEvent.getEventInfo());
            mapping.setHappenTime(deviceEvent.getHappenTime());
            mapping.setCreateTime(LocalDateTime.now());
            eventTaskMappingMapper.insert(mapping);
            log.debug("【iFound消费者】事件-任务映射已保存 - eventId: {}, taskId: {}",
                    deviceEvent.getId(), taskId);
        } catch (Exception e) {
            log.error("【iFound消费者】保存事件-任务映射失败 - eventId: {}, taskId: {}",
                    deviceEvent.getId(), taskId, e);
        }
    }
}
