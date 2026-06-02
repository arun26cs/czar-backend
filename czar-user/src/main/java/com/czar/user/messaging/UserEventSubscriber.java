package com.czar.user.messaging;

import com.czar.common.messaging.PubSubMessageEnvelope;
import com.czar.user.domain.Preferences;
import com.czar.user.domain.UserProfile;
import com.czar.user.repository.PreferencesRepository;
import com.czar.user.repository.UserProfileRepository;
import com.czar.user.service.TagService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Subscribes to czar-user-events Pub/Sub topic.
 * On user.created: creates users_profile + preferences + seeds default tags.
 * PubSubTemplate is optional — no-op when emulator is unavailable (tests).
 */
@Component
public class UserEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(UserEventSubscriber.class);
    private static final String SUBSCRIPTION = "czar-user-events-sub";

    private final UserProfileRepository profileRepository;
    private final PreferencesRepository preferencesRepository;
    private final TagService tagService;
    private final ObjectMapper objectMapper;

    public UserEventSubscriber(
            Optional<PubSubTemplate> pubSubTemplate,
            UserProfileRepository profileRepository,
            PreferencesRepository preferencesRepository,
            TagService tagService,
            ObjectMapper objectMapper) {

        this.profileRepository = profileRepository;
        this.preferencesRepository = preferencesRepository;
        this.tagService = tagService;
        this.objectMapper = objectMapper;

        pubSubTemplate.ifPresentOrElse(
                template -> {
                    template.subscribe(SUBSCRIPTION, this::handleMessage);
                    log.info("Subscribed to Pub/Sub subscription: {}", SUBSCRIPTION);
                },
                () -> log.warn("PubSubTemplate not available — UserEventSubscriber inactive (local dev without emulator)")
        );
    }

    private void handleMessage(BasicAcknowledgeablePubsubMessage message) {
        String data = message.getPubsubMessage().getData().toStringUtf8();
        try {
            PubSubMessageEnvelope envelope = objectMapper.readValue(data, PubSubMessageEnvelope.class);

            if ("user.created".equals(envelope.getEventType())) {
                onUserCreated(envelope.getUserId(), envelope.getPayload());
            }
            message.ack();
        } catch (Exception e) {
            log.error("Failed to process Pub/Sub message: {}", e.getMessage(), e);
            message.nack();
        }
    }

    @Transactional
    protected void onUserCreated(String userIdStr, String payload) {
        UUID userId = UUID.fromString(userIdStr);

        // Idempotent — skip if profile already exists
        if (profileRepository.existsById(userId)) {
            log.debug("UserProfile already exists for userId={} — skipping seed", userId);
            return;
        }

        // Parse display name from payload (email prefix or phone)
        String displayName = deriveDisplayName(payload, userId);

        UserProfile profile = new UserProfile();
        profile.setId(userId);
        profile.setDisplayName(displayName);
        profileRepository.save(profile);

        Preferences prefs = new Preferences();
        prefs.setUserId(userId);
        preferencesRepository.save(prefs);

        tagService.seedDefaultTags(userId);

        log.info("Provisioned profile + preferences + default tags for userId={}", userId);
    }

    private String deriveDisplayName(String payload, UUID userId) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String email = node.path("email").asText("");
            if (!email.isBlank()) {
                return email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            }
            String phone = node.path("phone").asText("");
            if (!phone.isBlank()) {
                return phone;
            }
        } catch (Exception e) {
            log.debug("Could not parse display name from payload for userId={}", userId);
        }
        return "User";
    }
}
