package com.czar.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class UserClientConfig {

    @Value("${czar.user.base-url}")
    private String userBaseUrl;

    @Value("${czar.internal.service-token}")
    private String serviceToken;

    @Bean("userRestClient")
    public RestClient userRestClient() {
        return RestClient.builder()
                .baseUrl(userBaseUrl)
                .defaultHeader("X-Service-Token", serviceToken)
                .build();
    }
}
