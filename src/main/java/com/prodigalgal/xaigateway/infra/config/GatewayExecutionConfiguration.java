package com.prodigalgal.xaigateway.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class GatewayExecutionConfiguration {

    @Bean
    AsyncTaskExecutor applicationTaskExecutor() {
        return new VirtualThreadTaskExecutor("xag-app-");
    }

    @Bean(name = "blockingTaskExecutor")
    AsyncTaskExecutor blockingTaskExecutor() {
        return new VirtualThreadTaskExecutor("xag-blocking-");
    }
}
