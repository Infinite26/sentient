package dev.sentient.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import dev.sentient.client.ClientPacketHandler;
import dev.sentient.llm.ChatMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server sends the stored conversation so the client can render the chat screen. */
public class ChatHistoryPacket {

    public final int entityId;
    public final String name;
    public final List<ChatMessage> messages;

    public ChatHistoryPacket(int entityId, String name, List<ChatMessage> messages) {
        this.entityId = entityId;
        this.name = name;
        this.messages = messages;
    }

    public static void encode(ChatHistoryPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.name == null ? "" : msg.name, 256);
        buf.writeVarInt(msg.messages.size());
        for (ChatMessage m : msg.messages) {
            buf.writeUtf(m.role, 32);
            buf.writeUtf(m.content, 4096);
        }
    }

    public static ChatHistoryPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        String name = buf.readUtf(256);
        int n = buf.readVarInt();
        List<ChatMessage> messages = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            messages.add(new ChatMessage(buf.readUtf(32), buf.readUtf(4096)));
        }
        return new ChatHistoryPacket(id, name, messages);
    }

    public static void handle(ChatHistoryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.onHistory(msg)));
        c.setPacketHandled(true);
    }
}
