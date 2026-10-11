package dev.sentient.llm;

import java.util.List;

public class LlmResponse {

    public final String content;
    public final List<LlmToolCall> toolCalls;

    public LlmResponse(String content, List<LlmToolCall> toolCalls) {
        this.content = content == null ? "" : content;
        this.toolCalls = toolCalls == null ? List.of() : toolCalls;
    }

    public static LlmResponse text(String content) {
        return new LlmResponse(content, List.of());
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /** Returns the text, or throws if the model produced nothing usable. */
    public String requireText() {
        if (content == null || content.isBlank()) {
            throw new RuntimeException("LLM returned an empty reply");
        }
        return content.trim();
    }
}
