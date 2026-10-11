package dev.sentient.llm;

import com.google.gson.JsonObject;

/** A tool the model may call. Parameters is a JSON-Schema object. */
public class LlmTool {

    public final String name;
    public final String description;
    public final JsonObject parameters;

    public LlmTool(String name, String description, JsonObject parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public static JsonObject objectSchema() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "object");
        o.add("properties", new JsonObject());
        return o;
    }
}
