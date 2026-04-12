package com.prodigalgal.xaigateway.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

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

    @Bean
    TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("xag-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(1);
        scheduler.setDaemon(true);
        return scheduler;
    }
}
