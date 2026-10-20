package com.frusta.motion;

/**
 * Angles of the arm holding the whip ({xRot, yRot, zRot}, radians, {@code HumanoidModel} right arm)
 * during each move. Keyframes in ticks, joined by smooth curves; {@code rest} is the arm as vanilla
 * would hold any item, so every move starts and ends there.
 *
 * <p>Model conventions: xRot -PI/2 points the arm forward and -PI straight up; yRot turns it to the
 * right; zRot moves the right arm away from the body. Pointing where the head looks is
 * {@code {-PI/2 + pitch, headYaw}} (pitch positive looking down, headYaw relative to the body).</p>
 */
public final class WhipArm {
    private static final double HALF_PI = Math.PI / 2;

    private WhipArm() {
    }

    /** A lash of the given style {@code t} ticks after the click. */
    public static float[] lash(int style, double t, double pitch, double yaw, float[] rest) {
        double[] aim = {-HALF_PI + pitch, yaw, 0};
        double[] low = {-HALF_PI + pitch + 0.45, yaw, 0};
        double[][] keys = switch (Math.floorMod(style, WhipShape.STYLES)) {
            // from the right: the arm swings out to the side and back, then across the front
            case 1 -> new double[][]{
                    {0, rest[0], rest[1], rest[2]},
                    {2.5, -1.75, 1.9, 0.35},
                    {4.6, aim[0], aim[1] + 0.25, 0.1},
                    {6.0, low[0], yaw - 0.55, 0},
                    {9.0, -0.9, yaw * 0.5 - 0.3, 0},
                    {Whip.LASH_END, rest[0], rest[1], rest[2]}};
            // backhand: across the chest to the left, then out to the target
            case 2 -> new double[][]{
                    {0, rest[0], rest[1], rest[2]},
                    {2.5, -1.7, -1.15, -0.2},
                    {4.6, aim[0], aim[1] - 0.2, 0.15},
                    {6.0, low[0], yaw + 0.6, 0.3},
                    {9.0, -0.9, yaw * 0.5 + 0.3, 0.2},
                    {Whip.LASH_END, rest[0], rest[1], rest[2]}};
            // diagonal: from high on the right down to the left
            case 3 -> new double[][]{
                    {0, rest[0], rest[1], rest[2]},
                    {2.5, -2.6, 0.9, 0.6},
                    {4.6, aim[0], aim[1], 0.1},
                    {6.0, low[0], yaw - 0.45, 0},
                    {9.0, -0.9, yaw * 0.5 - 0.2, 0},
                    {Whip.LASH_END, rest[0], rest[1], rest[2]}};
            // overhead: straight up and back over the shoulder, then down onto the target
            default -> new double[][]{
                    {0, rest[0], rest[1], rest[2]},
                    {2.5, -2.8, yaw * 0.25, 0.12},
                    {4.6, aim[0], aim[1], 0},
                    {6.0, low[0], aim[1], 0},
                    {9.0, -0.9, yaw * 0.5, 0},
                    {Whip.LASH_END, rest[0], rest[1], rest[2]}};
        };
        return sample(keys, t);
    }

    /** Spinning the whip over the head: the arm up, the hand turning small circles with the whip. */
    public static float[] twirl(double t) {
        double phi = WhipShape.twirlAngle(t);
        double raise = Whip.smooth(t / 4);
        double x = -0.31 + (-2.85 + 0.31) * raise + 0.12 * Math.cos(phi) * raise;
        double y = 0.15 * Math.sin(phi) * raise;
        double z = 0.25 * raise + 0.08 * Math.sin(phi) * raise;
        return new float[]{(float) x, (float) y, (float) z};
    }

    /** The Imperial Crack, {@code t} ticks after letting go of the spin. */
    public static float[] imperial(double t, double pitch, double yaw, float[] spin, float[] rest) {
        double[][] keys = {
                {0, spin[0], spin[1], spin[2]},
                {1.6, -3.05, yaw * 0.2, 0.1},
                {3.8, -HALF_PI + pitch, yaw, 0},
                {5.2, -HALF_PI + pitch + 0.6, yaw, 0},
                {9.0, -0.8, yaw * 0.5, 0},
                {Whip.IMPERIAL_END, rest[0], rest[1], rest[2]}};
        return sample(keys, t);
    }

    /** The grab: thrown at the target, held there, then the yank. */
    public static float[] grab(double t, double pitch, double yaw, float[] rest) {
        double[][] keys = {
                {0, rest[0], rest[1], rest[2]},
                {2.0, -2.4, yaw * 0.4, 0.1},
                {4.2, -HALF_PI + pitch, yaw, 0},
                {Whip.GRAB_YANK, -HALF_PI + pitch + 0.2, yaw, 0},
                {Whip.GRAB_YANK + 1.2, -0.45, yaw * 0.3, 0.35},
                {11.0, -0.6, yaw * 0.2, 0.25},
                {Whip.GRAB_END, rest[0], rest[1], rest[2]}};
        return sample(keys, t);
    }

    /**
     * Catmull-Rom through the keyframes {time, x, y, z} (times increasing); holds the first and last
     * values outside them.
     */
    static float[] sample(double[][] keys, double t) {
        int last = keys.length - 1;
        if (t <= keys[0][0]) {
            return values(keys[0]);
        }
        if (t >= keys[last][0]) {
            return values(keys[last]);
        }
        int k = 0;
        while (k < last - 1 && t > keys[k + 1][0]) {
            k++;
        }
        double[] p0 = keys[Math.max(0, k - 1)];
        double[] p1 = keys[k];
        double[] p2 = keys[k + 1];
        double[] p3 = keys[Math.min(last, k + 2)];
        double span = p2[0] - p1[0];
        double s = (t - p1[0]) / span;
        float[] out = new float[3];
        for (int c = 1; c <= 3; c++) {
            // tangents scaled to this span, so uneven keyframe times do not overshoot
            double m1 = (p2[c] - p0[c]) / Math.max(1e-6, p2[0] - p0[0]) * span;
            double m2 = (p3[c] - p1[c]) / Math.max(1e-6, p3[0] - p1[0]) * span;
            double s2 = s * s;
            double s3 = s2 * s;
            out[c - 1] = (float) ((2 * s3 - 3 * s2 + 1) * p1[c] + (s3 - 2 * s2 + s) * m1
                    + (-2 * s3 + 3 * s2) * p2[c] + (s3 - s2) * m2);
        }
        return out;
    }

    private static float[] values(double[] key) {
        return new float[]{(float) key[1], (float) key[2], (float) key[3]};
    }
}
