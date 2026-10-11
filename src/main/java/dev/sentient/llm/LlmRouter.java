package dev.sentient.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import dev.sentient.Sentient;
import dev.sentient.config.SentientConfig;

/**
 * Routes a request through the configured providers in order, falling back on
 * failure. All calls are async and server-side only.
 */
public final class LlmRouter {

    private static volatile List<LlmProvider> providers = List.of();

    private LlmRouter() {
    }

    /** (Re)build the provider chain from the current config. */
    public static synchronized void reload() {
        SentientConfig cfg = Sentient.config();
        List<LlmProvider> built = new ArrayList<>();
        for (SentientConfig.Provider p : cfg.effectiveProviders()) {
            if (p == null || p.type == null) {
                continue;
            }
            String type = p.type.trim().toLowerCase();
            switch (type) {
                case "ollama" -> built.add(new OllamaProvider(p.name, p.baseUrl, p.model, p.supportsTools, cfg.timeoutSeconds));
                case "anthropic" -> built.add(new AnthropicProvider(p.name, p.baseUrl, p.apiKey, p.model, p.supportsTools, cfg.timeoutSeconds));
                case "gemini", "google" -> built.add(new GeminiProvider(p.name, p.baseUrl, p.apiKey, p.model, p.supportsTools, cfg.timeoutSeconds));
                default -> built.add(new OpenAiProvider(p.name, p.baseUrl, p.apiKey, p.model, p.supportsTools, cfg.timeoutSeconds));
            }
        }
        providers = List.copyOf(built);
        Sentient.LOGGER.info("LLM router ready with providers: {}", describe());
    }

    public static String describe() {
        if (providers.isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        for (LlmProvider p : providers) {
            if (sb.length() > 0) {
                sb.append(" -> ");
            }
            sb.append(p.name()).append('[').append(p.supportsTools() ? "tools" : "text").append(']');
        }
        return sb.toString();
    }

    public static CompletableFuture<LlmResponse> chat(List<ChatMessage> messages, List<LlmTool> tools) {
        SentientConfig cfg = Sentient.config();
        LlmRequest req = new LlmRequest(messages, tools, null, cfg.temperature, cfg.maxTokens);
        List<LlmProvider> ps = providers;
        if (ps.isEmpty()) {
            return CompletableFuture.failedFuture(new RuntimeException("no LLM providers configured"));
        }
        return attempt(ps, 0, req, new ArrayList<>());
    }

    public static CompletableFuture<String> complete(List<ChatMessage> messages) {
        return chat(messages, List.of()).thenApply(LlmResponse::requireText);
    }

    public static CompletableFuture<String> test(String prompt) {
        return complete(List.of(ChatMessage.user(prompt)));
    }

    private static CompletableFuture<LlmResponse> attempt(List<LlmProvider> ps, int index, LlmRequest req,
            List<String> errors) {
        if (index >= ps.size()) {
            return CompletableFuture.failedFuture(
                    new RuntimeException("all providers failed: " + String.join(" | ", errors)));
        }
        LlmProvider provider = ps.get(index);
        CompletableFuture<LlmResponse> future;
        try {
            future = provider.chat(req);
        } catch (Throwable t) {
            future = CompletableFuture.failedFuture(t);
        }
        return future.handle((resp, err) -> {
            if (err == null) {
                return CompletableFuture.completedFuture(resp);
            }
            String reason = rootMessage(err);
            errors.add(provider.name() + ": " + reason);
            Sentient.LOGGER.warn("LLM provider {} failed: {}", provider.name(), reason);
            return attempt(ps, index + 1, req, errors);
        }).thenCompose(f -> f);
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        String m = c.getClass().getSimpleName();
        if (c.getMessage() != null && !c.getMessage().isBlank()) {
            m += ": " + c.getMessage();
        }
        return HttpSupport.trim(m.replaceAll("\\s+", " ").trim(), 160);
    }
}
