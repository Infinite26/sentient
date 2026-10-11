package dev.sentient.client;

import dev.sentient.Sentient;
import dev.sentient.net.OpenChatRequestPacket;
import dev.sentient.net.SentientNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Sentient.MODID, value = Dist.CLIENT)
public final class ClientTick {

    private ClientTick() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (ClientEvents.TALK.consumeClick()) {
            if (mc.screen != null || mc.player == null) {
                continue;
            }
            Entity target = mc.crosshairPickEntity;
            if (target instanceof LivingEntity living) {
                SentientNetwork.CHANNEL.sendToServer(new OpenChatRequestPacket(living.getId()));
            }
        }
    }
}
