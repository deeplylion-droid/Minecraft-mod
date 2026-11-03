package com.punizioni.server;

import com.punizioni.data.Filth;
import com.punizioni.machine.Pillory;
import com.punizioni.machine.PilloryPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a thrown thing meets a body: the line it flies along against the boxes of the player model
 * (head, body, arms, legs) in the pose the body is drawn in, standing or bent in the pillory. The
 * answer is the part, the face of it and the point on that face in the skin's own directions, so the
 * stain can be painted exactly there. The pillory's wood (board, posts, stage) stops things too.
 */
public final class BodyHit {
    private BodyHit() {
    }

    /** Not a body part: the pillory's wood. */
    public static final int WOOD = -1;

    /** What was hit, where on it (0..1 across and down its face, as in the skin) and how far along the line. */
    public record Hit(int part, int face, float u, float v, double distance) {
    }

    /** A box of the model: a part, its pivot and rotation (radians), its corners in the part's own pixels. */
    private record Box(int part, float px, float py, float pz, float xRot, float yRot, float x0, float y0, float z0, float x1, float y1, float z1) {
    }

    // ------------------------------------------------------------------ poses

    private static Box[] standing(float headYaw, float headPitch) {
        return new Box[]{
                new Box(Filth.HEAD, 0.0F, 0.0F, 0.0F, headPitch, headYaw, -4, -8, -4, 4, 0, 4),
                new Box(Filth.BODY, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -4, 0, -2, 4, 12, 2),
                new Box(Filth.RIGHT_ARM, -5.0F, 2.0F, 0.0F, 0.0F, 0.0F, -3, -2, -2, 1, 10, 2),
                new Box(Filth.LEFT_ARM, 5.0F, 2.0F, 0.0F, 0.0F, 0.0F, -1, -2, -2, 3, 10, 2),
                new Box(Filth.RIGHT_LEG, -1.9F, 12.0F, 0.0F, 0.0F, 0.0F, -2, 0, -2, 2, 12, 2),
                new Box(Filth.LEFT_LEG, 1.9F, 12.0F, 0.0F, 0.0F, 0.0F, -2, 0, -2, 2, 12, 2)};
    }

    private static Box[] pilloried(float headYaw, float headPitch) {
        float yaw = Mth.clamp(headYaw, -PilloryPose.HEAD_YAW, PilloryPose.HEAD_YAW);
        float pitch = Mth.clamp(headPitch, -PilloryPose.HEAD_PITCH, PilloryPose.HEAD_PITCH);
        return new Box[]{
                new Box(Filth.HEAD, 0.0F, PilloryPose.HEAD_Y, PilloryPose.HEAD_Z, pitch, yaw, -4, -8, -4, 4, 0, 4),
                new Box(Filth.BODY, 0.0F, PilloryPose.NECK_Y, PilloryPose.NECK_Z, PilloryPose.BEND, 0.0F, -4, 0, -2, 4, 12, 2),
                new Box(Filth.RIGHT_ARM, -5.0F, PilloryPose.SHOULDER_Y, PilloryPose.SHOULDER_Z, PilloryPose.ARM_X_ROT, PilloryPose.ARM_SPREAD,
                        -3, -2, -2, 1, 10, 2),
                new Box(Filth.LEFT_ARM, 5.0F, PilloryPose.SHOULDER_Y, PilloryPose.SHOULDER_Z, PilloryPose.ARM_X_ROT, -PilloryPose.ARM_SPREAD,
                        -1, -2, -2, 3, 10, 2),
                new Box(Filth.RIGHT_LEG, -1.9F, 12.0F, 0.0F, 0.0F, 0.0F, -2, 0, -2, 2, 12, 2),
                new Box(Filth.LEFT_LEG, 1.9F, 12.0F, 0.0F, 0.0F, 0.0F, -2, 0, -2, 2, 12, 2)};
    }

    /** The pillory's wood, in its own frame (blocks: right, up, forward). */
    private static final double[][] WOODWORK = {
            // the board (its holes are full of the one locked in it)
            {-PilloryPose.BOARD_HALF_WIDTH, PilloryPose.BOARD_BOTTOM, -PilloryPose.BOARD_HALF_THICKNESS,
                    PilloryPose.BOARD_HALF_WIDTH, PilloryPose.BOARD_TOP, PilloryPose.BOARD_HALF_THICKNESS},
            // posts
            {-1.02, 0.0, -0.08, -0.86, 2.28, 0.08},
            {0.86, 0.0, -0.08, 1.02, 2.28, 0.08},
            // the beam over them
            {-1.12, 2.22, -0.1, 1.12, 2.38, 0.1},
            // the stage
            {-1.06, 0.0, -1.05, 1.06, PilloryPose.PLATFORM, 0.5}};

    // ------------------------------------------------------------------ casting

    /** A standing player (or one in any other pose: close enough), hit along a line. */
    public static @Nullable Hit standing(LivingEntity target, Vec3 from, Vec3 dir, double length, boolean nearest) {
        float yawRel = Mth.wrapDegrees(target.getYHeadRot() - target.yBodyRot) * Mth.DEG_TO_RAD;
        Box[] boxes = standing(yawRel, target.getXRot() * Mth.DEG_TO_RAD);
        return cast(boxes, target.position(), target.yBodyRot, target.getScale(), from, dir, length, nearest);
    }

    /** The pillory: its wood and whoever is locked in it, hit along a line (null: through the empty space). */
    public static @Nullable Hit pillory(Pillory pillory, Vec3 from, Vec3 dir, double length) {
        Hit best = null;
        LivingEntity victim = pillory.victim();
        if (victim != null && victim.getVehicle() == pillory) {
            float yawRel = Mth.wrapDegrees(victim.getYHeadRot() - pillory.getYRot()) * Mth.DEG_TO_RAD;
            best = cast(pilloried(yawRel, victim.getXRot() * Mth.DEG_TO_RAD), victim.position(), pillory.getYRot(), victim.getScale(), from, dir,
                    length, false);
        }
        Hit wood = wood(pillory, from, dir, length);
        if (wood != null && (best == null || wood.distance < best.distance)) {
            best = wood;
        }
        return best;
    }

    private static @Nullable Hit wood(Pillory pillory, Vec3 from, Vec3 dir, double length) {
        Vec3 r = pillory.right();
        Vec3 f = pillory.forward();
        Vec3 q = from.subtract(pillory.position());
        double[] p = {q.dot(r), q.y, q.dot(f)};
        double[] d = {dir.dot(r), dir.y, dir.dot(f)};
        double best = Double.MAX_VALUE;
        for (double[] b : WOODWORK) {
            double[] t = slab(p, d, b[0], b[1], b[2], b[3], b[4], b[5]);
            if (t != null && t[0] <= length && t[0] < best) {
                best = t[0];
            }
        }
        return best < Double.MAX_VALUE ? new Hit(WOOD, Filth.FRONT, 0.5F, 0.5F, best) : null;
    }

    /**
     * The line from {@code from} along {@code dir} (blocks) against the boxes of a body standing at
     * {@code feet}, turned {@code bodyYaw}. With {@code nearest}, a line that misses every box still
     * hits the part it passes closest to (the bounding box was hit: the throw counts).
     */
    private static @Nullable Hit cast(Box[] boxes, Vec3 feet, float bodyYaw, float scale, Vec3 from, Vec3 dir, double length, boolean nearest) {
        double s = PilloryPose.PX * Math.max(0.05F, scale);
        float yaw = bodyYaw * Mth.DEG_TO_RAD;
        // the body's right and forward (the model's -x and -z)
        double rx = -Mth.cos(yaw);
        double rz = -Mth.sin(yaw);
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        double qx = from.x - feet.x;
        double qy = from.y - feet.y;
        double qz = from.z - feet.z;
        double[] mp = {-(qx * rx + qz * rz) / s, 24.0 - qy / s, -(qx * fx + qz * fz) / s};
        double[] md = {-(dir.x * rx + dir.z * rz) / s, -dir.y / s, -(dir.x * fx + dir.z * fz) / s};

        Hit best = null;
        Box closest = null;
        double closestDist = Double.MAX_VALUE;
        double[] closestLocal = null;
        double[] closestDir = null;
        for (Box b : boxes) {
            double[] lp = toPart(b, mp, false);
            double[] ld = toPart(b, md, true);
            double[] t = slab(lp, ld, b.x0, b.y0, b.z0, b.x1, b.y1, b.z1);
            if (t != null && t[0] <= length && (best == null || t[0] < best.distance)) {
                double hx = lp[0] + ld[0] * t[0];
                double hy = lp[1] + ld[1] * t[0];
                double hz = lp[2] + ld[2] * t[0];
                int face = face((int) t[1], ld);
                float[] uv = uv(b, face, hx, hy, hz);
                best = new Hit(b.part, face, uv[0], uv[1], t[0]);
            }
            if (nearest) {
                // how close the line passes to the middle of the box
                double cx = (b.x0 + b.x1) * 0.5 - lp[0];
                double cy = (b.y0 + b.y1) * 0.5 - lp[1];
                double cz = (b.z0 + b.z1) * 0.5 - lp[2];
                double dd = ld[0] * ld[0] + ld[1] * ld[1] + ld[2] * ld[2];
                double k = dd > 1.0E-12 ? Mth.clamp((cx * ld[0] + cy * ld[1] + cz * ld[2]) / dd, 0.0, length) : 0.0;
                double ex = lp[0] + ld[0] * k - (b.x0 + b.x1) * 0.5;
                double ey = lp[1] + ld[1] * k - (b.y0 + b.y1) * 0.5;
                double ez = lp[2] + ld[2] * k - (b.z0 + b.z1) * 0.5;
                double dist = ex * ex + ey * ey + ez * ez;
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = b;
                    closestLocal = new double[]{lp[0] + ld[0] * k, lp[1] + ld[1] * k, lp[2] + ld[2] * k, k};
                    closestDir = ld;
                }
            }
        }
        if (best == null && nearest && closest != null) {
            // the face that looks back along the line, the closest point pressed onto it
            double ax = Math.abs(closestDir[0]) / (closest.x1 - closest.x0);
            double ay = Math.abs(closestDir[1]) / (closest.y1 - closest.y0);
            double az = Math.abs(closestDir[2]) / (closest.z1 - closest.z0);
            int axis = ax >= ay && ax >= az ? 0 : ay >= az ? 1 : 2;
            int face = face(axis, closestDir);
            double hx = Mth.clamp(closestLocal[0], closest.x0, closest.x1);
            double hy = Mth.clamp(closestLocal[1], closest.y0, closest.y1);
            double hz = Mth.clamp(closestLocal[2], closest.z0, closest.z1);
            float[] uv = uv(closest, face, hx, hy, hz);
            best = new Hit(closest.part, face, uv[0], uv[1], closestLocal[3]);
        }
        return best;
    }

    /** Model pixels into a part's own pixels: the inverse of its pivot and rotation (vanilla turns Z, then Y, then X). */
    private static double[] toPart(Box b, double[] m, boolean direction) {
        double x = direction ? m[0] : m[0] - b.px;
        double y = direction ? m[1] : m[1] - b.py;
        double z = direction ? m[2] : m[2] - b.pz;
        double cy = Math.cos(b.yRot);
        double sy = Math.sin(b.yRot);
        double x1 = x * cy - z * sy;
        double z1 = x * sy + z * cy;
        double cx = Math.cos(b.xRot);
        double sx = Math.sin(b.xRot);
        double y2 = y * cx + z1 * sx;
        double z2 = -y * sx + z1 * cx;
        return new double[]{x1, y2, z2};
    }

    /** The line against an axis-aligned box: {entry distance, axis of entry}, or null. Starting inside counts as 0. */
    private static double @Nullable [] slab(double[] p, double[] d, double x0, double y0, double z0, double x1, double y1, double z1) {
        double[] lo = {x0, y0, z0};
        double[] hi = {x1, y1, z1};
        double enter = -Double.MAX_VALUE;
        double exit = Double.MAX_VALUE;
        int axis = 0;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0E-12) {
                if (p[i] < lo[i] || p[i] > hi[i]) {
                    return null;
                }
                continue;
            }
            double a = (lo[i] - p[i]) / d[i];
            double b = (hi[i] - p[i]) / d[i];
            double near = Math.min(a, b);
            double far = Math.max(a, b);
            if (near > enter) {
                enter = near;
                axis = i;
            }
            exit = Math.min(exit, far);
        }
        if (enter > exit || exit < 0.0) {
            return null;
        }
        return new double[]{Math.max(0.0, enter), axis};
    }

    /** The face a line going along {@code d} enters through, on that axis. */
    private static int face(int axis, double[] d) {
        return switch (axis) {
            // the model's -x is the body's right side
            case 0 -> d[0] > 0.0 ? Filth.RIGHT : Filth.LEFT;
            // y points down: going down, it came in through the top
            case 1 -> d[1] > 0.0 ? Filth.TOP : Filth.BOTTOM;
            default -> d[2] > 0.0 ? Filth.FRONT : Filth.BACK;
        };
    }

    /** Where on the face (0..1 across, 0..1 down), in the directions the skin lays that face out. */
    private static float[] uv(Box b, int face, double x, double y, double z) {
        double w = b.x1 - b.x0;
        double h = b.y1 - b.y0;
        double d = b.z1 - b.z0;
        double u;
        double v;
        switch (face) {
            case Filth.FRONT -> {
                u = (x - b.x0) / w;
                v = (y - b.y0) / h;
            }
            case Filth.BACK -> {
                u = (b.x1 - x) / w;
                v = (y - b.y0) / h;
            }
            case Filth.RIGHT -> {
                u = (b.z1 - z) / d;
                v = (y - b.y0) / h;
            }
            case Filth.LEFT -> {
                u = (z - b.z0) / d;
                v = (y - b.y0) / h;
            }
            case Filth.TOP -> {
                u = (x - b.x0) / w;
                v = (b.z1 - z) / d;
            }
            default -> {
                u = (x - b.x0) / w;
                v = (z - b.z0) / d;
            }
        }
        return new float[]{(float) Mth.clamp(u, 0.0, 1.0), (float) Mth.clamp(v, 0.0, 1.0)};
    }
}
