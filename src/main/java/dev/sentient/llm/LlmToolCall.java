package dev.sentient.llm;

import com.google.gson.JsonObject;

/** A tool invocation requested by the model. */
public class LlmToolCall {

    public final String id;
    public final String name;
    public final JsonObject arguments;

    public LlmToolCall(String id, String name, JsonObject arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments == null ? new JsonObject() : arguments;
    }

    public String argumentAsString(String key, String fallback) {
        if (arguments.has(key) && !arguments.get(key).isJsonNull()) {
            try {
                return arguments.get(key).getAsString();
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    public int argumentAsInt(String key, int fallback) {
        if (arguments.has(key) && !arguments.get(key).isJsonNull()) {
            try {
                return arguments.get(key).getAsInt();
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }
}
