package com.frusta.motion;

/**
 * Maths of the humanoid arm (taken from the yo-yo's {@code ArmAim}): where a point of the arm ends
 * up in the world for given arm angles. Used to guess where the whip leaves the hand of a player
 * who is not being drawn this frame.
 *
 * <p>Model space is Minecraft's {@code HumanoidModel}: +Y down, -Z forward, -X the body's right;
 * a {@code ModelPart} turns by {@code Rz * Ry * Rx}. The player renderer maps it to the world by
 * turning by {@code 180 - bodyYaw} around Y, mirroring X and Y, scaling by 15/16 and moving the
 * model root 1.501 model blocks above the feet.</p>
 */
public final class ArmMath {
    /** Players are drawn at 15/16 scale. */
    public static final double PLAYER_SCALE = 0.9375;
    /** Height of the model root above the feet, in model blocks. */
    public static final double MODEL_ROOT = 1.501;
    /** Right arm pivot (shoulder), in model pixels. Crouching moves it to y = 5.2. */
    public static final double SHOULDER_X = -5;
    public static final double SHOULDER_Y = 2;
    public static final double SHOULDER_Y_CROUCHING = 5.2;

    private ArmMath() {
    }

    /**
     * World offset from the feet of a point given in arm pixels, for an arm with its pivot at
     * {@code pivotPx} (model pixels) and the given rotations.
     */
    public static V3 armPoint(V3 pivotPx, double xRot, double yRot, double zRot, V3 pointPx,
                              double bodyYawDeg, double scale) {
        V3 local = rotateZYX(pointPx.scale(1 / 16.0), zRot, yRot, xRot);
        V3 model = new V3(0, -MODEL_ROOT, 0).add(pivotPx.scale(1 / 16.0)).add(local);
        return modelToWorld(model, bodyYawDeg).scale(scale);
    }

    /** Turns a model-space vector into world space for a body turned by {@code bodyYawDeg}. */
    public static V3 modelToWorld(V3 m, double bodyYawDeg) {
        double theta = Math.toRadians(180 - bodyYawDeg);
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double vx = -m.x();
        double vy = -m.y();
        double vz = m.z();
        return new V3(vx * cos + vz * sin, vy, -vx * sin + vz * cos);
    }

    /** {@code Rz(z) * Ry(y) * Rx(x) * v}, the rotation order of a {@code ModelPart}. */
    public static V3 rotateZYX(V3 v, double z, double y, double x) {
        double cx = Math.cos(x);
        double sx = Math.sin(x);
        double x1 = v.x();
        double y1 = v.y() * cx - v.z() * sx;
        double z1 = v.y() * sx + v.z() * cx;
        double cy = Math.cos(y);
        double sy = Math.sin(y);
        double x2 = x1 * cy + z1 * sy;
        double z2 = -x1 * sy + z1 * cy;
        double cz = Math.cos(z);
        double sz = Math.sin(z);
        return new V3(x2 * cz - y1 * sz, x2 * sz + y1 * cz, z2);
    }
}
