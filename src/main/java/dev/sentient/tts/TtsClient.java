package dev.sentient.tts;

import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;

import dev.sentient.Sentient;
import dev.sentient.llm.HttpSupport;

/** Calls the Kokoro sidecar (or any endpoint speaking the same contract). Server-side. */
public final class TtsClient {

    private TtsClient() {
    }

    /** Returns WAV bytes for the text, or fails. */
    public static CompletableFuture<byte[]> speak(String text, String voice, double speed) {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("voice", voice);
        body.addProperty("speed", speed);
        body.addProperty("format", "wav");
        return HttpSupport.postJsonBytes(endpoint(), null, body, Sentient.config().timeoutSeconds);
    }

    private static String endpoint() {
        String base = Sentient.config().ttsEndpoint;
        String b = base == null ? "" : base.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b.endsWith("/tts") ? b : b + "/tts";
    }
}
