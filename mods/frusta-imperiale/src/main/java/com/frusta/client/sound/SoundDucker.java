package com.frusta.client.sound;

import com.frusta.FrustaMod;
import com.frusta.config.FrustaConfig;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * Makes a crack tower over every other sound: for a moment all the other sounds that are playing
 * (music, rain, mobs, blocks, other players) are turned down, then they come back smoothly. It does
 * this on the playing channels themselves; the player's volume settings are never touched.
 */
public final class SoundDucker {
    private static Field soundEngineField;
    private static Field channelsField;
    private static boolean broken;

    /** How deep the current duck goes (0 none, 1 down to the configured volume). */
    private static double depth;
    private static int hold;
    private static int release;
    private static int releaseTotal = 1;
    private static boolean applied;

    private SoundDucker() {
    }

    /** Ducks the other sounds by {@code strength} (0 to 1) for {@code holdTicks}, then lets them back up. */
    public static void duck(double strength, int holdTicks, int releaseTicks) {
        if (!FrustaConfig.CLIENT.duckOtherSounds.get()) {
            return;
        }
        double now = current();
        if (strength >= now) {
            depth = Mth.clamp(strength, 0, 1);
            hold = holdTicks;
            release = releaseTicks;
            releaseTotal = Math.max(1, releaseTicks);
        }
    }

    /** How much the other sounds are turned down right now (0 none, 1 fully). */
    private static double current() {
        if (hold > 0) {
            return depth;
        }
        if (release > 0) {
            double k = (double) release / releaseTotal;
            return depth * k * k;
        }
        return 0;
    }

    public static void tick(Minecraft mc) {
        double d = current();
        if (hold > 0) {
            hold--;
        } else if (release > 0) {
            release--;
        }
        if (d <= 0 && !applied) {
            return;
        }
        double floor = FrustaConfig.CLIENT.duckVolume.get();
        apply(mc, (float) (1 - d * (1 - floor)));
        applied = d > 0;
    }

    /** Sets every playing sound that is not ours to its normal volume times {@code factor}. */
    @SuppressWarnings("unchecked")
    private static void apply(Minecraft mc, float factor) {
        if (broken) {
            return;
        }
        try {
            if (soundEngineField == null) {
                soundEngineField = ObfuscationReflectionHelper.findField(SoundManager.class, "f_120349_");
                channelsField = ObfuscationReflectionHelper.findField(SoundEngine.class, "f_120226_");
            }
            SoundEngine engine = (SoundEngine) soundEngineField.get(mc.getSoundManager());
            Map<SoundInstance, ChannelAccess.ChannelHandle> channels =
                    (Map<SoundInstance, ChannelAccess.ChannelHandle>) channelsField.get(engine);
            for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : channels.entrySet()) {
                SoundInstance sound = entry.getKey();
                if (FrustaMod.MODID.equals(sound.getLocation().getNamespace()) || sound.getSound() == null) {
                    continue;
                }
                SoundSource source = sound.getSource();
                float category = source == SoundSource.MASTER ? 1.0F : mc.options.getSoundSourceVolume(source);
                float volume = Mth.clamp(sound.getVolume() * category, 0.0F, 1.0F) * factor;
                if (volume > 0.0F) {
                    entry.getValue().execute(channel -> channel.setVolume(volume));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Without its fields the crack is still loud; it just cannot quiet the rest.
            broken = true;
            FrustaMod.LOGGER.warn("Cannot turn the other sounds down during cracks", e);
        }
    }
}
