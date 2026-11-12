package com.sysware.xian.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 共享 RestTemplate 配置
 * <p>
 * 该 Bean 被 DispatchService / TtsConversationService 等长耗时 AI 调用共用，
 * 读超时对齐 spring.ai 的 600s 上限；连接超时必须设置，
 * 否则默认无限等待，一旦底层连接挂起会永久占住调用线程。
 *
 * @author sysware
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(600_000);
        return new RestTemplate(factory);
    }
}
