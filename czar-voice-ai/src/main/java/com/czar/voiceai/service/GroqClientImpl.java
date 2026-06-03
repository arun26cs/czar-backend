package com.czar.voiceai.service;

import com.czar.voiceai.exception.GroqRateLimitException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Calls the Groq Chat Completions API (OpenAI-compatible endpoint).
 * Returns the raw content string from the first choice for downstream parsing.
 */
@Component
public class GroqClientImpl implements GroqClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${groq.model:llama-3.1-8b-instant}")
    private String model;

    public GroqClientImpl(
            @Qualifier("groqWebClient") WebClient webClient,
            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<String> chat(String systemPrompt, String userMessage) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                ),
                "temperature", 0.1,
                "max_tokens", 1024
        );

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.value() == 429, response ->
                        Mono.error(new GroqRateLimitException("Groq rate limit exceeded — retry after 60 seconds")))
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(err -> Mono.error(new RuntimeException("Groq API error: " + err))))
                .bodyToMono(GroqChatResponse.class)
                .map(resp -> resp.choices().get(0).message().content());
    }

    // -------------------------------------------------------------------------
    // Internal response DTOs — only used inside this class
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqChatResponse(List<GroqChoice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqChoice(GroqMessage message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqMessage(String role, String content) {}
}
