package dev.sentient.event;

import dev.sentient.Sentient;
import dev.sentient.command.SentientCommand;
import dev.sentient.config.SentientConfig;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Sentient.MODID)
public final class SentientEvents {

    private SentientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SentientCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        Sentient.config().save();
    }
}
