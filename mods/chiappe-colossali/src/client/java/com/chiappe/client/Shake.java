package com.chiappe.client;

import com.chiappe.Butt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Big farts shake the screen of whoever is close (and of whoever let it go), less the farther they are. */
public final class Shake {
    private Shake() {
    }

    private static float strength;
    private static long startedAt;
    private static int length = 1;

    static void fart(Minecraft mc, Player who, int level, double radius) {
        if (mc.player == null || level < 8) {
            return;
        }
        float k = (float) level / Butt.MAX_LEVEL;
        float amount;
        if (who == mc.player) {
            amount = 2.0F * k * k;
        } else {
            double near = 1.0 - mc.player.distanceTo(who) / (radius * 3.0);
            amount = near <= 0.0 ? 0.0F : (float) (4.0F * k * k * near);
        }
        if (amount > strength * remaining(mc, 0.0F)) {
            strength = amount;
            length = 10 + level;
            startedAt = mc.level.getGameTime();
        }
    }

    static void reset() {
        strength = 0.0F;
    }

    private static float remaining(Minecraft mc, float partial) {
        if (mc.level == null || strength <= 0.0F) {
            return 0.0F;
        }
        float t = (mc.level.getGameTime() - startedAt + partial) / length;
        return t >= 1.0F ? 0.0F : (1.0F - t) * (1.0F - t);
    }

    /** Called where vanilla tilts the view when you get hurt. */
    public static void apply(PoseStack poseStack) {
        Minecraft mc = Minecraft.getInstance();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float left = remaining(mc, partial);
        if (left <= 0.0F) {
            return;
        }
        float amount = strength * left * mc.options.screenEffectScale().get().floatValue();
        float t = (mc.level.getGameTime() - startedAt + partial) * 2.6F;
        poseStack.rotateDegrees(Axis.ZP, amount * (Mth.sin(t * 2.1F) + 0.5F * Mth.sin(t * 5.3F + 1.0F)));
        poseStack.rotateDegrees(Axis.XP, amount * 0.6F * (Mth.sin(t * 2.9F + 2.0F) + 0.4F * Mth.sin(t * 6.1F)));
    }
}
