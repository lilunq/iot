package com.sysware.xian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.sysware.xian.domain.DeviceFaultAnalysisMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 设备故障AI异步分析消费者（优先消费最新消息）
 * <p>
 * AI分析一次约10秒，若在监听线程内同步处理会阻塞后续消息且只能FIFO。这里实现消费端排序：
 * <ul>
 *   <li>监听线程只做解码入堆并立即返回（大prefetch持续拉取），消息按到达时间倒序存放在
 *       {@link PriorityBlockingQueue} 中，堆顶始终是最新消息；</li>
 *   <li>单工作线程取堆顶执行分析（同设备串行，配合查库去重避免重复分析），分析完成后手动ack；</li>
 *   <li>同一设备堆内只保留最新一条，被替换的旧消息当场ack丢弃——告警风暴下同设备多次
 *       告警只需分析一次；</li>
 *   <li>可靠性：消息在分析完成前一直是unacked状态，应用崩溃/停机后由broker重投；
 *       重投后命中 {@link DeviceFaultAiAnalysisService#analyzeAndApply} 内的查库去重被丢弃，不会重复分析；</li>
 *   <li>堆积上限=analysisContainerFactory的prefetch，天然背压。</li>
 * </ul>
 *
 * @author sysware
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DeviceFaultAnalysisReceiver {

    private final ObjectMapper objectMapper;
    private final DeviceFaultAiAnalysisService deviceFaultAiAnalysisService;

    /** 按到达时间倒序的本地缓冲堆：分析耗时约10秒，期间持续入堆，保证最新消息优先被处理 */
    private final PriorityBlockingQueue<AnalysisJob> jobHeap = new PriorityBlockingQueue<>(64,
            Comparator.comparingLong((AnalysisJob job) -> job.arriveTime).reversed());

    private volatile boolean running = false;
    private Thread workerThread;

    @RabbitListener(queuesToDeclare = @Queue(value = DeviceFaultAiAnalysisService.ANALYSIS_QUEUE,
            durable = "true", autoDelete = "false"),
            containerFactory = "analysisContainerFactory")
    public void onMessage(Message message, Channel channel) {
        String strBody = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            DeviceFaultAnalysisMessage msg = objectMapper.readValue(strBody, DeviceFaultAnalysisMessage.class);
            if (msg.getDeviceId() == null || msg.getDeviceId().isBlank()) {
                log.warn("【设备故障AI分析消费者】消息缺少deviceId，丢弃: {}", strBody);
                ackQuietly(channel, message.getMessageProperties().getDeliveryTag());
                return;
            }
            AnalysisJob job = new AnalysisJob(msg.getDeviceId(), msg.getDeviceName(), System.currentTimeMillis(),
                    message.getMessageProperties().getDeliveryTag(), channel);
            // 同设备只保留堆内最新一条，被替换的旧消息由监听线程当场ack（丢弃）
            AnalysisJob older = removeByDeviceId(msg.getDeviceId());
            if (older != null) {
                ackQuietly(older.channel, older.deliveryTag);
            }
            jobHeap.offer(job);
        } catch (Exception e) {
            // 解析失败属毒消息，ack丢弃避免无限重投
            log.error("【设备故障AI分析消费者】消息解析失败: {}", strBody, e);
            ackQuietly(channel, message.getMessageProperties().getDeliveryTag());
        }
    }

    @PostConstruct
    public void start() {
        running = true;
        workerThread = new Thread(this::runWorker, "device-fault-ai-analysis-worker");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("【设备故障AI分析消费者】工作线程已启动");
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
        // 堆内未处理的消息不做ack，应用重启后由broker重投，不丢消息
    }

    /**
     * 工作线程：串行取堆顶（最新）消息执行分析
     */
    private void runWorker() {
        while (running) {
            AnalysisJob job;
            try {
                job = jobHeap.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (job == null) {
                continue;
            }
            try {
                deviceFaultAiAnalysisService.analyzeAndApply(job.deviceId, job.deviceName);
            } catch (Exception e) {
                log.error("【设备故障AI分析消费者】分析执行异常 - 设备: {}, deviceId: {}",
                        job.deviceName, job.deviceId, e);
            } finally {
                ackQuietly(job.channel, job.deliveryTag);
            }
        }
        log.info("【设备故障AI分析消费者】工作线程已退出");
    }

    /**
     * 从堆中移除同设备的待处理任务（堆内允许重复deviceId时保留最新，移除较旧的一条）
     */
    private AnalysisJob removeByDeviceId(String deviceId) {
        for (AnalysisJob job : jobHeap) {
            if (job.deviceId.equals(deviceId) && jobHeap.remove(job)) {
                return job;
            }
        }
        return null;
    }

    private void ackQuietly(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // ack失败（如连接中断）时消息由broker重投，重投后命中查库去重被丢弃，无副作用
            log.warn("【设备故障AI分析消费者】ack失败，等待broker重投 - deliveryTag: {}", deliveryTag, e);
        }
    }

    /**
     * 一个待分析任务：arriveTime 用于堆排序（最新优先），channel/deliveryTag 用于分析完成后ack
     */
    private static class AnalysisJob {
        final String deviceId;
        final String deviceName;
        final long arriveTime;
        final long deliveryTag;
        final Channel channel;

        AnalysisJob(String deviceId, String deviceName, long arriveTime, long deliveryTag, Channel channel) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.arriveTime = arriveTime;
            this.deliveryTag = deliveryTag;
            this.channel = channel;
        }
    }
}
