package dev.sentient.llm;

import java.util.List;

public class LlmRequest {

    public final List<ChatMessage> messages;
    public final List<LlmTool> tools;
    public final String model;
    public final double temperature;
    public final int maxTokens;

    public LlmRequest(List<ChatMessage> messages, List<LlmTool> tools, String model,
            double temperature, int maxTokens) {
        this.messages = messages;
        this.tools = tools == null ? List.of() : tools;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    public boolean hasTools() {
        return tools != null && !tools.isEmpty();
    }
}
