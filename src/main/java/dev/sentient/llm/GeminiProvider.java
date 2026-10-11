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

/** Google Gemini generateContent API. */
public class GeminiProvider implements LlmProvider {

    private final String name;
    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final boolean tools;
    private final int timeout;

    public GeminiProvider(String name, String baseUrl, String apiKey, String defaultModel,
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
        String model = req.model != null && !req.model.isBlank() ? req.model : defaultModel;
        JsonObject body = new JsonObject();

        StringBuilder system = new StringBuilder();
        JsonArray contents = new JsonArray();
        for (ChatMessage m : req.messages) {
            if (ChatMessage.SYSTEM.equals(m.role)) {
                if (system.length() > 0) {
                    system.append("\n\n");
                }
                system.append(m.content);
                continue;
            }
            JsonObject part = new JsonObject();
            part.addProperty("text", m.content);
            JsonArray parts = new JsonArray();
            parts.add(part);
            JsonObject c = new JsonObject();
            c.addProperty("role", ChatMessage.ASSISTANT.equals(m.role) ? "model" : "user");
            c.add("parts", parts);
            contents.add(c);
        }
        if (system.length() > 0) {
            JsonObject part = new JsonObject();
            part.addProperty("text", system.toString());
            JsonArray parts = new JsonArray();
            parts.add(part);
            JsonObject si = new JsonObject();
            si.add("parts", parts);
            body.add("systemInstruction", si);
        }
        body.add("contents", contents);

        JsonObject gen = new JsonObject();
        gen.addProperty("temperature", req.temperature);
        gen.addProperty("maxOutputTokens", req.maxTokens);
        body.add("generationConfig", gen);

        if (req.hasTools() && tools) {
            JsonArray decls = new JsonArray();
            for (LlmTool t : req.tools) {
                JsonObject o = new JsonObject();
                o.addProperty("name", t.name);
                o.addProperty("description", t.description);
                o.add("parameters", t.parameters);
                decls.add(o);
            }
            JsonArray toolsArr = new JsonArray();
            JsonObject toolObj = new JsonObject();
            toolObj.add("functionDeclarations", decls);
            toolsArr.add(toolObj);
            body.add("tools", toolsArr);
        }

        String url = OpenAiProvider.endpoint(baseUrl, "/v1beta/models/" + model + ":generateContent");
        Map<String, String> headers = new LinkedHashMap<>();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("x-goog-api-key", apiKey);
        }
        return HttpSupport.postJson(url, headers, body, timeout).thenApply(GeminiProvider::parse);
    }

    public static LlmResponse parse(String body) {
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonObject()) {
            throw new RuntimeException("Gemini response was not JSON: " + HttpSupport.trim(body, 200));
        }
        JsonObject obj = root.getAsJsonObject();
        if (obj.has("error")) {
            throw new RuntimeException("Gemini error: " + HttpSupport.trim(obj.get("error").toString(), 300));
        }
        JsonArray candidates = obj.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("Gemini response had no candidates: " + HttpSupport.trim(body, 200));
        }
        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        if (content == null || !content.has("parts")) {
            throw new RuntimeException("Gemini candidate had no parts: " + HttpSupport.trim(body, 200));
        }
        StringBuilder text = new StringBuilder();
        List<LlmToolCall> calls = new ArrayList<>();
        for (JsonElement el : content.getAsJsonArray("parts")) {
            JsonObject part = el.getAsJsonObject();
            if (part.has("text")) {
                text.append(part.get("text").getAsString());
            } else if (part.has("functionCall")) {
                JsonObject fc = part.getAsJsonObject("functionCall");
                String fname = fc.get("name").getAsString();
                JsonObject args = fc.has("args") && fc.get("args").isJsonObject()
                        ? fc.getAsJsonObject("args") : new JsonObject();
                calls.add(new LlmToolCall("", fname, args));
            }
        }
        return new LlmResponse(text.toString(), calls);
    }
}
