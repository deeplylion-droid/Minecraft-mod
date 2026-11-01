package com.punizioni.machine;

import net.minecraft.util.Mth;

/**
 * Where the four limbs of someone lying spread-eagle on the quartering rack are, as the horses
 * stretch them: shared by the server (where the horses stand) and the client (the ropes, the
 * stretched model). Machine frame, in blocks: x to the right, y up, z forward. The victim lies on
 * their back with the feet towards the front end of the rack and the arms over the head, towards the
 * back; the hips stay where they are and everything else grows away from them.
 */
public final class RackGeometry {
    private RackGeometry() {
    }

    /** The top of the rack. */
    public static final double TOP = 0.86;
    /** Where the hips are (the victim's position on the rack). */
    public static final double HIP_Z = 0.28;
    /** A player's model is drawn at 15/16. */
    private static final double PX = 0.9375 / 16.0;
    /** Spread of the legs and of the arms from the body's axis (radians). */
    public static final float LEG_SPREAD = 0.32F;
    public static final float ARM_SPREAD = 0.42F;

    /** How much longer each part gets at full stretch (1 = twice as long). */
    public static float legScale(float stretch) {
        return 1.0F + 1.6F * stretch;
    }

    public static float armScale(float stretch) {
        return 1.0F + 1.6F * stretch;
    }

    public static float bodyScale(float stretch) {
        return 1.0F + 0.7F * stretch;
    }

    /** The shoulders, where the arms start. */
    public static double shoulderZ(float stretch) {
        return HIP_Z - 10.0 * PX * bodyScale(stretch);
    }

    /** The middle of the head (for the victim's eyes). */
    public static double headZ(float stretch) {
        return HIP_Z - 12.0 * PX * bodyScale(stretch) - 4.0 * PX;
    }

    /** Right (side = 1) or left (side = -1) ankle: x, z. */
    public static double[] ankle(int side, float stretch) {
        double len = 12.0 * PX * legScale(stretch);
        double x = side * (1.9 * PX + len * Mth.sin(LEG_SPREAD));
        double z = HIP_Z + len * Mth.cos(LEG_SPREAD);
        return new double[]{x, z};
    }

    /** Right (side = 1) or left (side = -1) wrist: x, z. */
    public static double[] wrist(int side, float stretch) {
        double len = 11.0 * PX * armScale(stretch);
        double x = side * (5.0 * PX + len * Mth.sin(ARM_SPREAD));
        double z = shoulderZ(stretch) - len * Mth.cos(ARM_SPREAD);
        return new double[]{x, z};
    }

    /** Which way each horse pulls from its limb (unit x, z): the limb's own direction, a little wider. */
    public static double[] pull(int limb) {
        boolean arm = limb < 2;
        int side = limb % 2 == 0 ? 1 : -1;
        double a = (arm ? ARM_SPREAD : LEG_SPREAD) + 0.25;
        return new double[]{side * Math.sin(a), arm ? -Math.cos(a) : Math.cos(a)};
    }

    /** The limb a horse is tied to: 0 right wrist, 1 left wrist, 2 right ankle, 3 left ankle (x, z). */
    public static double[] anchor(int limb, float stretch) {
        int side = limb % 2 == 0 ? 1 : -1;
        return limb < 2 ? wrist(side, stretch) : ankle(side, stretch);
    }
}
