package com.czar.voiceai.exception;

/**
 * Thrown when the Groq API returns HTTP 429 (rate limit exceeded).
 * Maps to HTTP 503 Service Unavailable with a Retry-After header.
 */
public class GroqRateLimitException extends RuntimeException {
    public GroqRateLimitException(String message) {
        super(message);
    }
}
