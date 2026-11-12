package com.sysware.xian.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 * <p>
 * 全局监听器配置见 application.yml（手动ACK、prefetch=10），供 DeviceEventReceiver 等使用。
 * 这里额外提供一个高吞吐的手动ACK容器工厂，仅供设备故障AI分析消费者（DeviceFaultAnalysisReceiver）使用：
 * 大 prefetch 让容器在单条分析耗时（约10秒）期间持续把后续消息拉入消费端本地缓冲堆排序，
 * 实现「优先消费最新消息」，堆积上限即为 prefetch。
 *
 * @author sysware
 */
@Configuration
public class RabbitMQConfig {

    /** 设备故障AI分析消费者的prefetch：本地缓冲堆的容量上限 */
    private static final int ANALYSIS_PREFETCH = 40;

    @Bean
    public SimpleRabbitListenerContainerFactory analysisContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // 手动ACK：消息先进本地堆排序，由工作线程分析完成后确认；未确认消息在崩溃/停机后由broker重投
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(ANALYSIS_PREFETCH);
        return factory;
    }
}
