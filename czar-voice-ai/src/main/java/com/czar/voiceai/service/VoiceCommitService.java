package com.czar.voiceai.service;

import com.czar.voiceai.dto.ParsedItem;
import com.czar.voiceai.dto.VoiceCommitRequest;
import com.czar.voiceai.dto.VoiceCommitResponse;
import com.czar.voiceai.messaging.VoiceResultPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Validates approved items and publishes them to the czar-ai-results Pub/Sub topic.
 *
 * <p>Phase 8 (czar-conflict / czar-notification) will subscribe and create the
 * actual plans and notes in the downstream services.
 *
 * <p>Server-side field validation is intentionally re-run here — the client is
 * trusted to fill in missingFields but the server enforces correctness independently.
 */
@Service
public class VoiceCommitService {

    private final VoiceResultPublisher publisher;

    public VoiceCommitService(VoiceResultPublisher publisher) {
        this.publisher = publisher;
    }

    public VoiceCommitResponse commit(UUID userId, VoiceCommitRequest request) {
        request.items().forEach(this::validateItem);

        UUID jobId = UUID.randomUUID();
        publisher.publish("ai.result.ready", userId.toString(), jobId.toString(), request.items());
        return new VoiceCommitResponse(jobId, request.items().size());
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private void validateItem(ParsedItem item) {
        if (item.type() == null || (!item.type().equals("plan") && !item.type().equals("note"))) {
            throw new IllegalArgumentException("Item type must be 'plan' or 'note', got: " + item.type());
        }
        if ("plan".equals(item.type())) {
            require(item.title() != null && !item.title().isBlank(), "Plan title is required");
            require(item.scheduledDate() != null, "Plan scheduledDate is required");
            require(item.hour() != null && item.hour() >= 0 && item.hour() <= 23,
                    "Plan hour (0-23) is required");
            require(item.durationMinutes() != null && item.durationMinutes() >= 1,
                    "Plan durationMinutes (>= 1) is required");
        } else {
            require(item.title() != null && !item.title().isBlank(), "Note title is required");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
