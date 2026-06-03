package com.czar.conflict.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PlannerClientConfig {

    @Value("${czar.planner.base-url}")
    private String plannerBaseUrl;

    @Value("${czar.internal.service-token}")
    private String serviceToken;

    @Bean("plannerRestClient")
    public RestClient plannerRestClient() {
        return RestClient.builder()
                .baseUrl(plannerBaseUrl)
                .defaultHeader("X-Service-Token", serviceToken)
                .build();
    }
}
