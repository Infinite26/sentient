package dev.sentient.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Native Ollama /api/chat. */
public class OllamaProvider implements LlmProvider {

    private final String name;
    private final String baseUrl;
    private final String defaultModel;
    private final boolean tools;
    private final int timeout;

    public OllamaProvider(String name, String baseUrl, String defaultModel, boolean supportsTools,
            int timeoutSeconds) {
        this.name = name;
        this.baseUrl = baseUrl;
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
        body.addProperty("stream", false);

        JsonArray msgs = new JsonArray();
        for (ChatMessage m : req.messages) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role);
            o.addProperty("content", m.content);
            msgs.add(o);
        }
        body.add("messages", msgs);

        JsonObject options = new JsonObject();
        options.addProperty("temperature", req.temperature);
        options.addProperty("num_predict", req.maxTokens);
        body.add("options", options);

        if (req.hasTools() && tools) {
            body.add("tools", OpenAiProvider.openAiTools(req.tools));
        }

        return HttpSupport.postJson(OpenAiProvider.endpoint(baseUrl, "/api/chat"), null, body, timeout)
                .thenApply(OllamaProvider::parse);
    }

    public static LlmResponse parse(String body) {
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonObject()) {
            throw new RuntimeException("Ollama response was not JSON: " + HttpSupport.trim(body, 200));
        }
        JsonObject obj = root.getAsJsonObject();
        if (obj.has("error")) {
            throw new RuntimeException("Ollama error: " + HttpSupport.trim(obj.get("error").toString(), 300));
        }
        JsonObject message = obj.getAsJsonObject("message");
        if (message == null) {
            throw new RuntimeException("Ollama response had no message: " + HttpSupport.trim(body, 200));
        }
        String content = message.has("content") && !message.get("content").isJsonNull()
                ? message.get("content").getAsString() : "";
        List<LlmToolCall> calls = new ArrayList<>();
        if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
            for (JsonElement el : message.getAsJsonArray("tool_calls")) {
                JsonObject tc = el.getAsJsonObject();
                JsonObject fn = tc.getAsJsonObject("function");
                JsonObject args = fn.has("arguments") && fn.get("arguments").isJsonObject()
                        ? fn.getAsJsonObject("arguments") : new JsonObject();
                calls.add(new LlmToolCall("", fn.get("name").getAsString(), args));
            }
        }
        return new LlmResponse(content, calls);
    }
}
