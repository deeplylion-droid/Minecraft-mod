package com.frusta.client.fx;

import com.frusta.FrustaMod;
import com.frusta.client.WhipAnim;
import com.frusta.client.WhipAnims;
import com.frusta.config.FrustaConfig;
import com.frusta.item.WhipMaterial;
import com.frusta.motion.V3;
import com.frusta.motion.Whip;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The show around the whip: a glowing trail behind the tip while it moves, and at every crack a
 * shockwave ring around the tip, a flash, sparks and a puff of air; the Imperial Crack adds a sonic
 * boom along the lash, a ring of dust running over the ground and an explosion of light.
 */
public final class WhipFx {
    private static final ResourceLocation TRAIL = FrustaMod.id("textures/fx/trail.png");
    private static final ResourceLocation RING = FrustaMod.id("textures/fx/ring.png");
    private static final ResourceLocation GLOW = FrustaMod.id("textures/fx/glow.png");
    private static final Random RANDOM = new Random();

    private record Ring(V3 pos, V3 axis, long start, int life, float radius, int color, boolean faceCamera) {
    }

    private record Flash(V3 pos, long start, int life, float size, int color) {
    }

    private static final List<Ring> RINGS = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();

    private WhipFx() {
    }

    public static void clear() {
        RINGS.clear();
        FLASHES.clear();
    }

    public static void tick() {
        long now = WhipAnims.ticks();
        RINGS.removeIf(r -> now - r.start() > r.life());
        FLASHES.removeIf(f -> now - f.start() > f.life());
    }

    /** A crack at {@code point}. */
    public static void crack(V3 point, WhipAnim anim, boolean imperial) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        long now = WhipAnims.ticks();
        WhipMaterial material = anim.material();
        V3 dir = anim.node(Whip.NODES - 1).sub(anim.node(Whip.NODES - 4)).normalize(new V3(0, 0, 1));
        if (FrustaConfig.CLIENT.trails.get()) {
            RINGS.add(new Ring(point, dir, now, imperial ? 10 : 6, imperial ? 4.5F : 1.6F, material.glow(), false));
            RINGS.add(new Ring(point, dir, now, imperial ? 7 : 4, imperial ? 2.6F : 0.9F, 0xFFFFFF, true));
            FLASHES.add(new Flash(point, now, imperial ? 5 : 3, imperial ? 3.2F : 1.1F, material.glow2()));
            if (imperial) {
                // a ring of shock running over the ground from the whipper's feet
                V3 feet = anim.node(0).add(0, -1.2, 0);
                RINGS.add(new Ring(groundUnder(level, feet), V3.UP, now, 14, 9.0F, material.glow(), false));
            }
        }
        if (!FrustaConfig.CLIENT.particles.get()) {
            return;
        }
        for (int i = 0; i < (imperial ? 30 : 12); i++) {
            V3 v = randomDir().scale(0.25 + RANDOM.nextDouble() * (imperial ? 0.6 : 0.35));
            level.addParticle(ParticleTypes.CRIT, point.x(), point.y(), point.z(), v.x(), v.y(), v.z());
        }
        for (int i = 0; i < (imperial ? 10 : 4); i++) {
            V3 v = randomDir().scale(0.05 + RANDOM.nextDouble() * 0.08);
            level.addParticle(ParticleTypes.POOF, point.x(), point.y(), point.z(), v.x(), v.y(), v.z());
        }
        ParticleOptions spark = switch (material) {
            case LEATHER -> ParticleTypes.ENCHANTED_HIT;
            case DIAMOND -> ParticleTypes.ELECTRIC_SPARK;
            case NETHERITE -> ParticleTypes.FLAME;
        };
        for (int i = 0; i < (imperial ? 24 : 10); i++) {
            V3 v = randomDir().scale(0.15 + RANDOM.nextDouble() * 0.3);
            level.addParticle(spark, point.x(), point.y(), point.z(), v.x(), v.y(), v.z());
        }
        if (material == WhipMaterial.NETHERITE) {
            for (int i = 0; i < (imperial ? 14 : 5); i++) {
                V3 v = randomDir().scale(0.1 + RANDOM.nextDouble() * 0.2);
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, point.x(), point.y(), point.z(), v.x(), v.y(), v.z());
            }
        }
        if (imperial) {
            level.addParticle(ParticleTypes.EXPLOSION_EMITTER, point.x(), point.y(), point.z(), 0, 0, 0);
            // the sonic boom runs back along the lash (not right in front of the camera, where it would fill the screen)
            Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
            for (int i = 1; i < Whip.NODES; i += 4) {
                V3 p = anim.node(i);
                if (camera.distanceToSqr(p.x(), p.y(), p.z()) > 2.5 * 2.5) {
                    level.addParticle(ParticleTypes.SONIC_BOOM, p.x(), p.y(), p.z(), 0, 0, 0);
                }
            }
            dustRing(level, anim.node(0));
        }
    }

    /** Dust kicked up from the ground in a circle around the whipper. */
    private static void dustRing(ClientLevel level, V3 hand) {
        V3 center = groundUnder(level, hand.add(0, -1.0, 0));
        BlockPos below = BlockPos.containing(center.x(), center.y() - 0.5, center.z());
        BlockState ground = level.getBlockState(below);
        if (ground.isAir() || ground.getRenderShape() == RenderShape.INVISIBLE) {
            return;
        }
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
        for (int i = 0; i < 60; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double r = 0.5 + RANDOM.nextDouble() * 2.5;
            level.addParticle(dust, center.x() + Math.cos(a) * r, center.y() + 0.1, center.z() + Math.sin(a) * r,
                    Math.cos(a) * 0.6, 0.25 + RANDOM.nextDouble() * 0.3, Math.sin(a) * 0.6);
        }
    }

    private static V3 groundUnder(ClientLevel level, V3 p) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < 4; dy++) {
            pos.set(p.x(), p.y() - dy, p.z());
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                return new V3(p.x(), pos.getY() + state.getCollisionShape(level, pos).max(net.minecraft.core.Direction.Axis.Y) + 0.05, p.z());
            }
        }
        return p;
    }

    private static V3 randomDir() {
        double y = RANDOM.nextDouble() * 2 - 1;
        double a = RANDOM.nextDouble() * Math.PI * 2;
        double r = Math.sqrt(1 - y * y);
        return new V3(Math.cos(a) * r, y, Math.sin(a) * r);
    }

    // =====================================================================================
    // Drawing (after the particles, additive: it glows)
    // =====================================================================================

    public static void render(PoseStack poseStack, Vec3 camera, float partialTick, Iterable<Map.Entry<Integer, WhipAnim>> anims) {
        if (!FrustaConfig.CLIENT.trails.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        double clock = WhipAnims.clock(partialTick);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        V3 cam = new V3(camera.x, camera.y, camera.z);

        RenderType trailType = RenderType.eyes(TRAIL);
        VertexConsumer trail = buffers.getBuffer(trailType);
        for (Map.Entry<Integer, WhipAnim> entry : anims) {
            drawTrail(trail, m, n, cam, clock, entry.getValue());
        }
        buffers.endBatch(trailType);

        RenderType ringType = RenderType.eyes(RING);
        VertexConsumer rings = buffers.getBuffer(ringType);
        for (Ring r : RINGS) {
            double age = (clock - r.start()) / r.life();
            if (age < 0 || age > 1) {
                continue;
            }
            float size = (float) (r.radius() * (0.25 + 0.75 * Math.sqrt(age)));
            float fade = (float) ((1 - age) * (1 - age));
            V3 axis = r.faceCamera() ? r.pos().sub(cam).normalize(V3.UP) : r.axis();
            quad(rings, m, n, r.pos().sub(cam), axis, size, r.color(), fade);
        }
        buffers.endBatch(ringType);

        RenderType glowType = RenderType.eyes(GLOW);
        VertexConsumer glow = buffers.getBuffer(glowType);
        for (Flash f : FLASHES) {
            double age = (clock - f.start()) / f.life();
            if (age < 0 || age > 1) {
                continue;
            }
            float fade = (float) (1 - age);
            V3 axis = f.pos().sub(cam).normalize(V3.UP);
            quad(glow, m, n, f.pos().sub(cam), axis, (float) (f.size() * (0.6 + 0.4 * age)), f.color(), fade);
            quad(glow, m, n, f.pos().sub(cam), axis, (float) (f.size() * 0.35), 0xFFFFFF, fade);
        }
        buffers.endBatch(glowType);
    }

    /** A ribbon through the tip's recent positions, facing the camera, fading towards its tail. */
    private static void drawTrail(VertexConsumer c, Matrix4f m, Matrix3f n, V3 cam, double clock, WhipAnim anim) {
        List<WhipAnim.TrailPoint> points = new ArrayList<>();
        for (WhipAnim.TrailPoint p : anim.trail()) {
            points.add(p);
        }
        if (points.size() < 2) {
            return;
        }
        int color = anim.material().glow();
        int color2 = anim.material().glow2();
        float width = 0.22F;
        V3 prevA = null;
        V3 prevB = null;
        float prevK = 0;
        int prevColor = color;
        for (int i = 0; i < points.size(); i++) {
            WhipAnim.TrailPoint p = points.get(i);
            float k = (float) Mth.clamp(1.0 - (clock - p.clock()) / 5.0, 0.0, 1.0);
            V3 next = points.get(Math.min(points.size() - 1, i + 1)).pos();
            V3 before = points.get(Math.max(0, i - 1)).pos();
            V3 along = next.sub(before).normalize(new V3(0, 1, 0));
            V3 toCam = cam.sub(p.pos()).normalize(V3.UP);
            V3 side = along.cross(toCam).normalize(new V3(1, 0, 0)).scale(width * (0.3 + 0.7 * k));
            V3 rel = p.pos().sub(cam);
            V3 a = rel.add(side);
            V3 b = rel.sub(side);
            int col = mix(color2, color, k);
            if (prevA != null && (k > 0.01F || prevK > 0.01F)) {
                vertex(c, m, n, prevA, 0, 0, prevColor, prevK);
                vertex(c, m, n, prevB, 1, 0, prevColor, prevK);
                vertex(c, m, n, b, 1, 1, col, k);
                vertex(c, m, n, a, 0, 1, col, k);
            }
            prevA = a;
            prevB = b;
            prevK = k;
            prevColor = col;
        }
    }

    /** A square of side {@code 2 * half} centred on {@code rel}, facing {@code axis}, both sides. */
    private static void quad(VertexConsumer c, Matrix4f m, Matrix3f n, V3 rel, V3 axis, float half, int color, float fade) {
        V3 u = axis.cross(Math.abs(axis.y()) > 0.9 ? new V3(1, 0, 0) : V3.UP).normalize(new V3(1, 0, 0)).scale(half);
        V3 v = axis.cross(u).normalize(new V3(0, 0, 1)).scale(half);
        V3 p0 = rel.sub(u).sub(v);
        V3 p1 = rel.add(u).sub(v);
        V3 p2 = rel.add(u).add(v);
        V3 p3 = rel.sub(u).add(v);
        vertex(c, m, n, p0, 0, 0, color, fade);
        vertex(c, m, n, p1, 1, 0, color, fade);
        vertex(c, m, n, p2, 1, 1, color, fade);
        vertex(c, m, n, p3, 0, 1, color, fade);
        vertex(c, m, n, p3, 0, 1, color, fade);
        vertex(c, m, n, p2, 1, 1, color, fade);
        vertex(c, m, n, p1, 1, 0, color, fade);
        vertex(c, m, n, p0, 0, 0, color, fade);
    }

    private static void vertex(VertexConsumer c, Matrix4f m, Matrix3f n, V3 p, float u, float v, int rgb, float k) {
        // Additive: the glow fades by getting darker.
        int r = (int) (((rgb >> 16) & 255) * k);
        int g = (int) (((rgb >> 8) & 255) * k);
        int b = (int) ((rgb & 255) * k);
        c.vertex(m, (float) p.x(), (float) p.y(), (float) p.z())
                .color(r, g, b, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0, 1, 0)
                .endVertex();
    }

    private static int mix(int a, int b, float k) {
        int r = (int) Mth.lerp(k, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(k, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(k, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }
}
