package com.frusta.motion;

/**
 * Everything about the whip that is plain numbers: how long each move lasts, when the crack lands,
 * how thick the whip is along its length, how hard it hits and how long a whipped target kneels.
 * The server hits at the crack tick and every client draws the crack at that same tick.
 */
public final class Whip {
    /** Points along the whip used by the physics and the drawing, handle end included. */
    public static final int NODES = 33;

    // --- the lash (attack key), in ticks from the click
    /** The arm goes up and back, lifting the whip off the ground. */
    public static final double LASH_WINDUP = 3;
    /** The loop reaches the tip and it cracks: damage, sound, shockwave. */
    public static final int LASH_CRACK = 6;
    /** From here on the whip is free and falls (physics). */
    public static final double LASH_FREE = 8;
    /** The arm is back at rest. */
    public static final int LASH_END = 14;
    public static final int LASH_COOLDOWN = 12;

    // --- the Imperial Crack (use key held: the whip spins overhead; let go: the crack)
    /** Let go earlier than this and it is a normal lash. */
    public static final int TWIRL_MIN = 10;
    /** Fully charged. */
    public static final int TWIRL_FULL = 30;
    public static final double IMPERIAL_WINDUP = 2;
    public static final int IMPERIAL_CRACK = 5;
    public static final double IMPERIAL_FREE = 8;
    public static final int IMPERIAL_END = 16;
    public static final int IMPERIAL_COOLDOWN = 30;

    // --- the grab (sneak + use)
    public static final double GRAB_WINDUP = 2;
    /** The loop reaches the target. */
    public static final double GRAB_REACH = 5;
    /** Wrapped around it. */
    public static final double GRAB_WRAPPED = 6.5;
    /** The target is pulled towards the player. */
    public static final int GRAB_YANK = 7;
    public static final double GRAB_FREE = 12;
    public static final int GRAB_END = 16;
    /** After the yank, when the target has landed at your feet and kneels (with Sottomissione). */
    public static final int GRAB_LANDED = GRAB_YANK + 8;
    public static final int GRAB_COOLDOWN = 20;

    // --- shape: a braided thong that tapers, then the fall, then the cracker at the tip
    public static final double FALL_START = 0.88;
    public static final double CRACKER_START = 0.96;
    private static final double THONG_BASE = 0.06;
    private static final double THONG_END = 0.036;
    private static final double FALL = 0.025;
    private static final double CRACKER = 0.019;

    /** Highest level of Sottomissione. */
    public static final int MAX_LEVEL = 5;

    private Whip() {
    }

    /** Radius in blocks at a fraction {@code f} of the length, from the handle (0) to the tip (1). */
    public static double radius(double f) {
        if (f < FALL_START) {
            return THONG_BASE - (THONG_BASE - THONG_END) * Math.pow(Math.max(0, f) / FALL_START, 0.85);
        }
        return f < CRACKER_START ? FALL : CRACKER;
    }

    /**
     * Damage of a lash: the whip's own damage times {@code perLevel} for every level of Sottomissione
     * (levels above V, only possible with commands, count as V).
     */
    public static float damage(double base, int sottomissione, double perLevel) {
        int level = Math.max(0, Math.min(MAX_LEVEL, sottomissione));
        return (float) (base * Math.pow(perLevel, level));
    }

    /** How long a target whipped with Sottomissione stays on its knees: 1 s at level I, 3 s at V. */
    public static int kneelTicks(int sottomissione) {
        int level = Math.max(0, Math.min(MAX_LEVEL, sottomissione));
        return level == 0 ? 0 : 10 + 10 * level;
    }

    /** Charge of the spin, 0 to 1, after holding the use key for this many ticks. */
    public static double charge(double ticksHeld) {
        return clamp(ticksHeld / TWIRL_FULL, 0, 1);
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    /** 0 below 0, 1 above 1, an S-curve in between (smoothstep). */
    public static double smooth(double x) {
        double t = clamp(x, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
