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

/** OpenAI-compatible /chat/completions. Covers OpenAI, OpenRouter, llama.cpp, LM Studio, vLLM, proxies. */
public class OpenAiProvider implements LlmProvider {

    private final String name;
    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final boolean tools;
    private final int timeout;

    public OpenAiProvider(String name, String baseUrl, String apiKey, String defaultModel,
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
        body.addProperty("temperature", req.temperature);
        body.addProperty("max_tokens", req.maxTokens);
        body.addProperty("stream", false);

        JsonArray msgs = new JsonArray();
        for (ChatMessage m : req.messages) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role);
            o.addProperty("content", m.content);
            msgs.add(o);
        }
        body.add("messages", msgs);

        if (req.hasTools() && tools) {
            body.add("tools", openAiTools(req.tools));
            body.addProperty("tool_choice", "auto");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        return HttpSupport.postJson(endpoint(baseUrl, "/chat/completions"), headers, body, timeout)
                .thenApply(OpenAiProvider::parse);
    }

    public static String endpoint(String base, String path) {
        String b = base == null ? "" : base.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        if (b.endsWith(path)) {
            return b;
        }
        return b + path;
    }

    public static JsonArray openAiTools(List<LlmTool> tools) {
        JsonArray arr = new JsonArray();
        for (LlmTool t : tools) {
            JsonObject fn = new JsonObject();
            fn.addProperty("name", t.name);
            fn.addProperty("description", t.description);
            fn.add("parameters", t.parameters);
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("type", "function");
            wrapper.add("function", fn);
            arr.add(wrapper);
        }
        return arr;
    }

    public static LlmResponse parse(String body) {
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonObject()) {
            throw new RuntimeException("response was not a JSON object: " + HttpSupport.trim(body, 200));
        }
        JsonObject obj = root.getAsJsonObject();
        if (obj.has("error")) {
            throw new RuntimeException("LLM error: " + HttpSupport.trim(obj.get("error").toString(), 300));
        }
        JsonArray choices = obj.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("response had no choices: " + HttpSupport.trim(body, 200));
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null) {
            throw new RuntimeException("response choice had no message");
        }
        String content = "";
        if (message.has("content") && !message.get("content").isJsonNull()) {
            content = message.get("content").getAsString();
        }
        List<LlmToolCall> calls = new ArrayList<>();
        if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
            for (JsonElement el : message.getAsJsonArray("tool_calls")) {
                JsonObject tc = el.getAsJsonObject();
                String id = tc.has("id") ? tc.get("id").getAsString() : "";
                JsonObject fn = tc.getAsJsonObject("function");
                String fname = fn.get("name").getAsString();
                JsonObject args = new JsonObject();
                if (fn.has("arguments")) {
                    try {
                        JsonElement a = JsonParser.parseString(fn.get("arguments").getAsString());
                        if (a.isJsonObject()) {
                            args = a.getAsJsonObject();
                        }
                    } catch (Exception ignored) {
                    }
                }
                calls.add(new LlmToolCall(id, fname, args));
            }
        }
        return new LlmResponse(content, calls);
    }
}
