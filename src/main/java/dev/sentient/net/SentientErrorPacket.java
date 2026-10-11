package dev.sentient.net;

import java.util.function.Supplier;

import dev.sentient.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server tells the client something went wrong (endpoint down, timeout, ...). */
public class SentientErrorPacket {

    public final String message;

    public SentientErrorPacket(String message) {
        this.message = message;
    }

    public static void encode(SentientErrorPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.message == null ? "" : msg.message, 512);
    }

    public static SentientErrorPacket decode(FriendlyByteBuf buf) {
        return new SentientErrorPacket(buf.readUtf(512));
    }

    public static void handle(SentientErrorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.onError(msg)));
        c.setPacketHandled(true);
    }
}
