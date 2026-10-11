package dev.sentient.net;

import java.util.function.Supplier;

import dev.sentient.chat.SentientChat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client asks to open the chat screen for the entity it is currently looking at. */
public class OpenChatRequestPacket {

    public final int entityId;

    public OpenChatRequestPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(OpenChatRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
    }

    public static OpenChatRequestPacket decode(FriendlyByteBuf buf) {
        return new OpenChatRequestPacket(buf.readVarInt());
    }

    public static void handle(OpenChatRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sender = c.getSender();
            if (sender != null) {
                SentientChat.openChatForEntity(sender, msg.entityId);
            }
        });
        c.setPacketHandled(true);
    }
}
