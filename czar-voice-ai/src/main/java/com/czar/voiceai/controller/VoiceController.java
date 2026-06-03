package com.czar.voiceai.controller;

import com.czar.voiceai.dto.VoiceCommitRequest;
import com.czar.voiceai.dto.VoiceCommitResponse;
import com.czar.voiceai.dto.VoiceParseRequest;
import com.czar.voiceai.dto.VoiceParseResponse;
import com.czar.voiceai.exception.GroqRateLimitException;
import com.czar.voiceai.service.VoiceCommitService;
import com.czar.voiceai.service.VoiceParseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

/**
 * Voice AI endpoints.
 *
 * <p>POST /api/v1/voice/parse  — transcript → Groq → typed items with missing-field flags.
 * <p>POST /api/v1/voice/commit — approved items → Pub/Sub (czar-ai-results) → jobId.
 */
@RestController
@RequestMapping("/api/v1/voice")
public class VoiceController {

    private final VoiceParseService voiceParseService;
    private final VoiceCommitService voiceCommitService;

    public VoiceController(VoiceParseService voiceParseService, VoiceCommitService voiceCommitService) {
        this.voiceParseService = voiceParseService;
        this.voiceCommitService = voiceCommitService;
    }

    /**
     * Parses a voice transcript into structured plan and note items.
     *
     * <p>Returns HTTP 200 with parsed items (some may have {@code missingFields}).
     * Returns HTTP 503 with {@code Retry-After: 60} header on Groq rate limit.
     */
    @PostMapping("/parse")
    public Mono<ResponseEntity<VoiceParseResponse>> parse(
            Authentication auth,
            @Valid @RequestBody VoiceParseRequest request) {
        UUID userId = UUID.fromString(auth.getName());
        return voiceParseService.parse(userId, request)
                .map(ResponseEntity::ok)
                .onErrorResume(GroqRateLimitException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .header("Retry-After", "60")
                                .<VoiceParseResponse>build()));
    }

    /**
     * Commits user-approved items to Pub/Sub for downstream plan/note creation.
     *
     * <p>Returns HTTP 200 with a {@code jobId} that Phase 8 consumers can correlate.
     * Returns HTTP 422 if any item has missing required fields.
     */
    @PostMapping("/commit")
    public ResponseEntity<VoiceCommitResponse> commit(
            Authentication auth,
            @Valid @RequestBody VoiceCommitRequest request) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(voiceCommitService.commit(userId, request));
    }

    // -------------------------------------------------------------------------
    // Local exception handlers
    // -------------------------------------------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadCommit(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setType(URI.create("https://czar.app/errors/validation"));
        pd.setTitle("Commit Validation Failed");
        pd.setDetail(ex.getMessage());
        return ResponseEntity.unprocessableEntity().body(pd);
    }
}
