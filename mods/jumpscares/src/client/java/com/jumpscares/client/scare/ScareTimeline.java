package com.jumpscares.client.scare;

import com.jumpscares.scare.ScareKind;
import net.minecraft.util.Mth;

/**
 * The choreography of the five scares as a function of time (ticks since the start). Pure maths:
 * the victim's camera-space pass, the witnesses' world-space pass and the overlays all read it.
 */
public final class ScareTimeline {
    /** Distance of the face from the camera while it screams at you (blocks, at 70 degrees FOV). */
    private static final float[] DISTANCE = {0.42F, 0.37F, 0.50F, 0.40F, 0.46F};
    /** Model height (units, y down) of the middle of the face, put in the middle of the screen. */
    private static final float[] CENTER = {0.0F, -2.0F, 2.0F, -3.0F, -2.0F};
    /** Model height of the mouth: the last lunge goes into it. */
    private static final float[] MOUTH = {10.0F, 12.0F, 14.0F, 9.0F, 11.0F};

    private ScareTimeline() {
    }

    public static float center(ScareKind kind) {
        return CENTER[kind.ordinal()];
    }

    /** Deterministic noise in [-1, 1] for (seed, i). */
    static float hash(long seed, int i) {
        long z = seed + 0x9E3779B97F4A7C15L * (i + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (float) ((z >>> 11) * 0x1.0p-53 * 2.0 - 1.0);
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float easeIn(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t;
    }

    private static float easeOut(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    /** Fills {@code p} for {@code t} ticks into a scare; p.visible is false when the monster is not drawn. */
    public static void compute(ScareKind kind, long seed, float t, ScarePose p) {
        p.clear();
        int impact = kind.impactTick;
        int lunge = kind.lungeTick();
        int black = kind.blackTick();
        float dist = DISTANCE[kind.ordinal()];
        if (t < impact) {
            entrance(kind, seed, t, dist, p);
            p.vignette = 0.35F * t / impact;
            return;
        }
        if (t < lunge) {
            float k = t - impact;
            p.z = -dist + dist * 0.28F * (float) Math.exp(-k / 1.4F) * Mth.cos(k * 1.5F) - 0.02F * Mth.sin(k * 0.7F);
            float a = 0.008F + 0.028F * (float) Math.exp(-k / 4.0F);
            int frame = (int) (t * 3.0F);
            p.x = a * hash(seed, frame * 4);
            p.y = a * hash(seed, frame * 4 + 1);
            p.roll = a * 110.0F * hash(seed, frame * 4 + 2);
            p.yaw = a * 70.0F * hash(seed, frame * 4 + 3);
            p.shake = 0.45F + 0.55F * (float) Math.exp(-k / 3.0F);
            p.flash = Math.max(0.0F, 1.0F - k / 5.0F);
            p.noise = 0.3F * (float) Math.exp(-k / 2.5F);
            p.fov = 1.0F - 0.2F * (float) Math.exp(-k / 2.5F);
            p.vignette = 0.75F + 0.15F * Mth.sin(k * 0.9F);
            p.reach = 1.0F;
            p.jaw = 1.0F;
            inFace(kind, seed, t, k, p);
            return;
        }
        if (t < black) {
            float u = (t - lunge) / ScareKind.LUNGE;
            p.z = Mth.lerp(easeIn(u), -dist, -0.03F);
            p.y = Mth.lerp(u, 0.0F, (MOUTH[kind.ordinal()] - CENTER[kind.ordinal()]) * 0.25F / 16.0F);
            p.jaw = 1.0F;
            p.reach = 1.0F;
            p.shake = 1.0F;
            p.fov = 0.85F;
            p.vignette = 0.9F;
            p.black = smooth(0.35F, 1.0F, u);
            if (kind == ScareKind.CORPSE) {
                p.roll = 20.0F * hash(seed, 999);
            }
            return;
        }
        p.visible = false;
        float after = t - black;
        p.black = after < 4.0F ? 1.0F : Math.max(0.0F, 1.0F - (after - 4.0F) / (ScareKind.BLACK - 4.0F));
        p.vignette = p.black * 0.9F;
    }

    private static void entrance(ScareKind kind, long seed, float t, float dist, ScarePose p) {
        int impact = kind.impactTick;
        p.shake = 0.12F;
        switch (kind) {
            case CLOWN -> {
                // springs up from below like a jack-in-the-box, then comes at you
                float spring = -1.25F * (float) Math.exp(-0.9F * t) * Mth.cos(1.15F * t);
                if (t < 3.2F) {
                    p.y = spring;
                    p.z = -1.9F;
                    p.jaw = 0.3F + 0.3F * Mth.sin(t * 2.0F);
                } else {
                    float u = (t - 3.2F) / (impact - 3.2F);
                    float at = -1.25F * (float) Math.exp(-0.9F * 3.2F) * Mth.cos(1.15F * 3.2F);
                    p.y = Mth.lerp(u, at, 0.0F);
                    p.z = Mth.lerp(easeIn(u), -1.9F, -dist);
                    p.jaw = Mth.lerp(u, 0.3F, 1.0F);
                }
                p.roll = 16.0F * (float) Math.exp(-0.5F * t) * Mth.sin(2.4F * t);
                p.reach = smooth(1.5F, impact, t);
            }
            case LADY -> {
                // skips closer in three cuts, head tilted, between bursts of static
                int cut = Math.min(2, (int) (t / 2.0F));
                float[][] at = {{-0.6F, -0.1F, -8.0F, 22.0F}, {0.5F, 0.0F, -4.5F, -16.0F}, {-0.18F, 0.02F, -1.9F, 9.0F}};
                p.x = at[cut][0];
                p.y = at[cut][1];
                p.z = at[cut][2];
                p.roll = at[cut][3];
                float since = t - cut * 2.0F;
                p.noise = since < 0.5F ? 0.38F : 0.06F;
                p.flash = since < 0.4F ? 0.25F : 0.0F;
                p.reach = cut >= 2 ? 0.6F : 0.0F;
                p.shake = 0.05F;
            }
            case SPIDER -> {
                // drops from the ceiling on its thread, bounces, then jumps
                if (t < 3.0F) {
                    float u = t / 3.0F;
                    p.y = 1.6F * (1.0F - u * u);
                    p.pitch = 35.0F * (1.0F - u);
                } else if (t < 4.2F) {
                    p.y = -0.14F * Mth.sin((float) Math.PI * (t - 3.0F) / 1.2F);
                }
                p.z = t < 4.0F ? -1.9F : Mth.lerp(easeIn((t - 4.0F) / (impact - 4.0F)), -1.9F, -dist);
                p.legs = 1.0F;
                p.jaw = smooth(3.0F, impact, t);
                p.reach = 1.0F;
            }
            case CORPSE -> {
                // stop-motion: snaps from pose to pose, one per tick
                int f = Mth.clamp((int) t, 0, 3);
                float[] z = {-6.2F, -4.7F, -3.2F, -1.7F};
                float[] y = {-0.25F, -0.1F, -0.12F, -0.05F};
                float[] pitch = {48.0F, 44.0F, -12.0F, -4.0F};
                float[] roll = {10.0F, -14.0F, 18.0F, -8.0F};
                p.z = z[f];
                p.y = y[f];
                p.pitch = pitch[f];
                p.roll = roll[f];
                p.jaw = 0.35F;
                p.reach = 0.25F + 0.22F * f;
            }
            case DEMON -> {
                // rises from below in flames, roaring, then lunges
                float u = Math.min(1.0F, t / 4.0F);
                p.y = Mth.lerp(easeOut(u), -1.5F, 0.0F);
                p.pitch = Mth.lerp(u, -30.0F, 0.0F);
                p.z = t < 4.0F ? -2.3F : Mth.lerp(easeIn((t - 4.0F) / (impact - 4.0F)), -2.3F, -dist);
                p.jaw = 0.8F * smooth(0.5F, 3.0F, t) + 0.2F * smooth(4.0F, impact, t);
                p.reach = smooth(2.0F, impact, t);
                p.shake = 0.35F;
                p.flash = 0.12F;
            }
        }
    }

    private static void inFace(ScareKind kind, long seed, float t, float k, ScarePose p) {
        switch (kind) {
            case CLOWN -> {
                p.jaw = 0.72F + 0.28F * Mth.sin(k * 2.2F);
                p.roll += 9.0F * Mth.sin(k * 0.9F);
            }
            case LADY -> {
                p.jaw = smooth(0.0F, 5.0F, k);
                p.roll += 9.0F + k * 1.1F;
                p.noise = Math.max(p.noise, 0.12F);
            }
            case SPIDER -> {
                p.jaw = 0.5F + 0.5F * Mth.sin(k * 3.0F);
                p.legs = 0.6F;
            }
            case CORPSE -> {
                // violent twitches every two ticks
                int step = (int) (k / 2.0F);
                float blend = Mth.clamp((k - step * 2.0F) / 0.25F, 0.0F, 1.0F);
                float r0 = 22.0F * hash(seed, 100 + step - 1), r1 = 22.0F * hash(seed, 100 + step);
                float y0 = 14.0F * hash(seed, 200 + step - 1), y1 = 14.0F * hash(seed, 200 + step);
                p.roll += step == 0 ? r1 * blend : Mth.lerp(blend, r0, r1);
                p.yaw += step == 0 ? y1 * blend : Mth.lerp(blend, y0, y1);
                p.jaw = 0.6F + 0.4F * Math.abs(Mth.sin(k * 1.7F));
            }
            case DEMON -> {
                p.jaw = 0.9F + 0.1F * Mth.sin(k * 5.0F);
                p.roll += 2.5F * Mth.sin(k * 7.0F);
                p.pitch += 2.0F * Mth.sin(k * 5.3F);
                p.shake = Math.max(p.shake, 0.65F);
            }
        }
    }
}
