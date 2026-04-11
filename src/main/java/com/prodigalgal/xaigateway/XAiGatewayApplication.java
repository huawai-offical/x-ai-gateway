package com.prodigalgal.xaigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class XAiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(XAiGatewayApplication.class, args);
    }

}
