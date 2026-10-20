package com.frusta.client.fx;

import com.frusta.config.FrustaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ViewportEvent;

/** The screen jolts when a crack goes off close by: a quick, decaying shake of the camera. */
public final class ScreenShake {
    private static float strength;
    private static int left;
    private static int total;

    private ScreenShake() {
    }

    /** {@code power}: degrees of the strongest jolt; it fades out over {@code ticks}. */
    public static void add(float power, int ticks) {
        if (power > strength * left / Math.max(1F, total)) {
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
        if (left <= 0 || !FrustaConfig.CLIENT.screenShake.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float scale = mc.options.screenEffectScale().get().floatValue();
        float t = (float) (left - event.getPartialTick()) / total;
        float amount = strength * scale * Math.max(0, t) * Math.max(0, t);
        float time = (mc.level != null ? mc.level.getGameTime() : 0) + (float) event.getPartialTick();
        event.setPitch(event.getPitch() + amount * Mth.sin(time * 3.7F) * 0.8F);
        event.setYaw(event.getYaw() + amount * Mth.sin(time * 2.9F + 1.3F) * 0.6F);
        event.setRoll(event.getRoll() + amount * Mth.sin(time * 4.3F + 0.7F));
    }
}
