package dev.sentient.llm;

import java.util.concurrent.CompletableFuture;

/** One backend (OpenAI-compatible, Ollama, Anthropic, Gemini, ...). */
public interface LlmProvider {

    String name();

    boolean supportsTools();

    CompletableFuture<LlmResponse> chat(LlmRequest request);
}
