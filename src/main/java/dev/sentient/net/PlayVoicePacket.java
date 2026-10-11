package dev.sentient.net;

import java.util.function.Supplier;

import dev.sentient.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server streams synthesised speech (WAV) for an entity; the client plays it positionally. */
public class PlayVoicePacket {

    public final int entityId;
    public final byte[] wav;

    public PlayVoicePacket(int entityId, byte[] wav) {
        this.entityId = entityId;
        this.wav = wav;
    }

    public static void encode(PlayVoicePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeByteArray(msg.wav);
    }

    public static PlayVoicePacket decode(FriendlyByteBuf buf) {
        return new PlayVoicePacket(buf.readVarInt(), buf.readByteArray());
    }

    public static void handle(PlayVoicePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.onVoice(msg)));
        c.setPacketHandled(true);
    }
}
