package dev.sentient.net;

import java.util.function.Supplier;

import dev.sentient.chat.SentientChat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client sends a line of dialogue to an NPC. */
public class ChatRequestPacket {

    public final int entityId;
    public final String message;

    public ChatRequestPacket(int entityId, String message) {
        this.entityId = entityId;
        this.message = message;
    }

    public static void encode(ChatRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.message, 1024);
    }

    public static ChatRequestPacket decode(FriendlyByteBuf buf) {
        return new ChatRequestPacket(buf.readVarInt(), buf.readUtf(1024));
    }

    public static void handle(ChatRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sender = c.getSender();
            if (sender != null) {
                SentientChat.handlePlayerMessage(sender, msg.entityId, msg.message);
            }
        });
        c.setPacketHandled(true);
    }
}
