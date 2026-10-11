package dev.sentient.item;

import dev.sentient.Sentient;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class SentientItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Sentient.MODID);

    public static final RegistryObject<Item> WHISPER_STONE =
            ITEMS.register("whisper_stone", WhisperStoneItem::new);

    private SentientItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
