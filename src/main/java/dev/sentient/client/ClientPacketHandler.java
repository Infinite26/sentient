package dev.sentient.client;

import dev.sentient.client.audio.VoicePlayer;
import dev.sentient.net.ChatHistoryPacket;
import dev.sentient.net.ChatResponsePacket;
import dev.sentient.net.PlayVoicePacket;
import dev.sentient.net.SentientErrorPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ClientPacketHandler {

    private static NpcChatScreen active;

    private ClientPacketHandler() {
    }

    public static void onHistory(ChatHistoryPacket msg) {
        NpcChatScreen screen = new NpcChatScreen(msg.entityId, msg.name, msg.messages);
        active = screen;
        Minecraft.getInstance().setScreen(screen);
    }

    public static void onResponse(ChatResponsePacket msg) {
        if (active != null && active.entityId() == msg.entityId) {
            active.appendNpc(msg.message);
        } else {
            chat("<" + msg.name + "> " + msg.message);
        }
    }

    public static void onError(SentientErrorPacket msg) {
        if (active != null) {
            active.appendError(msg.message);
        } else {
            chat("[Sentient] " + msg.message);
        }
    }

    public static void onVoice(PlayVoicePacket msg) {
        VoicePlayer.play(msg.entityId, msg.wav);
    }

    public static void screenClosed(NpcChatScreen screen) {
        if (active == screen) {
            active = null;
        }
    }

    private static void chat(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.getChat() != null) {
            mc.gui.getChat().addMessage(Component.literal(text));
        }
    }
}
