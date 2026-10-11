package dev.sentient.net;

import java.util.function.Supplier;

import dev.sentient.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server sends an NPC's spoken reply. */
public class ChatResponsePacket {

    public final int entityId;
    public final String name;
    public final String message;

    public ChatResponsePacket(int entityId, String name, String message) {
        this.entityId = entityId;
        this.name = name;
        this.message = message;
    }

    public static void encode(ChatResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.name == null ? "" : msg.name, 256);
        buf.writeUtf(msg.message == null ? "" : msg.message, 4096);
    }

    public static ChatResponsePacket decode(FriendlyByteBuf buf) {
        return new ChatResponsePacket(buf.readVarInt(), buf.readUtf(256), buf.readUtf(4096));
    }

    public static void handle(ChatResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.onResponse(msg)));
        c.setPacketHandled(true);
    }
}
