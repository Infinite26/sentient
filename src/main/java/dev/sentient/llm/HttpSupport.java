package dev.sentient.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/** Shared HTTP + JSON plumbing for all providers. Server-side only. */
public final class HttpSupport {

    public static final Gson GSON = new Gson();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "sentient-llm-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private static HttpClient client;

    private HttpSupport() {
    }

    public static synchronized HttpClient client() {
        if (client == null) {
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .executor(POOL)
                    .build();
        }
        return client;
    }

    public static CompletableFuture<String> postJson(String url, Map<String, String> headers,
            JsonObject body, int timeoutSeconds) {
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
        if (headers != null) {
            headers.forEach(rb::header);
        }
        return client().sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                        throw new RuntimeException("HTTP " + resp.statusCode() + ": " + trim(resp.body(), 300));
                    }
                    return resp.body();
                });
    }

    /** POST JSON, return the raw response body (for binary endpoints such as TTS). */
    public static CompletableFuture<byte[]> postJsonBytes(String url, Map<String, String> headers,
            JsonObject body, int timeoutSeconds) {
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
        if (headers != null) {
            headers.forEach(rb::header);
        }
        return client().sendAsync(rb.build(), HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(resp -> {
                    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                        throw new RuntimeException("HTTP " + resp.statusCode() + ": "
                                + trim(new String(resp.body(), StandardCharsets.UTF_8), 300));
                    }
                    return resp.body();
                });
    }

    public static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
