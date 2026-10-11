package dev.sentient.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Anthropic Messages API. */
public class AnthropicProvider implements LlmProvider {

    private final String name;
    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final boolean tools;
    private final int timeout;

    public AnthropicProvider(String name, String baseUrl, String apiKey, String defaultModel,
            boolean supportsTools, int timeoutSeconds) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.tools = supportsTools;
        this.timeout = timeoutSeconds;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean supportsTools() {
        return tools;
    }

    @Override
    public CompletableFuture<LlmResponse> chat(LlmRequest req) {
        JsonObject body = new JsonObject();
        body.addProperty("model", req.model != null && !req.model.isBlank() ? req.model : defaultModel);
        body.addProperty("max_tokens", req.maxTokens);
        body.addProperty("temperature", req.temperature);

        StringBuilder system = new StringBuilder();
        JsonArray msgs = new JsonArray();
        for (ChatMessage m : req.messages) {
            if (ChatMessage.SYSTEM.equals(m.role)) {
                if (system.length() > 0) {
                    system.append("\n\n");
                }
                system.append(m.content);
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("role", ChatMessage.ASSISTANT.equals(m.role) ? "assistant" : "user");
            o.addProperty("content", m.content);
            msgs.add(o);
        }
        if (system.length() > 0) {
            body.addProperty("system", system.toString());
        }
        body.add("messages", msgs);

        if (req.hasTools() && tools) {
            JsonArray arr = new JsonArray();
            for (LlmTool t : req.tools) {
                JsonObject o = new JsonObject();
                o.addProperty("name", t.name);
                o.addProperty("description", t.description);
                o.add("input_schema", t.parameters);
                arr.add(o);
            }
            body.add("tools", arr);
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("anthropic-version", "2023-06-01");
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("x-api-key", apiKey);
        }
        return HttpSupport.postJson(OpenAiProvider.endpoint(baseUrl, "/v1/messages"), headers, body, timeout)
                .thenApply(AnthropicProvider::parse);
    }

    public static LlmResponse parse(String body) {
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonObject()) {
            throw new RuntimeException("Anthropic response was not JSON: " + HttpSupport.trim(body, 200));
        }
        JsonObject obj = root.getAsJsonObject();
        if (obj.has("error")) {
            throw new RuntimeException("Anthropic error: " + HttpSupport.trim(obj.get("error").toString(), 300));
        }
        JsonArray content = obj.getAsJsonArray("content");
        if (content == null) {
            throw new RuntimeException("Anthropic response had no content: " + HttpSupport.trim(body, 200));
        }
        StringBuilder text = new StringBuilder();
        List<LlmToolCall> calls = new ArrayList<>();
        for (JsonElement el : content) {
            JsonObject block = el.getAsJsonObject();
            String type = block.has("type") ? block.get("type").getAsString() : "";
            if ("text".equals(type) && block.has("text")) {
                text.append(block.get("text").getAsString());
            } else if ("tool_use".equals(type)) {
                String id = block.has("id") ? block.get("id").getAsString() : "";
                String fname = block.get("name").getAsString();
                JsonObject args = block.has("input") && block.get("input").isJsonObject()
                        ? block.getAsJsonObject("input") : new JsonObject();
                calls.add(new LlmToolCall(id, fname, args));
            }
        }
        return new LlmResponse(text.toString(), calls);
    }
}
