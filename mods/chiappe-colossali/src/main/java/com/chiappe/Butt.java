package com.chiappe;

/**
 * How big the cheeks are and how hard the fart hits, from the XP level (plain math, no game classes).
 * Sizes in blocks.
 */
public final class Butt {
    private Butt() {
    }

    /** From this level on they stop growing (and the fart one-shots the Ender Dragon). */
    public static final int MAX_LEVEL = 25;
    /** Diameter of one cheek at level 0 and at the top level. */
    public static final double MIN_DIAMETER = 0.45;
    public static final double MAX_DIAMETER = 10.0;
    /** Damage at the top level: the Ender Dragon has 200 health, hits on its head count in full. */
    public static final float TOP_DAMAGE = 1000.0F;

    public static int clamp(int xpLevel) {
        return Math.max(0, Math.min(MAX_LEVEL, xpLevel));
    }

    /** Every level makes them about 13% bigger: 0.45 blocks at level 0, 10 at level 25. */
    public static double diameter(double level) {
        double k = Math.max(0.0, Math.min(MAX_LEVEL, level)) / MAX_LEVEL;
        return MIN_DIAMETER * Math.pow(MAX_DIAMETER / MIN_DIAMETER, k);
    }

    /** How far the fart reaches from the player: 2.8 blocks at level 0, 10 at level 25. */
    public static double fartRadius(int level) {
        return 2.5 + 0.75 * diameter(level);
    }

    /**
     * 2 at level 0, 20% more every level (159 at level 24, so the dragon still survives one), then the
     * top level's 1000.
     */
    public static float fartDamage(int level) {
        if (level >= MAX_LEVEL) {
            return TOP_DAMAGE;
        }
        return (float) (2.0 * Math.pow(1.2, clamp(level)));
    }

    /** Bigger farts need longer to come back: 2 s at level 0, 4.5 s at level 25. */
    public static int cooldownTicks(int level) {
        return 40 + 2 * clamp(level);
    }

    /** How much farther back the third person camera goes than vanilla's 4 blocks, so it is not inside them. */
    public static double cameraExtra(int level) {
        return Math.max(0.0, diameter(level) * 1.15 - 0.8);
    }

    // ------------------------------------------------------------------ where the cheeks sit
    // In blocks, for a player of normal size, r = radius: x sideways, y up from the feet, z back.

    /** Height of the hips above the feet, and how far the back of the body is behind its middle. */
    public static final double HIPS = 12.0 / 16.0 * 0.9375;
    public static final double BACK = 2.0 / 16.0 * 0.9375;
    private static final double BODY_HALF_WIDTH = 0.25;
    private static final double BODY_HEIGHT = 1.9;
    /** The shape of one cheek, in radii: how far it is from the middle (they overlap: the cleft), how tall, how deep. */
    public static final double SIDE = 0.72;
    public static final double TALL = 1.06;
    public static final double DEEP = 0.82;

    /** Height of the middle of the cheeks: at the hips while they are small, then resting on the ground (a little sunk in). */
    public static double centreHeight(double r) {
        return Math.max(HIPS + 0.15 * r, (TALL - 0.12) * r);
    }

    /**
     * How far the middle of the cheeks is behind the back of the body: as close as they can be without
     * going into it. The big ones go round it, so the player stands in the cleft.
     */
    public static double back(double r) {
        double dx = Math.max(0.0, SIDE * r - BODY_HALF_WIDTH) / r;
        double h = centreHeight(r);
        double need = 0.0;
        for (int i = 0; i <= 19; i++) {
            double dy = (BODY_HEIGHT * i / 19.0 - h) / (TALL * r);
            double k = 1.0 - dx * dx - dy * dy;
            if (k > 0.0) {
                need = Math.max(need, DEEP * r * Math.sqrt(k));
            }
        }
        // the small ones sink into the body a little, so they look part of it
        return need - Math.min(0.3 * DEEP * r, 0.1);
    }

    /** Where the fart comes out: low in the cleft, at the back (height above the feet, distance behind the back of the body). */
    public static double exitHeight(double r) {
        return Math.max(0.15, centreHeight(r) - 0.45 * TALL * r);
    }

    public static double exitBack(double r) {
        return back(r) + DEEP * r * Math.sqrt(Math.max(0.0, 1.0 - SIDE * SIDE - 0.45 * 0.45));
    }
}
