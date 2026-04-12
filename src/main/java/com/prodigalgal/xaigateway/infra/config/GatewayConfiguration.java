package com.prodigalgal.xaigateway.infra.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
@EnableScheduling
public class GatewayConfiguration {

    @Bean
    Clock gatewayClock() {
        return Clock.systemUTC();
    }
}
