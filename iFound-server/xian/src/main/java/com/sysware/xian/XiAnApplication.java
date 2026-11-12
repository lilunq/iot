package com.sysware.xian;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = "com.sysware.xian.swcloud.fegin")
public class XiAnApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiAnApplication.class, args);
    }
}
