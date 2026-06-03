package com.czar.voiceai.service;

import reactor.core.publisher.Mono;

/**
 * Thin abstraction over the Groq Chat Completions API.
 * Mocked in unit tests; {@link GroqClientImpl} is the production implementation.
 */
public interface GroqClient {

    /**
     * Sends a chat completion request to the Groq API.
     *
     * @param systemPrompt instruction context for the LLM
     * @param userMessage  the voice transcript or user input
     * @return the raw content string from the first choice
     */
    Mono<String> chat(String systemPrompt, String userMessage);
}
