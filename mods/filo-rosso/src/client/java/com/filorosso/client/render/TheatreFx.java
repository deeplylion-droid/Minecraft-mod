package com.filorosso.client.render;

import com.filorosso.world.Theatre;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.LightCoordsUtil;
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
}
