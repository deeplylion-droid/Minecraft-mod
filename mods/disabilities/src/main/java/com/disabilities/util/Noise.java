package com.disabilities.util;

/** Smooth 1D value noise in [-1, 1], deterministic for a seed: tremors, sway, stumbles. */
public final class Noise {
    private Noise() {
    }

    public static float hash(long seed, long i) {
        long z = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (float) ((z >>> 11) * 0x1.0p-53 * 2.0 - 1.0);
    }

    /** Smooth noise at time t (one new random value per unit of t). */
    public static float smooth(long seed, double t) {
        long i = (long) Math.floor(t);
        float f = (float) (t - i);
        f = f * f * (3.0F - 2.0F * f);
        float a = hash(seed, i);
        float b = hash(seed, i + 1);
        return a + (b - a) * f;
    }

    /** Two octaves: a slow wobble plus a faster shiver. */
    public static float tremor(long seed, double t) {
        return 0.65F * smooth(seed, t) + 0.35F * smooth(seed + 17, t * 2.7);
    }
}
