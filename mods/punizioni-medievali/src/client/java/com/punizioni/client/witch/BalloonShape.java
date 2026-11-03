package com.punizioni.client.witch;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The balloon's shape in the player model's pixels (y down, the neck at 0, the feet at 24): a round
 * body that swells from the chest in two seconds, the head on top, the arms sticking out of its
 * sides, the legs dangling under it. Popped, it whizzes round on a crazy path while it empties.
 */
public final class BalloonShape {
    private BalloonShape() {
    }

    /** Swelling up takes this long (ticks). */
    public static final float INFLATE = 40.0F;
    /** Full size: radius and where its bottom is (px). */
    public static final float RADIUS = 13.7F;
    public static final float BOTTOM = 17.0F;

    /** 0 (a body) to 1 (a balloon), with a wobble at the end. */
    public static float inflation(float age) {
        float x = Mth.clamp(age / INFLATE, 0.0F, 1.0F);
        if (x >= 1.0F) {
            return 1.0F;
        }
        float ease = 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x);
        return ease + 0.1F * Mth.sin(x * Mth.PI * 4.0F) * x * (1.0F - x) * 4.0F;
    }

    public static float radius(float k) {
        return Mth.lerp(k, 5.5F, RADIUS);
    }

    public static float centerY(float k) {
        return Mth.lerp(k, 6.0F, BOTTOM - RADIUS);
    }

    /** Where the neck sits on top of it (a little sunk in). */
    public static float neckY(float k) {
        return centerY(k) - radius(k) + Mth.lerp(k, 0.5F, 1.4F);
    }

    public static float legY(float k) {
        return Mth.lerp(k, 12.0F, 15.6F);
    }

    public static float legScale(float k) {
        return Mth.lerp(k, 1.0F, 0.7F);
    }

    public static float shoulderX(float k) {
        return Mth.lerp(k, 5.0F, 11.6F);
    }

    public static float shoulderY(float k) {
        return Mth.lerp(k, 2.0F, centerY(k) - 5.5F);
    }

    public static float armScale(float k) {
        return Mth.lerp(k, 1.0F, 0.72F);
    }

    // ------------------------------------------------------------------ popped

    /** The whizzing round: where it is (blocks from where it popped; y up), {@code t} ticks after the pop. */
    public static Vec3 zip(int seed, float t, double drop) {
        float s = Mth.clamp(t / 36.0F, 0.0F, 1.0F);
        double a = (seed & 0xFFFF) * 0.0011 + t * 0.62 + Math.sin(t * 0.83 + seed % 7) * 1.3;
        double r = 3.6 * (1.0 - (1.0 - s) * (1.0 - s));
        double up = 2.2 * Math.sin(Math.PI * Math.min(1.0, s * 1.4)) * (1.0 - s);
        // it ends on the ground under where it popped
        double y = up - drop * s * s;
        return new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
    }

    /** How much air is left (1 full, 0 a flat skin). */
    public static float air(float t) {
        float s = Mth.clamp(t / 36.0F, 0.0F, 1.0F);
        return 1.0F - s * s * (3.0F - 2.0F * s);
    }
}
