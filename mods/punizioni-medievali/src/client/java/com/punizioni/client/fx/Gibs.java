package com.punizioni.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.punizioni.ModParticles;
import com.punizioni.client.render.BodyLayer;
import com.punizioni.client.render.Remains;
import com.punizioni.client.render.Ropes;
import com.punizioni.data.Limb;
import com.punizioni.entity.DraftHorse;
import com.punizioni.machine.ChoppingBlock;
import com.punizioni.machine.Machine;
import com.punizioni.machine.RackGeometry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Pieces of bodies, on the client only: the hand or the foot the axe takes flies off the block, the
 * arms and legs the horses tear off are dragged away behind them on their ropes. They wear the
 * victim's skin, leave a trail of blood, tumble, bounce, lie on the ground a minute and sink away.
 */
public final class Gibs {
    private Gibs() {
    }

    private static final int LIFE = 20 * 60;
    private static final RandomSource RANDOM = RandomSource.create();

    /** What a piece is: a hand, a foot, a whole arm or leg. */
    private enum Shape {
        HAND, FOOT, ARM, LEG
    }

    private static final class Gib {
        Shape shape;
        boolean right;
        UUID owner;
        Vec3 pos;
        Vec3 oldPos;
        Vec3 vel;
        Quaternionf rot = new Quaternionf();
        Quaternionf oldRot = new Quaternionf();
        float spinX;
        float spinY;
        float spinZ;
        int age;
        boolean resting;
        /** Dragged by a horse (its entity id), or -1. */
        int horse = -1;
    }

    private static final List<Gib> GIBS = new ArrayList<>();

    /** Pieces in the skin of each shape: [wide/slim][right/left][shape] base and overlay, and the cap of raw flesh. */
    private static ModelPart[][][] base;
    private static ModelPart[][][] over;
    private static ModelPart[][][] caps;

    public static void reset() {
        GIBS.clear();
    }

    // ------------------------------------------------------------------ spawning

    /** The axe falls: the hand or the foot jumps off the far side of the block. */
    public static void chop(ClientLevel level, int machineId, int victimId, int limbIndex) {
        if (!(level.getEntity(machineId) instanceof Machine m)) {
            return;
        }
        Entity victim = level.getEntity(victimId);
        Limb limb = Limb.ALL[Math.floorMod(limbIndex, Limb.ALL.length)];
        Gib g = new Gib();
        g.shape = limb.hand() ? Shape.HAND : Shape.FOOT;
        g.right = limb.right();
        g.owner = victim != null ? victim.getUUID() : null;
        g.pos = m.local(ChoppingBlock.cutRight(limb) * 1.4, ChoppingBlock.TOP + 0.15, ChoppingBlock.cutForward(limb) - 0.15);
        Vec3 away = m.forward().scale(-0.12 - RANDOM.nextFloat() * 0.08);
        Vec3 side = m.right().scale((limb.right() ? -1.0 : 1.0) * (0.04 + RANDOM.nextFloat() * 0.06));
        g.vel = away.add(side).add(0.0, 0.32 + RANDOM.nextFloat() * 0.12, 0.0);
        spin(g, 0.6F);
        add(g);
    }

    /** The horses tear the limbs off: each one stays on its rope and is dragged after its horse. */
    public static void tear(ClientLevel level, int machineId, int victimId) {
        if (!(level.getEntity(machineId) instanceof Machine m)) {
            return;
        }
        Entity victim = level.getEntity(victimId);
        List<DraftHorse> horses = level.getEntitiesOfClass(DraftHorse.class, new AABB(m.blockPosition()).inflate(14.0));
        for (int limb = 0; limb < 4; limb++) {
            double[] a = RackGeometry.anchor(limb, 1.0F);
            double[] p = RackGeometry.pull(limb);
            Gib g = new Gib();
            g.shape = limb < 2 ? Shape.ARM : Shape.LEG;
            g.right = limb % 2 == 0;
            g.owner = victim != null ? victim.getUUID() : null;
            g.pos = m.local(a[0], RackGeometry.TOP + 0.15, a[1]);
            Vec3 dir = m.local(p[0], 0.0, p[1]).subtract(m.position());
            g.vel = dir.scale(0.5).add(0.0, 0.28, 0.0);
            spin(g, 0.5F);
            // the horse that pulled this limb: the nearest one along its direction
            Vec3 expected = g.pos.add(dir.scale(6.0));
            DraftHorse best = null;
            double bestD = 9.0 * 9.0;
            for (DraftHorse h : horses) {
                double d = h.position().distanceToSqr(expected);
                if (d < bestD) {
                    bestD = d;
                    best = h;
                }
            }
            g.horse = best != null ? best.getId() : -1;
            add(g);
        }
    }

    private static void spin(Gib g, float speed) {
        g.spinX = (RANDOM.nextFloat() - 0.5F) * speed;
        g.spinY = (RANDOM.nextFloat() - 0.5F) * speed;
        g.spinZ = (RANDOM.nextFloat() - 0.5F) * speed;
        g.rot.rotationXYZ(RANDOM.nextFloat() * 6.28F, RANDOM.nextFloat() * 6.28F, RANDOM.nextFloat() * 6.28F);
        g.oldRot.set(g.rot);
    }

    private static void add(Gib g) {
        g.oldPos = g.pos;
        GIBS.add(g);
        while (GIBS.size() > 40) {
            GIBS.remove(0);
        }
    }

    // ------------------------------------------------------------------ physics

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            GIBS.clear();
            return;
        }
        Iterator<Gib> it = GIBS.iterator();
        while (it.hasNext()) {
            Gib g = it.next();
            if (++g.age > LIFE) {
                it.remove();
                continue;
            }
            g.oldPos = g.pos;
            g.oldRot.set(g.rot);
            Entity horse = g.horse >= 0 ? level.getEntity(g.horse) : null;
            if (horse != null && !horse.isRemoved() && g.age > 4) {
                // on the rope: pulled towards a point behind the horse, dragged on the ground
                Vec3 back = horse.position().add(horse.getForward().multiply(1.0, 0.0, 1.0).normalize().scale(-1.9));
                Vec3 to = back.subtract(g.pos);
                double dist = to.length();
                if (dist > 0.4) {
                    g.vel = g.vel.add(to.scale(0.12 * Math.min(1.0, dist / 2.0)));
                }
                g.vel = g.vel.multiply(0.8, 1.0, 0.8);
                if (g.vel.horizontalDistanceSqr() > 0.004 && g.age % 2 == 0) {
                    level.addParticle(ModParticles.BLOOD, g.pos.x, g.pos.y + 0.05, g.pos.z, 0.0, 0.02, 0.0);
                }
            } else if (horse == null && g.horse >= 0) {
                g.horse = -1;
            }
            if (!g.resting) {
                g.vel = g.vel.add(0.0, -0.045, 0.0).scale(0.985);
                Vec3 next = g.pos.add(g.vel);
                double ground = ground(level, next);
                if (next.y < ground + 0.06) {
                    next = new Vec3(next.x, ground + 0.06, next.z);
                    if (g.vel.y < -0.12) {
                        // a thud and a splash
                        for (int i = 0; i < 4; i++) {
                            level.addParticle(ModParticles.BLOOD, next.x, next.y + 0.05, next.z, (RANDOM.nextFloat() - 0.5F) * 0.15,
                                    0.08 + RANDOM.nextFloat() * 0.1, (RANDOM.nextFloat() - 0.5F) * 0.15);
                        }
                    }
                    g.vel = new Vec3(g.vel.x * 0.55, -g.vel.y * 0.3, g.vel.z * 0.55);
                    g.spinX *= 0.6F;
                    g.spinY *= 0.6F;
                    g.spinZ *= 0.6F;
                    if (g.vel.lengthSqr() < 0.0009 && g.horse < 0) {
                        g.resting = true;
                        settle(g);
                    }
                }
                g.pos = next;
                g.rot.rotateXYZ(g.spinX, g.spinY, g.spinZ);
                if (g.age % 3 == 0 && g.vel.lengthSqr() > 0.01) {
                    level.addParticle(ModParticles.BLOOD, g.pos.x, g.pos.y, g.pos.z, 0.0, 0.0, 0.0);
                }
            } else if (g.age % 25 == 0 && g.age < 200) {
                // it keeps bleeding for a while
                level.addParticle(ModParticles.BLOOD, g.pos.x, g.pos.y + 0.05, g.pos.z, 0.0, 0.0, 0.0);
            }
            if (g.horse >= 0) {
                g.resting = false;
            }
        }
    }

    /** Lying on the ground: the long side down. */
    private static void settle(Gib g) {
        // keep the yaw it had, lay it flat on its side
        Vec3 up = new Vec3(g.rot.transform(new org.joml.Vector3f(0.0F, 1.0F, 0.0F)));
        float yaw = (float) Math.atan2(up.x, up.z);
        g.rot.rotationY(yaw).rotateX(Mth.HALF_PI).rotateY(RANDOM.nextFloat() * 6.28F);
    }

    private static double ground(ClientLevel level, Vec3 p) {
        BlockPos pos = BlockPos.containing(p.x, p.y, p.z);
        for (int i = 0; i < 3; i++) {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            }
            pos = pos.below();
        }
        return pos.getY() + 1.0 - 3.0;
    }

    // ------------------------------------------------------------------ drawing

    private static void build() {
        if (base != null) {
            return;
        }
        base = new ModelPart[2][2][4];
        over = new ModelPart[2][2][4];
        caps = new ModelPart[2][2][4];
        // right arm, left arm, right leg, left leg: texture offsets of the base and the overlay layer
        int[][] arm = {{40, 16, 40, 32}, {32, 48, 48, 48}};
        int[][] leg = {{0, 16, 0, 32}, {16, 48, 0, 48}};
        for (int slim = 0; slim < 2; slim++) {
            for (int side = 0; side < 2; side++) {
                float aw = slim == 1 ? 3.0F : 4.0F;
                int[] a = arm[side];
                int[] l = leg[side];
                // a hand: the last 4 pixels of the arm, cut at the wrist (the top)
                base[slim][side][0] = BodyLayer.box(a[0], a[1] + 8, -aw / 2.0F, -2.0F, -2.0F, aw, 4.0F, 4.0F, 0.0F);
                over[slim][side][0] = BodyLayer.box(a[2], a[3] + 8, -aw / 2.0F, -2.0F, -2.0F, aw, 4.0F, 4.0F, 0.25F);
                caps[slim][side][0] = BodyLayer.cap(-aw / 2.0F, -2.6F, -2.0F, aw, 0.7F, 4.0F);
                base[slim][side][1] = BodyLayer.box(l[0], l[1] + 8, -2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, 0.0F);
                over[slim][side][1] = BodyLayer.box(l[2], l[3] + 8, -2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, 0.25F);
                caps[slim][side][1] = BodyLayer.cap(-2.0F, -2.6F, -2.0F, 4.0F, 0.7F, 4.0F);
                // whole arms and legs, torn at the shoulder and the hip (the top)
                base[slim][side][2] = BodyLayer.box(a[0], a[1], -aw / 2.0F, -6.0F, -2.0F, aw, 12.0F, 4.0F, 0.0F);
                over[slim][side][2] = BodyLayer.box(a[2], a[3], -aw / 2.0F, -6.0F, -2.0F, aw, 12.0F, 4.0F, 0.25F);
                caps[slim][side][2] = BodyLayer.cap(-aw / 2.0F, -6.6F, -2.0F, aw, 0.7F, 4.0F);
                base[slim][side][3] = BodyLayer.box(l[0], l[1], -2.0F, -6.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0.0F);
                over[slim][side][3] = BodyLayer.box(l[2], l[3], -2.0F, -6.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0.25F);
                caps[slim][side][3] = BodyLayer.cap(-2.0F, -6.6F, -2.0F, 4.0F, 0.7F, 4.0F);
            }
        }
    }

    public static void submit(LevelRenderContext context) {
        if (GIBS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        build();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack poseStack = context.poseStack();
        for (Gib g : GIBS) {
            Vec3 p = g.oldPos.lerp(g.pos, partial);
            float sink = g.age > LIFE - 40 ? (g.age - (LIFE - 40) + partial) / 40.0F * 0.3F : 0.0F;
            PlayerSkin skin = Remains.skin(g.owner);
            int slim = skin.model() == PlayerModelType.SLIM && (g.shape == Shape.HAND || g.shape == Shape.ARM) ? 1 : 0;
            int side = g.right ? 0 : 1;
            int shape = g.shape.ordinal();
            int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(p));
            RenderType type = RenderTypes.entityTranslucent(skin.body().texturePath());
            poseStack.pushPose();
            poseStack.translate(p.x - camera.x, p.y - camera.y - sink, p.z - camera.z);
            poseStack.rotate(new Quaternionf(g.oldRot).slerp(g.rot, partial));
            poseStack.scale(-0.9375F / 16.0F, -0.9375F / 16.0F, 0.9375F / 16.0F);
            context.submitNodeCollector().submitModelPart(base[slim][side][shape], poseStack, type, light, OverlayTexture.NO_OVERLAY, null);
            context.submitNodeCollector().submitModelPart(over[slim][side][shape], poseStack, type, light, OverlayTexture.NO_OVERLAY, null);
            context.submitNodeCollector().submitModelPart(caps[slim][side][shape], poseStack, BodyLayer.STUMP_TYPE, light, OverlayTexture.NO_OVERLAY,
                    null);
            poseStack.popPose();
            Entity horse = g.horse >= 0 ? level.getEntity(g.horse) : null;
            if (horse != null) {
                // the rope from the horse's harness to the torn limb
                Vec3 h = horse.getPosition(partial).add(0.0, 0.95, 0.0);
                poseStack.pushPose();
                Ropes.rope(context.submitNodeCollector(), poseStack, camera, camera, Ropes.sag(h, p, 0.15, 8), 0.06F, light);
                poseStack.popPose();
            }
        }
    }
}
