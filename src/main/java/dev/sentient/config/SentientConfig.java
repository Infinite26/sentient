package dev.sentient.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.sentient.Sentient;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * JSON config at config/sentient.json. Reloadable with /sentient reload.
 */
public class SentientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** OpenAI-compatible chat-completions endpoint. */
    public String endpoint = "http://127.0.0.1:11434/v1/chat/completions";
    /** Bearer token; leave blank for local servers that don't need one. */
    public String apiKey = "";
    public String model = "llama3.1";
    public double temperature = 0.8;
    public int maxTokens = 256;
    public int timeoutSeconds = 30;
    /** How many prior messages to send as context. */
    public int contextMessages = 12;
    /** Max stored messages per NPC (older ones are dropped). */
    public int maxMemoryMessages = 40;
    /** Minimum ticks between requests for the same player. */
    public int cooldownTicks = 40;
    /** How close a player must be to talk to an NPC (blocks). */
    public double interactionRange = 8.0;
    /** Broadcast NPC replies to nearby players as well. */
    public boolean broadcastToNearby = true;
    public double broadcastRadius = 24.0;
    /** Send replies to the server log. */
    public boolean logConversations = true;

    /**
     * The system prompt. Placeholders: {persona} {name} {player} {biome} {time} {dimension}
     */
    public String systemPrompt = "You are {name}, a living creature in a Minecraft world. "
            + "You are {persona}. You are speaking with the player {player}. "
            + "It is {time} in the {biome} biome ({dimension}). "
            + "Stay fully in character. Keep replies short (1-3 sentences), vivid and specific. "
            + "Never mention that you are an AI, a model, or a game. Never use markdown or emojis. "
            + "You remember previous conversations with this player.";

    /** Per entity-type persona, keyed by entity type id. */
    public Map<String, String> personas = new LinkedHashMap<>();

    public String personaFor(String entityTypeId) {
        return personas.getOrDefault(entityTypeId, "a mysterious creature of the " + entityTypeId);
    }

    // ---- LLM providers (Phase 1) -------------------------------------------

    /**
     * Named LLM providers, tried in order with fallback. If empty, one
     * OpenAI-compatible provider is derived from endpoint/model/apiKey.
     */
    public List<Provider> providers = new ArrayList<>();

    /** One provider entry. type: openai | ollama | anthropic | gemini */
    public static class Provider {
        public String name = "local";
        public String type = "openai";
        public String baseUrl = "http://127.0.0.1:11434/v1";
        public String apiKey = "";
        public String model = "llama3.1";
        public boolean supportsTools = true;

        public Provider() {
        }
    }

    public List<Provider> effectiveProviders() {
        if (providers != null && !providers.isEmpty()) {
            return providers;
        }
        return List.of(defaultProvider());
    }

    private Provider defaultProvider() {
        Provider p = new Provider();
        p.name = "default";
        p.type = "openai";
        p.baseUrl = stripChatPath(endpoint);
        p.apiKey = apiKey;
        p.model = model;
        p.supportsTools = true;
        return p;
    }

    private static String stripChatPath(String url) {
        if (url == null) {
            return "http://127.0.0.1:11434/v1";
        }
        String u = url.trim();
        String suffix = "/chat/completions";
        return u.endsWith(suffix) ? u.substring(0, u.length() - suffix.length()) : u;
    }

    // ---- Text to speech (Phase 1) ------------------------------------------

    public boolean ttsEnabled = true;
    /** Local Kokoro sidecar, or any endpoint speaking the same HTTP contract. */
    public String ttsEndpoint = "http://127.0.0.1:8770";
    public double ttsVolume = 1.0;
    public double ttsSpeed = 1.0;
    /** Per entity-type voice override, e.g. {"minecraft:villager": "af_heart"}. */
    public Map<String, String> ttsVoiceOverrides = new LinkedHashMap<>();

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("sentient.json");
    }

    public static void load() {
        Path p = path();
        SentientConfig cfg = new SentientConfig();
        if (Files.exists(p)) {
            try {
                String json = Files.readString(p, StandardCharsets.UTF_8);
                SentientConfig read = GSON.fromJson(json, SentientConfig.class);
                if (read != null) {
                    cfg = read;
                }
            } catch (Exception e) {
                Sentient.LOGGER.error("Failed to read sentient.json, using defaults", e);
            }
        }
        if (cfg.personas == null) {
            cfg.personas = new LinkedHashMap<>();
        }
        if (cfg.providers == null) {
            cfg.providers = new ArrayList<>();
        }
        if (cfg.ttsVoiceOverrides == null) {
            cfg.ttsVoiceOverrides = new LinkedHashMap<>();
        }
        boolean changed = cfg.fillDefaultPersonas();
        if (cfg.providers.isEmpty()) {
            cfg.providers.add(cfg.defaultProvider());
            changed = true;
        }
        Sentient.setConfig(cfg);
        if (!Files.exists(p) || changed) {
            cfg.save();
        }
        Sentient.LOGGER.info("Sentient config loaded (providers={}, model={})",
                cfg.effectiveProviders().size(), cfg.model);
    }

    public void save() {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Sentient.LOGGER.error("Failed to write sentient.json", e);
        }
    }

    private boolean fillDefaultPersonas() {
        return putIfAbsent("minecraft:villager", "a pragmatic village merchant who gossips and worries about the harvest and the local pillagers")
                | putIfAbsent("minecraft:iron_golem", "a solemn iron guardian who speaks slowly, protects the village, and distrusts the night")
                | putIfAbsent("minecraft:wolf", "a loyal dog who is delighted to see the player and thinks about sticks, bones and belly rubs")
                | putIfAbsent("minecraft:cat", "a haughty cat who deigns to speak with you and mostly wants to be left in a sunbeam")
                | putIfAbsent("minecraft:horse", "a proud, skittish horse who judges your riding")
                | putIfAbsent("minecraft:zombie", "a decayed, hungry villager, half-mad, torn between hunger and dim memories of being alive")
                | putIfAbsent("minecraft:creeper", "a hissing, shy creature that only wants to get close to you, and cannot understand why you always run")
                | putIfAbsent("minecraft:enderman", "an eerie, fragmented being of the End who teleports mid-sentence and speaks in riddles")
                | putIfAbsent("minecraft:allay", "a cheerful little spirit that sings, collects things, and adores being useful")
                | putIfAbsent("minecraft:piglin", "a greedy nether trader obsessed with gold, wary of outsiders")
                | putIfAbsent("minecraft:ender_dragon", "an ancient, mournful empress of the End, arrogant and vast")
                | putIfAbsent("minecraft:wandering_trader", "a well-travelled merchant with tall tales and terrible prices");
    }

    private boolean putIfAbsent(String key, String value) {
        if (!personas.containsKey(key)) {
            personas.put(key, value);
            return true;
        }
        return false;
    }
}
