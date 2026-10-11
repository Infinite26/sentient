package dev.sentient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.sentient.config.SentientConfig;
import dev.sentient.item.SentientItems;
import dev.sentient.llm.LlmRouter;
import dev.sentient.net.SentientNetwork;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;

/**
 * Sentient - LLM-powered NPCs.
 *
 * All language-model traffic is server-side only; clients never see the API key.
 */
@Mod(Sentient.MODID)
public class Sentient {

    public static final String MODID = "sentient";
    public static final Logger LOGGER = LoggerFactory.getLogger("Sentient");

    private static SentientConfig config = new SentientConfig();

    public Sentient(IEventBus modBus, ModContainer container) {
        SentientConfig.load();
        LlmRouter.reload();
        SentientNetwork.register();
        SentientItems.register(modBus);
        modBus.addListener(this::addCreative);
    }

    public static SentientConfig config() {
        return config;
    }

    public static void setConfig(SentientConfig cfg) {
        config = cfg;
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(SentientItems.WHISPER_STONE.get());
        }
    }
}
