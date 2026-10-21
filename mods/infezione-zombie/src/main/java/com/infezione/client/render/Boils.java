package com.infezione.client.render;

import com.infezione.InfezioneMod;
import com.infezione.infection.Stages;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The boils of the infection: swollen, glossy domes of pus that grow out of the skin from stage 3 (a few
 * around the bite), cover the body at stage 4 and the zombie at stage 5, where some have burst into open
 * sores. They throb; from stage 4 each one swells now and then until it bursts, then grows back.
 *
 * <p>Each boil sits on a face of a part of the player model, in the part's own pixels, so it follows the
 * part wherever it moves. Every player gets their own (a few of the places are left out, the others move a
 * little and change size), always the same ones.</p>
 */
public final class Boils {
    public static final ResourceLocation TEXTURE = InfezioneMod.id("textures/entity/boil.png");

    public enum Part { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    /** Which face of the part: front (-z), back (+z), the character's left (+x) or right (-x). */
    public enum Face { FRONT, BACK, LEFT, RIGHT }

    /**
     * A place for a boil: on {@code face} of {@code part}, {@code a} pixels across and {@code b} down from
     * the face's top left (as the skin texture draws it), {@code radius} pixels wide, from {@code stage}.
     * A {@code sore} is an open wound from the start.
     */
    public record Spot(Part part, Face face, float a, float b, float radius, int stage, boolean sore) {
    }

    /** Around the bite (the left of the jaw) first, then everywhere. */
    public static final Spot[] SPOTS = {
            // stage 3: the bite festers
            new Spot(Part.HEAD, Face.FRONT, 7.0F, 5.7F, 1.0F, 3, false),
            new Spot(Part.HEAD, Face.LEFT, 1.6F, 6.6F, 1.3F, 3, false),
            new Spot(Part.BODY, Face.FRONT, 6.6F, 0.9F, 1.0F, 3, false),
            new Spot(Part.LEFT_ARM, Face.FRONT, 2.0F, 10.4F, 0.8F, 3, false),
            // stage 4: everywhere
            new Spot(Part.HEAD, Face.FRONT, 1.3F, 1.4F, 0.9F, 4, false),
            new Spot(Part.HEAD, Face.FRONT, 1.8F, 6.7F, 0.8F, 4, false),
            new Spot(Part.HEAD, Face.BACK, 5.0F, 6.2F, 1.2F, 4, false),
            new Spot(Part.BODY, Face.FRONT, 2.2F, 4.6F, 1.4F, 4, false),
            new Spot(Part.BODY, Face.BACK, 5.5F, 3.0F, 1.6F, 4, false),
            new Spot(Part.RIGHT_ARM, Face.RIGHT, 2.0F, 5.0F, 1.1F, 4, false),
            new Spot(Part.LEFT_ARM, Face.LEFT, 2.0F, 7.0F, 1.2F, 4, false),
            new Spot(Part.RIGHT_LEG, Face.FRONT, 1.5F, 3.0F, 1.2F, 4, false),
            new Spot(Part.RIGHT_ARM, Face.FRONT, 2.2F, 10.0F, 0.8F, 4, false),
            // stage 5: the zombie is covered, and some have burst
            new Spot(Part.HEAD, Face.FRONT, 5.2F, 0.9F, 1.0F, 5, true),
            new Spot(Part.HEAD, Face.RIGHT, 4.0F, 4.2F, 1.3F, 5, false),
            new Spot(Part.BODY, Face.FRONT, 5.0F, 8.0F, 1.2F, 5, true),
            new Spot(Part.BODY, Face.BACK, 2.0F, 9.0F, 1.3F, 5, false),
            new Spot(Part.LEFT_LEG, Face.FRONT, 2.5F, 7.0F, 1.3F, 5, false),
            new Spot(Part.RIGHT_ARM, Face.FRONT, 1.5F, 6.5F, 1.0F, 5, true),
            new Spot(Part.LEFT_ARM, Face.BACK, 2.0F, 3.0F, 1.4F, 5, false),
            new Spot(Part.LEFT_LEG, Face.LEFT, 2.0F, 2.5F, 1.1F, 5, false),
            new Spot(Part.BODY, Face.FRONT, 0.8F, 10.5F, 1.0F, 5, false),
    };

    private static final int SEGMENTS = 9;
    private static final int RINGS = 4;

    private Boils() {
    }

    /** How a player's boil looks this frame: where exactly, how big, swelling or burst. */
    public static final class Shape {
        public boolean shown;
        public float a;
        public float b;
        public float radius;
        public float height;
        public boolean sore;
        public float tremble;
    }

    /**
     * The boil {@code index} of a player ({@code seed}) at {@code stage}, at {@code age} ticks of the player:
     * left out for some players, a little moved and resized for each, throbbing, and from stage 4 swelling
     * until it bursts (it is then an open sore for a while, and grows back).
     */
    public static void shape(int index, long seed, int stage, float age, Shape out) {
        Spot spot = SPOTS[index];
        out.shown = stage >= spot.stage() && hash(seed, index, 1) > 0.18F;
        if (!out.shown) {
            return;
        }
        out.a = spot.a() + (hash(seed, index, 2) - 0.5F) * 1.2F;
        out.b = spot.b() + (hash(seed, index, 3) - 0.5F) * 1.2F;
        float grown = stage == 3 ? 0.9F : stage == 4 ? 1.25F : 1.45F;
        float size = spot.radius() * grown * (0.8F + 0.45F * hash(seed, index, 4));
        float scale = 1.0F + 0.07F * Mth.sin(age * 0.11F + index * 1.7F);
        out.sore = spot.sore();
        out.tremble = 0.0F;
        if (stage >= 4 && !spot.sore()) {
            float period = period(seed, index, stage);
            float t = (age + hash(seed, index, 5) * period) % period;
            if (t < 70.0F) {
                // just burst: an open sore
                out.sore = true;
                scale = 0.8F;
            } else if (t < 260.0F) {
                // growing back
                scale *= 0.35F + 0.65F * smooth((t - 70.0F) / 190.0F);
            } else if (t > period - 50.0F) {
                // swelling up, trembling, about to burst
                float k = (t - (period - 50.0F)) / 50.0F;
                scale *= 1.0F + 0.45F * k * k;
                out.tremble = k;
            }
        }
        out.radius = size * scale;
        out.height = out.sore ? size * 0.55F : out.radius * 1.05F;
    }

    /** The number of the latest burst of a boil (it changes when the boil bursts), or -1 if it never bursts. */
    public static long burstCycle(int index, long seed, int stage, float age) {
        Spot spot = SPOTS[index];
        if (stage < 4 || spot.sore() || stage < spot.stage() || hash(seed, index, 1) <= 0.18F) {
            return -1;
        }
        float period = period(seed, index, stage);
        return (long) Math.floor((age + hash(seed, index, 5) * period) / period);
    }

    /** Ticks between two bursts of a boil: half a minute to a minute, faster on a zombie. */
    private static float period(long seed, int index, int stage) {
        float base = stage == Stages.ZOMBIE ? 420.0F : 640.0F;
        return base + hash(seed, index, 6) * base;
    }

    /** Where a boil sits on its part, in the part's pixels: the point on the face and the way out of it. */
    public static void anchor(Spot spot, float a, float b, boolean slim, Vector3f point, Vector3f normal) {
        float x0;
        float y0;
        float z0;
        float w;
        float h;
        float d;
        switch (spot.part()) {
            case HEAD -> {
                x0 = -4; y0 = -8; z0 = -4; w = 8; h = 8; d = 8;
            }
            case BODY -> {
                x0 = -4; y0 = 0; z0 = -2; w = 8; h = 12; d = 4;
            }
            case RIGHT_ARM -> {
                x0 = slim ? -2 : -3; y0 = -2; z0 = -2; w = slim ? 3 : 4; h = 12; d = 4;
            }
            case LEFT_ARM -> {
                x0 = -1; y0 = -2; z0 = -2; w = slim ? 3 : 4; h = 12; d = 4;
            }
            default -> {
                x0 = -2; y0 = 0; z0 = -2; w = 4; h = 12; d = 4;
            }
        }
        float across = Mth.clamp(a, 0.4F, (spot.face() == Face.LEFT || spot.face() == Face.RIGHT ? d : w) - 0.4F);
        float down = Mth.clamp(b, 0.4F, h - 0.4F);
        switch (spot.face()) {
            case FRONT -> {
                point.set(x0 + across, y0 + down, z0);
                normal.set(0, 0, -1);
            }
            case BACK -> {
                point.set(x0 + w - across, y0 + down, z0 + d);
                normal.set(0, 0, 1);
            }
            case LEFT -> {
                point.set(x0 + w, y0 + down, z0 + across);
                normal.set(1, 0, 0);
            }
            default -> {
                point.set(x0, y0 + down, z0 + d - across);
                normal.set(-1, 0, 0);
            }
        }
    }

    /**
     * Draws a boil as a lumpy dome (or an open sore: a swollen rim around a dark hole) on the face of a part,
     * the pose stack being in the part's space. Sizes are in model pixels.
     */
    public static void draw(PoseStack poseStack, VertexConsumer buffer, Vector3f point, Vector3f normal, Shape shape,
                            long seed, int index, float age, int light, int overlay) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normals = pose.normal();
        // two directions along the face
        Vector3f t1 = Math.abs(normal.x) > 0.5F ? new Vector3f(0, 0, 1) : new Vector3f(1, 0, 0);
        Vector3f t2 = new Vector3f(normal).cross(t1).normalize();
        float shakeA = shape.tremble > 0 ? 0.08F * shape.tremble * Mth.sin(age * 3.1F + index) : 0.0F;
        float shakeB = shape.tremble > 0 ? 0.08F * shape.tremble * Mth.sin(age * 2.7F + index * 2) : 0.0F;
        float[][] ring = new float[RINGS + 1][];
        for (int r = 0; r <= RINGS; r++) {
            ring[r] = profile(shape.sore, r);
        }
        float u0 = shape.sore ? 0.5F : 0.0F;
        for (int r = 0; r < RINGS; r++) {
            for (int s = 0; s < SEGMENTS; s++) {
                int s1 = (s + 1) % SEGMENTS;
                vertex(buffer, matrix, normals, point, normal, t1, t2, shape, seed, index, ring[r], s, r, shakeA, shakeB, u0, light, overlay);
                vertex(buffer, matrix, normals, point, normal, t1, t2, shape, seed, index, ring[r], s1, r, shakeA, shakeB, u0, light, overlay);
                vertex(buffer, matrix, normals, point, normal, t1, t2, shape, seed, index, ring[r + 1], s1, r + 1, shakeA, shakeB, u0, light, overlay);
                vertex(buffer, matrix, normals, point, normal, t1, t2, shape, seed, index, ring[r + 1], s, r + 1, shakeA, shakeB, u0, light, overlay);
            }
        }
    }

    /** {radius, height} of a ring, as shares of the boil's: a round dome, or the rim and the hole of a sore. */
    private static float[] profile(boolean sore, int r) {
        if (sore) {
            float[][] p = {{1.0F, 0.0F}, {0.82F, 0.7F}, {0.6F, 0.55F}, {0.32F, 0.12F}, {0.0F, 0.08F}};
            return p[r];
        }
        double theta = Math.PI / 2 * r / RINGS;
        return new float[]{(float) Math.cos(theta), (float) Math.sin(theta)};
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Matrix3f normals, Vector3f point, Vector3f normal,
                               Vector3f t1, Vector3f t2, Shape shape, long seed, int index, float[] ring, int s, int r,
                               float shakeA, float shakeB, float u0, int light, int overlay) {
        double phi = Math.PI * 2 * s / SEGMENTS;
        float cos = (float) Math.cos(phi);
        float sin = (float) Math.sin(phi);
        // lumpy, not a perfect dome
        float lump = r == RINGS ? 1.0F : 1.0F + 0.22F * (hash(seed, index * 31 + s, 7) - 0.5F);
        float radius = shape.radius * ring[0] * lump;
        float height = shape.height * ring[1] - 0.05F;
        float x = point.x + (t1.x * cos + t2.x * sin) * radius + normal.x * height + t1.x * shakeA + t2.x * shakeB;
        float y = point.y + (t1.y * cos + t2.y * sin) * radius + normal.y * height + t1.y * shakeA + t2.y * shakeB;
        float z = point.z + (t1.z * cos + t2.z * sin) * radius + normal.z * height + t1.z * shakeA + t2.z * shakeB;
        // the normal of a dome (for the sore's hole, roughly the face's)
        float k = shape.sore ? 0.4F : ring[0];
        float nx = (t1.x * cos + t2.x * sin) * k + normal.x * (1.0F - k * 0.6F);
        float ny = (t1.y * cos + t2.y * sin) * k + normal.y * (1.0F - k * 0.6F);
        float nz = (t1.z * cos + t2.z * sin) * k + normal.z * (1.0F - k * 0.6F);
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        // the texture is a disc: its centre at the top of the boil, its edge at the skin
        float tr = 0.47F * (1.0F - (float) r / RINGS);
        float u = u0 + (0.25F + 0.5F * tr * cos);
        float v = 0.5F + tr * sin;
        buffer.vertex(matrix, x / 16.0F, y / 16.0F, z / 16.0F)
                .color(1.0F, 1.0F, 1.0F, 1.0F)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(normals, nx / length, ny / length, nz / length)
                .endVertex();
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    /** A repeatable pseudo-random number between 0 and 1. */
    static float hash(long seed, int a, int b) {
        long h = seed * 0x9E3779B97F4A7C15L + a * 0xC2B2AE3D27D4EB4FL + b * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }
}
