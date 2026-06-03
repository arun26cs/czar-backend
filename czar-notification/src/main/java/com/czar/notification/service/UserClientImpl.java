package com.czar.notification.service;

import com.czar.notification.dto.DeviceTokenInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

/**
 * Calls czar-user's internal endpoint to retrieve the FCM device token for a user.
 */
@Service
public class UserClientImpl implements UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClientImpl.class);

    private final RestClient restClient;

    public UserClientImpl(RestClient userRestClient) {
        this.restClient = userRestClient;
    }

    @Override
    public Optional<DeviceTokenInfo> getDeviceToken(UUID userId) {
        try {
            DeviceTokenInfo info = restClient.get()
                    .uri("/internal/v1/device-tokens?userId={userId}", userId)
                    .retrieve()
                    .body(DeviceTokenInfo.class);
            return Optional.ofNullable(info);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Failed to fetch device token for userId={}: {}", userId, ex.getMessage());
            return Optional.empty();
        }
    }
}
