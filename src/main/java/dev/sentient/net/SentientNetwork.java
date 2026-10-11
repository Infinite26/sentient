package dev.sentient.net;

import java.util.Optional;

import dev.sentient.Sentient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SentientNetwork {

    public static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Sentient.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int nextId = 0;

    private SentientNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextId++, OpenChatRequestPacket.class,
                OpenChatRequestPacket::encode, OpenChatRequestPacket::decode, OpenChatRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, ChatRequestPacket.class,
                ChatRequestPacket::encode, ChatRequestPacket::decode, ChatRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, ChatHistoryPacket.class,
                ChatHistoryPacket::encode, ChatHistoryPacket::decode, ChatHistoryPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, ChatResponsePacket.class,
                ChatResponsePacket::encode, ChatResponsePacket::decode, ChatResponsePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, SentientErrorPacket.class,
                SentientErrorPacket::encode, SentientErrorPacket::decode, SentientErrorPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, PlayVoicePacket.class,
                PlayVoicePacket::encode, PlayVoicePacket::decode, PlayVoicePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendTo(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void broadcastNear(ServerLevel level, Vec3 center, double radius, Object msg) {
        double r2 = radius * radius;
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(center) <= r2) {
                sendTo(sp, msg);
            }
        }
    }
}
