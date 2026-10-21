package com.infezione.client.fx;

import com.infezione.client.ClientInfections;
import com.infezione.config.InfezioneConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ViewportEvent;

/** The infected player's camera: jolts for twitches and convulsions, and a constant tremor in the agony. */
public final class ScreenShake {
    private static float strength;
    private static int left;
    private static int total;

    private ScreenShake() {
    }

    /** {@code power}: degrees of the strongest jolt; it fades out over {@code ticks}. */
    public static void add(float power, int ticks) {
        float now = total > 0 ? strength * left / (float) total : 0.0F;
        if (power >= now) {
            strength = power;
            left = ticks;
            total = ticks;
        }
    }

    public static void tick() {
        if (left > 0) {
            left--;
        }
    }

    public static void clear() {
        left = 0;
    }

    public static void apply(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (!InfezioneConfig.CLIENT.screenShake.get() || mc.player == null) {
            return;
        }
        float scale = mc.options.screenEffectScale().get().floatValue();
        float time = ClientInfections.clock() + (float) event.getPartialTick();
        float amount = 0.0F;
        if (left > 0) {
            float t = (left - (float) event.getPartialTick()) / total;
            amount = strength * Math.max(0.0F, t);
        }
        if (ClientInfections.SELF.stage == 4) {
            amount += 0.25F;
        }
        amount *= scale;
        if (amount <= 0.0F) {
            return;
        }
        event.setPitch(event.getPitch() + amount * Mth.sin(time * 3.7F) * 0.8F);
        event.setYaw(event.getYaw() + amount * Mth.sin(time * 2.9F + 1.3F) * 0.6F);
        event.setRoll(event.getRoll() + amount * Mth.sin(time * 4.3F + 0.7F));
    }
}
