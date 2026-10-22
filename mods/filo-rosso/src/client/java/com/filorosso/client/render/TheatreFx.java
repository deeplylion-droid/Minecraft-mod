package com.filorosso.client.render;

import com.filorosso.entity.Burattinaio;
import com.filorosso.world.Theatre;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Theatre's dark above: red threads hang down from nowhere over the stage and the stalls,
 * swaying, some long enough to almost touch the boards. Dust falls through the light.
 */
public final class TheatreFx {
    private TheatreFx() {
    }

    /** Plain white, see-through by the alpha of each vertex. */
    private static final net.minecraft.client.renderer.rendertype.RenderType SPOT =
            net.minecraft.client.renderer.rendertype.RenderTypes.eyes(com.filorosso.FiloRosso.id("textures/entity/spot.png"));

    /** x, z, how far down it comes (y), seed. */
    private static final double[][] THREADS = new double[30][4];

    static {
        java.util.Random r = new java.util.Random(97);
        for (int i = 0; i < THREADS.length; i++) {
            THREADS[i][0] = -22 + r.nextDouble() * 44;
            THREADS[i][1] = -48 + r.nextDouble() * 60;
            THREADS[i][2] = Theatre.STAGE + 6 + r.nextDouble() * 26;
            THREADS[i][3] = r.nextDouble() * 100;
        }
    }

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(TheatreFx::submit);
    }

    public static boolean here(ClientLevel level) {
        return level != null && level.dimension() == Theatre.LEVEL;
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (!here(level) || mc.player == null) {
            return;
        }
        // dust shining in the follow spot
        for (var e : level.entitiesForRendering()) {
            if (e instanceof Burattinaio b && b.isAlive() && spotLevel(b, 0.0F) > 0.5F) {
                for (int i = 0; i < 2; i++) {
                    double h = level.getRandom().nextDouble();
                    double r = 4.2 * (1.0 - h) + 0.5 * h;
                    double a = level.getRandom().nextDouble() * Math.PI * 2.0;
                    double d = Math.sqrt(level.getRandom().nextDouble()) * r;
                    level.addParticle(ParticleTypes.WHITE_ASH, b.getX() - 3.0 * h + Math.cos(a) * d, Theatre.STAGE + 46.0 * h,
                            b.getZ() - 6.0 * h + Math.sin(a) * d, 0.0, -0.02, 0.0);
                }
            }
        }
        // dust drifting down through the dark
        for (int i = 0; i < 3; i++) {
            double x = mc.player.getX() + (level.getRandom().nextDouble() - 0.5) * 30.0;
            double z = mc.player.getZ() + (level.getRandom().nextDouble() - 0.5) * 30.0;
            double y = mc.player.getY() + 4.0 + level.getRandom().nextDouble() * 10.0;
            level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0.0, -0.01, 0.0);
        }
    }

    private static void submit(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!here(level)) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float time = level.getGameTime() + partial;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack poseStack = context.poseStack();
        int light = LightCoordsUtil.FULL_BRIGHT;
        for (var e : level.entitiesForRendering()) {
            if (e instanceof Burattinaio b && b.isAlive()) {
                spot(context, poseStack, camera, b, partial, time);
            }
        }
        for (double[] t : THREADS) {
            double top = Theatre.STAGE + 70;
            double bottom = t[2];
            List<Vec3> pts = new ArrayList<>();
            int seg = 16;
            for (int i = 0; i <= seg; i++) {
                double k = i / (double) seg;
                double y = top + (bottom - top) * k;
                double sway = Math.sin(time * 0.02 + t[3]) * 0.6 * k * k + Math.sin(time * 0.05 + t[3] * 2) * 0.15 * k;
                pts.add(new Vec3(t[0] + sway, y, t[1] + sway * 0.6));
            }
            Ribbons.thread(context.submitNodeCollector(), poseStack, camera, camera, pts, 0.06F, 0, light);
            Ribbons.glow(context.submitNodeCollector(), poseStack, camera, camera, pts, 0.16F, 0xD8202A, 0.22F);
        }
    }

    /** How bright the follow spot on the Burattinaio is now (0 while the lights are out, flickering back). */
    public static float spotLevel(Burattinaio b, float partial) {
        if (b.action() != Burattinaio.DARK) {
            return 1.0F;
        }
        float t = b.actionTime(partial);
        if (t < 2.0F) {
            return 1.0F - t / 2.0F;
        }
        if (t < 60.0F) {
            return 0.0F;
        }
        // the lights come back with a stutter
        return Mth.clamp((t - 60.0F) / 8.0F, 0.0F, 1.0F) * (((int) t) % 3 == 0 ? 0.3F : 1.0F);
    }

    /** The follow spot: a cone of dusty light from the dark above down onto it, and a pool of light on the boards. */
    private static void spot(LevelRenderContext context, PoseStack poseStack, Vec3 camera, Burattinaio b, float partial, float time) {
        float k = spotLevel(b, partial);
        if (k <= 0.01F) {
            return;
        }
        Vec3 pos = b.getPosition(partial);
        double floor = Theatre.STAGE + 0.03;
        Vec3 top = new Vec3(pos.x - 3.0, Theatre.STAGE + 46.0, pos.z - 6.0);
        Vec3 base = new Vec3(pos.x, floor, pos.z);
        double r0 = 0.5;
        double r1 = 4.2;
        int seg = 28;
        float flicker = 0.92F + 0.08F * Mth.sin(time * 0.37F) * Mth.sin(time * 0.11F + 1.3F);
        int cone = 0xFFF2D8;
        context.submitNodeCollector().submitCustomGeometry(poseStack, SPOT, (pose, buffer) -> {
            for (int i = 0; i < seg; i++) {
                double a0 = i * Math.PI * 2.0 / seg;
                double a1 = (i + 1) * Math.PI * 2.0 / seg;
                Vec3 t0 = top.add(Math.cos(a0) * r0, 0.0, Math.sin(a0) * r0).subtract(camera);
                Vec3 t1 = top.add(Math.cos(a1) * r0, 0.0, Math.sin(a1) * r0).subtract(camera);
                Vec3 b0 = base.add(Math.cos(a0) * r1, 0.0, Math.sin(a0) * r1).subtract(camera);
                Vec3 b1 = base.add(Math.cos(a1) * r1, 0.0, Math.sin(a1) * r1).subtract(camera);
                int ct = ARGB.color(0.0F, cone);
                int cb = ARGB.color(0.10F * k * flicker, cone);
                spotVertex(buffer, pose, t0, ct);
                spotVertex(buffer, pose, b0, cb);
                spotVertex(buffer, pose, b1, cb);
                spotVertex(buffer, pose, t1, ct);
            }
            // the pool of light on the boards: brightest in the middle
            Vec3 c = base.subtract(camera);
            for (int i = 0; i < seg; i++) {
                double a0 = i * Math.PI * 2.0 / seg;
                double a1 = (i + 1) * Math.PI * 2.0 / seg;
                for (int ring = 0; ring < 3; ring++) {
                    double ra = r1 * ring / 3.0;
                    double rb = r1 * (ring + 1) / 3.0;
                    int ca = ARGB.color(0.22F * k * flicker * (1.0F - ring / 3.0F), cone);
                    int cb = ARGB.color(0.22F * k * flicker * (1.0F - (ring + 1) / 3.0F), cone);
                    spotVertex(buffer, pose, c.add(Math.cos(a0) * ra, 0.0, Math.sin(a0) * ra), ca);
                    spotVertex(buffer, pose, c.add(Math.cos(a0) * rb, 0.0, Math.sin(a0) * rb), cb);
                    spotVertex(buffer, pose, c.add(Math.cos(a1) * rb, 0.0, Math.sin(a1) * rb), cb);
                    spotVertex(buffer, pose, c.add(Math.cos(a1) * ra, 0.0, Math.sin(a1) * ra), ca);
                }
            }
        });
    }

    private static void spotVertex(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, int color) {
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color).setUv(0.5F, 0.5F)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
