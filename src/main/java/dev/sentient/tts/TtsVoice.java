package dev.sentient.tts;

import dev.sentient.Sentient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Chooses a Kokoro voice for an entity: config override, else stable by UUID. */
public final class TtsVoice {

    /** A spread of English Kokoro voices (af_=US female, am_=US male, bf_/bm_=GB). */
    private static final String[] VOICES = {
            "af_heart", "af_bella", "af_nicole", "af_sarah", "af_sky",
            "am_michael", "am_adam", "am_onyx",
            "bf_emma", "bf_isabella", "bm_george", "bm_lewis"
    };

    private TtsVoice() {
    }

    public static String forEntity(LivingEntity entity) {
        String typeId = typeId(entity);
        String override = Sentient.config().ttsVoiceOverrides.get(typeId);
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        int h = Math.floorMod(entity.getUUID().hashCode(), VOICES.length);
        return VOICES[h];
    }

    public static String typeId(LivingEntity entity) {
        ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return rl == null ? "minecraft:unknown" : rl.toString();
    }
}
