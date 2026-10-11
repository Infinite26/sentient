package dev.sentient.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import dev.sentient.Sentient;
import dev.sentient.config.SentientConfig;
import dev.sentient.llm.ChatMessage;
import dev.sentient.llm.LlmResponse;
import dev.sentient.llm.LlmRouter;
import dev.sentient.memory.NpcMemory;
import dev.sentient.memory.SentientMemoryData;
import dev.sentient.net.ChatHistoryPacket;
import dev.sentient.net.ChatResponsePacket;
import dev.sentient.net.PlayVoicePacket;
import dev.sentient.net.SentientErrorPacket;
import dev.sentient.net.SentientNetwork;
import dev.sentient.tts.TtsClient;
import dev.sentient.tts.TtsVoice;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

public final class SentientChat {

    private static final Map<UUID, Long> LAST_REQUEST = new ConcurrentHashMap<>();

    private SentientChat() {
    }

    public static void beginConversation(ServerPlayer player, LivingEntity target) {
        if (target.isRemoved() || target.isDeadOrDying()) {
            sendError(player, "That creature is gone.");
            return;
        }
        if (tooFar(player, target)) {
            sendError(player, "You are too far away to talk.");
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        SentientMemoryData data = SentientMemoryData.get(server);
        NpcMemory mem = data.getOrCreate(target.getUUID(), target.getName().getString(), entityTypeId(target));
        SentientNetwork.sendTo(player, new ChatHistoryPacket(target.getId(), target.getName().getString(), mem.last(60)));
    }

    public static void openChatForEntity(ServerPlayer player, int entityId) {
        Entity e = player.serverLevel().getEntity(entityId);
        if (e instanceof LivingEntity le) {
            beginConversation(player, le);
        } else {
            sendError(player, "There is nothing there to talk to.");
        }
    }

    public static void handlePlayerMessage(ServerPlayer player, int entityId, String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > 512) {
            text = text.substring(0, 512);
        }
        Entity e = player.serverLevel().getEntity(entityId);
        if (!(e instanceof LivingEntity target) || target.isRemoved() || target.isDeadOrDying()) {
            sendError(player, "That creature is gone.");
            return;
        }
        if (tooFar(player, target)) {
            sendError(player, "You are too far away to talk.");
            return;
        }
        if (!checkCooldown(player)) {
            sendError(player, "Wait a moment before speaking again.");
            return;
        }
        query(player, player.getName().getString(), target, text,
                (npcName, reply) -> {
                    ChatResponsePacket resp = new ChatResponsePacket(target.getId(), npcName, reply);
                    if (Sentient.config().broadcastToNearby) {
                        SentientNetwork.broadcastNear(player.serverLevel(), target.position(),
                                Sentient.config().broadcastRadius, resp);
                    } else {
                        SentientNetwork.sendTo(player, resp);
                    }
                    speakIfEnabled(player, target, reply, player.getServer());
                },
                reason -> sendError(player, "The creature stares blankly. (" + reason + ")"));
    }

    public static void askFromCommand(CommandSourceStack source, LivingEntity target, String text) {
        ServerPlayer speaker = source.getEntity() instanceof ServerPlayer sp ? sp : null;
        query(speaker, source.getTextName(), target, text,
                (npcName, reply) -> source.sendSuccess(() -> Component.literal(npcName + ": " + reply), false),
                reason -> source.sendFailure(Component.literal("LLM error: " + reason)));
    }

    public static void testEndpoint(CommandSourceStack source) {
        LlmRouter.test("Say the single word: ready").whenComplete((reply, err) -> {
            if (source.getServer() == null) {
                return;
            }
            source.getServer().execute(() -> {
                if (err != null) {
                    source.sendFailure(Component.literal("LLM error: " + shortReason(err)));
                } else {
                    source.sendSuccess(() -> Component.literal("LLM OK: " + sanitize(reply)), false);
                }
            });
        });
    }

    private static void query(ServerPlayer speaker, String speakerName, LivingEntity target, String userText,
            BiConsumer<String, String> onSuccess, Consumer<String> onError) {
        if (target == null || target.isRemoved() || target.isDeadOrDying()) {
            onError.accept("the creature is gone");
            return;
        }
        MinecraftServer server = target.getServer();
        if (server == null) {
            onError.accept("no server");
            return;
        }
        SentientMemoryData data = SentientMemoryData.get(server);
        String typeId = entityTypeId(target);
        NpcMemory mem = data.getOrCreate(target.getUUID(), target.getName().getString(), typeId);
        String who = (speakerName == null || speakerName.isBlank()) ? "Someone" : speakerName;
        mem.add(ChatMessage.user(who + " says: " + userText), Sentient.config().maxMemoryMessages);
        data.setDirty();

        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(ChatMessage.system(systemPrompt(target, who, typeId)));
        prompt.addAll(mem.last(Math.max(2, Sentient.config().contextMessages)));

        final String npcName = target.getName().getString();
        Sentient.LOGGER.info("[chat] {} -> {}: {}", who, npcName, userText);

        LlmRouter.chat(prompt, List.of()).thenApply(LlmResponse::requireText).whenComplete((reply, err) -> server.execute(() -> {
            if (target.isRemoved() || target.isDeadOrDying()) {
                onError.accept("the creature is gone");
                return;
            }
            if (err != null) {
                Sentient.LOGGER.warn("LLM call for {} failed: {}", npcName, err.getMessage());
                onError.accept(shortReason(err));
                return;
            }
            String clean = sanitize(reply);
            if (clean.isEmpty()) {
                onError.accept("empty reply");
                return;
            }
            mem.add(ChatMessage.assistant(clean), Sentient.config().maxMemoryMessages);
            data.setDirty();
            if (Sentient.config().logConversations) {
                Sentient.LOGGER.info("[chat] {}: {}", npcName, clean);
            }
            onSuccess.accept(npcName, clean);
        }));
    }

    private static String systemPrompt(LivingEntity target, String playerName, String typeId) {
        SentientConfig cfg = Sentient.config();
        String biome = "unknown";
        String time = "daytime";
        String dim = "overworld";
        if (target.level() instanceof ServerLevel sl) {
            dim = sl.dimension().location().getPath();
            time = sl.isDay() ? "daytime" : "night";
            biome = sl.getBiome(target.blockPosition()).unwrapKey()
                    .map(k -> k.location().getPath()).orElse("unknown");
        }
        return cfg.systemPrompt
                .replace("{persona}", cfg.personaFor(typeId))
                .replace("{name}", target.getName().getString())
                .replace("{player}", playerName == null ? "a traveller" : playerName)
                .replace("{biome}", biome)
                .replace("{time}", time)
                .replace("{dimension}", dim);
    }

    private static boolean tooFar(ServerPlayer player, LivingEntity target) {
        double range = Sentient.config().interactionRange + 2.0;
        return player.distanceToSqr(target) > range * range;
    }

    private static boolean checkCooldown(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return true;
        }
        long now = server.getTickCount();
        Long last = LAST_REQUEST.get(player.getUUID());
        if (last != null && now - last < Sentient.config().cooldownTicks) {
            return false;
        }
        LAST_REQUEST.put(player.getUUID(), now);
        return true;
    }

    private static String entityTypeId(LivingEntity target) {
        ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        return rl == null ? "minecraft:unknown" : rl.toString();
    }

    private static void speakIfEnabled(ServerPlayer player, LivingEntity target, String text,
            MinecraftServer server) {
        if (!Sentient.config().ttsEnabled || text == null || text.isBlank() || server == null) {
            return;
        }
        String voice = TtsVoice.forEntity(target);
        TtsClient.speak(text, voice, Sentient.config().ttsSpeed).whenComplete((wav, err) -> {
            if (err != null) {
                Sentient.LOGGER.warn("TTS failed for {}: {}", target.getName().getString(), err.getMessage());
                return;
            }
            if (wav == null || wav.length == 0) {
                return;
            }
            server.execute(() -> {
                if (target.isRemoved()) {
                    return;
                }
                PlayVoicePacket pkt = new PlayVoicePacket(target.getId(), wav);
                if (Sentient.config().broadcastToNearby) {
                    SentientNetwork.broadcastNear(player.serverLevel(), target.position(),
                            Sentient.config().broadcastRadius, pkt);
                } else {
                    SentientNetwork.sendTo(player, pkt);
                }
            });
        });
    }

    private static void sendError(ServerPlayer player, String message) {
        SentientNetwork.sendTo(player, new SentientErrorPacket(message));
    }

    public static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        String out = s.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (out.length() >= 2 && out.startsWith("\"") && out.endsWith("\"")) {
            out = out.substring(1, out.length() - 1).trim();
        }
        if (out.length() > 500) {
            out = out.substring(0, 500);
        }
        return out;
    }

    private static String shortReason(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        String m = c.getClass().getSimpleName();
        if (c.getMessage() != null && !c.getMessage().isBlank()) {
            m += ": " + c.getMessage();
        }
        m = m.replaceAll("\\s+", " ").trim();
        return m.length() > 140 ? m.substring(0, 140) + "..." : m;
    }
}
