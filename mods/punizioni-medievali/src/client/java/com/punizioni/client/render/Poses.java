package com.punizioni.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.punizioni.data.Body;
import com.punizioni.data.Limb;
import com.punizioni.machine.Gallows;
import com.punizioni.machine.IronMaiden;
import com.punizioni.machine.MachineKind;
import com.punizioni.machine.PilloryPose;
import com.punizioni.machine.Pyre;
import com.punizioni.machine.RackGeometry;
import com.punizioni.machine.RatTable;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How bodies look in the machines and after: chained up for the lash, spread on the rack (and
 * stretched like rubber), kneeling at the block, tied to the stake, face down under the blade, spread
 * on the wheel, hanging from the noose, bent over in the pillory. Called at the end of PlayerModel.setupAnim (poses) and from
 * AvatarRenderer (whole-body rotations and offsets).
 */
public final class Poses {
    private Poses() {
    }

    private static final float PX = 0.9375F / 16.0F;

    /** The vanilla parts the BodyLayer replaces are hidden again every frame (the model is shared). */
    public static void apply(PlayerModel m, AvatarRenderState s) {
        reset(m);
        Visuals v = s.getData(Visuals.KEY);
        if (v == null) {
            return;
        }
        float t = v.time + v.seed % 100;
        if (v.kind != null) {
            // riding the machine: forget vanilla's sitting pose first
            for (ModelPart p : new ModelPart[]{m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) {
                p.xRot = 0.0F;
                p.yRot = 0.0F;
                p.zRot = 0.0F;
            }
            m.body.xRot = 0.0F;
            m.body.yRot = 0.0F;
            switch (v.kind) {
                case WHIPPING_POST -> chained(m, v, t);
                case IRON_MAIDEN -> maiden(m, v, t);
                case QUARTERING_RACK -> spread(m, v, t, RackGeometry.ARM_SPREAD, RackGeometry.LEG_SPREAD, true);
                case CHOPPING_BLOCK -> block(m, v);
                case RAT_TABLE -> table(m, v, t);
                case PYRE -> stake(m, v, t);
                case GUILLOTINE -> prone(m, v);
                case WHEEL -> spread(m, v, t, 0.85F, 0.35F, false);
                case GALLOWS -> hanged(m, v, t);
                case PILLORY -> pillory(m, v, t);
                default -> {
                }
            }
        }
        if (v.bound && v.kind == null) {
            tied(m, t);
        }
        if (v.stretch > 0.001F) {
            stretch(m, v.stretch);
        }
        // the BodyLayer draws these parts itself: cut short, bent the wrong way, or not at all
        Body b = v.body;
        hide(m.rightArm, m.rightSleeve, b.lacks(Limb.RIGHT_HAND) || b.brokenBone(Body.RIGHT_ARM) || b.torn());
        hide(m.leftArm, m.leftSleeve, b.lacks(Limb.LEFT_HAND) || b.brokenBone(Body.LEFT_ARM) || b.torn());
        hide(m.rightLeg, m.rightPants, b.lacks(Limb.RIGHT_FOOT) || b.brokenBone(Body.RIGHT_LEG) || b.torn());
        hide(m.leftLeg, m.leftPants, b.lacks(Limb.LEFT_FOOT) || b.brokenBone(Body.LEFT_LEG) || b.torn());
        if (b.headless()) {
            m.head.visible = false;
        }
        if (v.hood) {
            // the hood covers the hat layer
            m.hat.visible = false;
        }
    }

    private static void reset(PlayerModel m) {
        m.rightArm.skipDraw = false;
        m.leftArm.skipDraw = false;
        m.rightLeg.skipDraw = false;
        m.leftLeg.skipDraw = false;
        m.rightSleeve.skipDraw = false;
        m.leftSleeve.skipDraw = false;
        m.rightPants.skipDraw = false;
        m.leftPants.skipDraw = false;
        m.head.visible = true;
    }

    private static void hide(ModelPart limb, ModelPart overlay, boolean hide) {
        limb.skipDraw = hide;
        overlay.skipDraw = hide;
    }

    private static float tremble(float t, float speed, float amount) {
        return (Mth.sin(t * speed) + 0.5F * Mth.sin(t * speed * 2.3F + 1.0F)) * amount;
    }

    /**
     * The prisoner's rope, tied like a salami: arms pressed to the sides, legs together, rope wound
     * round all of it (BondsLayer); only the head moves, and the body sways a little.
     */
    private static void tied(PlayerModel m, float t) {
        float sway = Mth.sin(t * 0.07F) * 0.03F;
        for (ModelPart arm : new ModelPart[]{m.rightArm, m.leftArm}) {
            // pressed to the body: they lean with it (crouching)
            arm.xRot = m.body.xRot;
            arm.yRot = 0.0F;
            arm.zRot = 0.0F;
        }
        for (ModelPart leg : new ModelPart[]{m.rightLeg, m.leftLeg}) {
            leg.xRot = 0.0F;
            leg.yRot = 0.0F;
            leg.zRot = 0.0F;
        }
        m.body.zRot = sway;
        m.rightArm.zRot = sway;
        m.leftArm.zRot = sway;
    }

    /** Wrists chained high on the post; every lash makes the body arch and the head jerk back. */
    private static void chained(PlayerModel m, Visuals v, float t) {
        m.rightArm.xRot = -2.85F;
        m.rightArm.yRot = 0.0F;
        m.rightArm.zRot = -0.32F + tremble(t, 0.7F, 0.03F);
        m.leftArm.xRot = -2.85F;
        m.leftArm.yRot = 0.0F;
        m.leftArm.zRot = 0.32F + tremble(t + 3.0F, 0.7F, 0.03F);
        m.rightLeg.xRot = 0.05F + tremble(t, 0.4F, 0.04F);
        m.leftLeg.xRot = -0.05F - tremble(t, 0.4F, 0.04F);
        m.rightLeg.zRot = 0.08F;
        m.leftLeg.zRot = -0.08F;
        m.head.xRot = 0.35F;
        float k = (float) Math.exp(-v.sinceLash / 4.0F);
        m.body.xRot = -0.25F * k;
        m.head.xRot -= 0.9F * k;
        m.rightArm.xRot -= 0.15F * k;
        m.leftArm.xRot -= 0.15F * k;
    }

    private static void maiden(PlayerModel m, Visuals v, float t) {
        if (v.state == IronMaiden.CLOSING) {
            // trying to hold the doors back
            float push = Mth.clamp(v.age / 20.0F, 0.0F, 1.0F);
            m.rightArm.xRot = -1.3F * push + tremble(t, 1.1F, 0.08F);
            m.leftArm.xRot = -1.3F * push + tremble(t + 2.0F, 1.1F, 0.08F);
            m.rightArm.zRot = 0.5F * push;
            m.leftArm.zRot = -0.5F * push;
            m.head.yRot += tremble(t, 0.9F, 0.25F);
        } else if (v.state == IronMaiden.CLOSED) {
            m.head.xRot = 0.6F;
            m.rightArm.xRot = -0.3F + (v.alive ? tremble(t, 1.4F, 0.1F) : 0.0F);
            m.leftArm.xRot = -0.3F + (v.alive ? tremble(t + 1.0F, 1.4F, 0.1F) : 0.0F);
        } else {
            m.rightArm.xRot = -0.08F;
            m.leftArm.xRot = -0.08F;
            m.rightArm.zRot = 0.12F;
            m.leftArm.zRot = -0.12F;
        }
        m.rightLeg.xRot = 0.0F;
        m.leftLeg.xRot = 0.0F;
    }

    /** Spread-eagle (the rack, the wheel): arms over the head and out, legs apart. */
    private static void spread(PlayerModel m, Visuals v, float t, float arms, float legs, boolean pulled) {
        float pull = pulled ? v.stretch : 0.3F;
        m.rightArm.xRot = -Mth.PI;
        m.rightArm.yRot = 0.0F;
        m.rightArm.zRot = -arms + tremble(t, 1.2F, 0.02F + 0.05F * pull);
        m.leftArm.xRot = -Mth.PI;
        m.leftArm.yRot = 0.0F;
        m.leftArm.zRot = arms + tremble(t + 1.7F, 1.2F, 0.02F + 0.05F * pull);
        m.rightLeg.xRot = 0.0F;
        m.rightLeg.yRot = 0.0F;
        m.rightLeg.zRot = legs + tremble(t + 0.5F, 1.0F, 0.02F + 0.04F * pull);
        m.leftLeg.xRot = 0.0F;
        m.leftLeg.yRot = 0.0F;
        m.leftLeg.zRot = -legs + tremble(t + 2.5F, 1.0F, 0.02F + 0.04F * pull);
        m.body.xRot = 0.0F;
        if (v.alive) {
            m.head.xRot = -0.25F + tremble(t, 0.6F, 0.15F * pull);
            m.head.yRot = Mth.clamp(m.head.yRot, -0.9F, 0.9F) + tremble(t, 0.8F, 0.3F * pull);
        } else {
            m.head.xRot = 0.0F;
            m.head.yRot = 0.6F;
        }
    }

    /** Longer and longer, thinner and thinner. */
    private static void stretch(PlayerModel m, float s) {
        float arms = RackGeometry.armScale(s);
        float legs = RackGeometry.legScale(s);
        float body = RackGeometry.bodyScale(s);
        m.rightArm.yScale = arms;
        m.leftArm.yScale = arms;
        m.rightLeg.yScale = legs;
        m.leftLeg.yScale = legs;
        m.body.yScale = body;
        float thin = 1.0F / Mth.sqrt(arms);
        m.rightArm.xScale = m.rightArm.zScale = thin;
        m.leftArm.xScale = m.leftArm.zScale = thin;
        float thinLegs = 1.0F / Mth.sqrt(legs);
        m.rightLeg.xScale = m.rightLeg.zScale = thinLegs;
        m.leftLeg.xScale = m.leftLeg.zScale = thinLegs;
        m.body.xScale = m.body.zScale = 1.0F / Mth.sqrt(body);
        // the body grows down from the neck: the arms and legs follow it
        m.rightArm.y = 2.0F * body;
        m.leftArm.y = 2.0F * body;
        m.rightLeg.y = 12.0F * body;
        m.leftLeg.y = 12.0F * body;
    }

    /** Kneeling at the block with a hand on it, or sitting with a foot on it. */
    private static void block(PlayerModel m, Visuals v) {
        Limb limb = Limb.ALL[Math.floorMod(v.choice, Limb.ALL.length)];
        if (limb.hand()) {
            m.rightLeg.xRot = 1.45F;
            m.leftLeg.xRot = 1.45F;
            m.rightLeg.zRot = 0.05F;
            m.leftLeg.zRot = -0.05F;
            m.body.xRot = 0.45F;
            m.head.xRot = 0.2F;
            ModelPart on = limb.right() ? m.rightArm : m.leftArm;
            ModelPart off = limb.right() ? m.leftArm : m.rightArm;
            // reaching down onto the block (its top is 0.45 blocks up)
            on.xRot = -1.05F;
            on.yRot = 0.0F;
            on.zRot = limb.right() ? 0.25F : -0.25F;
            off.xRot = 0.35F;
            off.zRot = limb.right() ? -0.15F : 0.15F;
            // the body leans forward: the arms and the head move with it
            m.head.z = -3.0F;
            m.rightArm.z = -2.0F;
            m.leftArm.z = -2.0F;
        } else {
            ModelPart on = limb.right() ? m.rightLeg : m.leftLeg;
            ModelPart off = limb.right() ? m.leftLeg : m.rightLeg;
            // sitting on the ground, the leg raised onto the block
            on.xRot = -2.3F;
            on.zRot = 0.0F;
            off.xRot = -1.5F;
            off.zRot = limb.right() ? -0.25F : 0.25F;
            m.rightArm.xRot = 0.65F;
            m.leftArm.xRot = 0.65F;
            m.rightArm.zRot = 0.2F;
            m.leftArm.zRot = -0.2F;
            m.body.xRot = -0.15F;
        }
    }

    private static void table(PlayerModel m, Visuals v, float t) {
        float k = v.state == RatTable.SWARM && v.alive ? 1.0F : 0.15F;
        m.rightArm.xRot = tremble(t, 1.6F, 0.12F * k);
        m.leftArm.xRot = tremble(t + 2.0F, 1.6F, 0.12F * k);
        m.rightArm.zRot = 0.1F + tremble(t, 1.9F, 0.1F * k);
        m.leftArm.zRot = -0.1F - tremble(t + 1.0F, 1.9F, 0.1F * k);
        m.rightLeg.xRot = tremble(t + 0.5F, 1.4F, 0.15F * k);
        m.leftLeg.xRot = tremble(t + 3.0F, 1.4F, 0.15F * k);
        m.rightLeg.zRot = 0.04F;
        m.leftLeg.zRot = -0.04F;
        m.head.xRot = -0.2F + tremble(t, 0.9F, 0.15F * k);
        m.head.yRot = Mth.clamp(m.head.yRot, -0.8F, 0.8F) + tremble(t, 1.3F, 0.5F * k);
    }

    /** Hands tied behind the post; the fire makes them writhe and throw the head back. */
    private static void stake(PlayerModel m, Visuals v, float t) {
        float k = v.state == Pyre.BURNING && v.alive ? Mth.clamp(v.age / 60.0F, 0.2F, 1.0F) : 0.1F;
        m.rightArm.xRot = 0.55F + tremble(t, 1.5F, 0.05F * k);
        m.rightArm.zRot = -0.12F;
        m.leftArm.xRot = 0.55F + tremble(t + 1.0F, 1.5F, 0.05F * k);
        m.leftArm.zRot = 0.12F;
        m.rightLeg.xRot = tremble(t, 1.7F, 0.2F * k);
        m.leftLeg.xRot = tremble(t + 2.0F, 1.7F, 0.2F * k);
        m.body.yRot = tremble(t, 0.8F, 0.12F * k);
        m.head.xRot = -0.55F * k + tremble(t, 1.1F, 0.2F * k);
        m.head.yRot = Mth.clamp(m.head.yRot, -0.7F, 0.7F) + tremble(t, 1.4F, 0.4F * k);
    }

    /** Face down on the plank, hands behind the back, the head out through the lunette. */
    private static void prone(PlayerModel m, Visuals v) {
        m.rightArm.xRot = 0.15F;
        m.rightArm.zRot = -0.05F;
        m.leftArm.xRot = 0.15F;
        m.leftArm.zRot = 0.05F;
        m.rightLeg.xRot = 0.0F;
        m.leftLeg.xRot = 0.0F;
        m.head.xRot = Mth.clamp(m.head.xRot, -0.3F, 0.6F);
        m.head.yRot = Mth.clamp(m.head.yRot, -0.4F, 0.4F);
    }

    /**
     * Bent over behind the pillory's board: the head out in front of it, the arms through its side
     * holes (PilloryPose). The head turns a little in its hole; a fresh splat of dung makes it jerk
     * back and shake, the hands writhe.
     */
    private static void pillory(PlayerModel m, Visuals v, float t) {
        float k = v.a;
        m.body.xRot = PilloryPose.BEND * k;
        m.body.y = PilloryPose.NECK_Y * k;
        m.body.z = PilloryPose.NECK_Z * k;
        float flinch = v.sinceSplat < 14.0F && v.alive ? 1.0F - v.sinceSplat / 14.0F : 0.0F;
        m.head.y = Mth.lerp(k, 0.0F, PilloryPose.HEAD_Y);
        m.head.z = Mth.lerp(k, 0.0F, PilloryPose.HEAD_Z);
        m.head.xRot = Mth.clamp(m.head.xRot, -PilloryPose.HEAD_PITCH, PilloryPose.HEAD_PITCH) - 0.35F * flinch;
        m.head.yRot = Mth.clamp(m.head.yRot, -PilloryPose.HEAD_YAW, PilloryPose.HEAD_YAW) + Mth.sin(t * 2.6F) * 0.18F * flinch;
        m.head.zRot = 0.0F;
        float writhe = tremble(t, 1.9F, 0.04F + 0.12F * flinch);
        m.rightArm.x = -5.0F;
        m.rightArm.y = Mth.lerp(k, m.rightArm.y, PilloryPose.SHOULDER_Y);
        m.rightArm.z = PilloryPose.SHOULDER_Z * k;
        m.rightArm.xRot = PilloryPose.ARM_X_ROT * k;
        m.rightArm.yRot = PilloryPose.ARM_SPREAD * k;
        m.rightArm.zRot = writhe;
        m.leftArm.x = 5.0F;
        m.leftArm.y = Mth.lerp(k, m.leftArm.y, PilloryPose.SHOULDER_Y);
        m.leftArm.z = PilloryPose.SHOULDER_Z * k;
        m.leftArm.xRot = PilloryPose.ARM_X_ROT * k;
        m.leftArm.yRot = -PilloryPose.ARM_SPREAD * k;
        m.leftArm.zRot = -writhe;
        m.rightLeg.xRot = 0.0F;
        m.leftLeg.xRot = 0.0F;
        m.rightLeg.zRot = 0.04F;
        m.leftLeg.zRot = -0.04F;
    }

    private static void hanged(PlayerModel m, Visuals v, float t) {
        m.rightArm.xRot = 0.32F;
        m.rightArm.zRot = 0.1F;
        m.leftArm.xRot = 0.32F;
        m.leftArm.zRot = -0.1F;
        if (v.state == Gallows.HANGING) {
            // the legs kick, less and less
            float kick = v.alive ? Math.max(0.0F, 1.0F - v.age / 120.0F) : 0.0F;
            m.rightLeg.xRot = Mth.sin(t * 0.9F) * 0.7F * kick;
            m.leftLeg.xRot = Mth.sin(t * 0.9F + 2.1F) * 0.7F * kick;
            m.head.xRot = 0.35F;
            m.head.zRot = 0.45F;
            m.head.yRot = 0.0F;
        } else {
            m.head.xRot = 0.15F;
            m.rightLeg.xRot = 0.0F;
            m.leftLeg.xRot = 0.0F;
        }
    }

    // ------------------------------------------------------------------ the whole body

    /** Rotations around the whole body, after vanilla's (AvatarRenderer.setupRotations). */
    public static void rotations(AvatarRenderState s, PoseStack poseStack) {
        rotations(s.getData(Visuals.KEY), poseStack);
    }

    /** The same, for the victim's own camera (VictimCamera). */
    public static void rotations(@Nullable Visuals v, PoseStack poseStack) {
        if (v == null || v.kind == null) {
            return;
        }
        float body = RackGeometry.bodyScale(v.stretch);
        float hips = (24.0F - 12.0F * body) * PX;
        switch (v.kind) {
            case QUARTERING_RACK, RAT_TABLE -> {
                // on the back, the hips where the machine holds them, the head towards its back end
                poseStack.translate(0.0F, 0.13F, 0.0F);
                poseStack.rotateDegrees(Axis.XP, 90.0F);
                poseStack.translate(0.0F, -hips, 0.0F);
            }
            case GUILLOTINE -> {
                // face down, the head forward into the lunette
                poseStack.translate(0.0F, 0.13F, 0.0F);
                poseStack.rotateDegrees(Axis.XP, -90.0F);
                poseStack.translate(0.0F, -hips, 0.0F);
            }
            case WHEEL -> {
                poseStack.translate(0.0F, hips, 0.0F);
                poseStack.rotateDegrees(Axis.ZP, v.b);
                poseStack.translate(0.0F, -hips, 0.0F);
            }
            case GALLOWS -> {
                if (v.state == Gallows.HANGING) {
                    // the body swings from the beam, like the rope
                    float top = (float) (Gallows.BEAM - (Gallows.FLOOR - v.a));
                    poseStack.translate(0.0F, top, 0.0F);
                    poseStack.rotateDegrees(Axis.XP, MachineRenderer.swayX(v.time, v.age));
                    poseStack.rotateDegrees(Axis.ZP, MachineRenderer.swayZ(v.time));
                    poseStack.rotateDegrees(Axis.YP, Mth.sin(v.time * 0.013F) * 35.0F * Math.min(1.0F, v.age / 40.0F));
                    poseStack.translate(0.0F, -top, 0.0F);
                }
            }
            default -> {
            }
        }
    }

    /** Lowered (kneeling, sitting) or lifted (long stretched legs) on top of vanilla's offset. */
    public static Vec3 offset(AvatarRenderState s) {
        Visuals v = s.getData(Visuals.KEY);
        Vec3 base = offset(v);
        if (v != null && v.bound && v.kind == null && s.walkAnimationSpeed > 0.05F) {
            // tied like a salami, legs and all: it hops
            float hop = Math.abs(Mth.sin(s.walkAnimationPos * 1.1F)) * Math.min(1.0F, s.walkAnimationSpeed * 2.0F) * 0.13F;
            return base.add(0.0, hop, 0.0);
        }
        return base;
    }

    public static Vec3 offset(@Nullable Visuals v) {
        if (v == null) {
            return Vec3.ZERO;
        }
        if (v.kind == MachineKind.CHOPPING_BLOCK) {
            boolean hand = Limb.ALL[Math.floorMod(v.choice, Limb.ALL.length)].hand();
            return new Vec3(0.0, hand ? -0.42 : -0.62, 0.0);
        }
        if (v.kind == null && v.stretch > 0.001F) {
            // standing on legs that are much longer than they should be
            float lift = (12.0F * (RackGeometry.bodyScale(v.stretch) - 1.0F) + 12.0F * (RackGeometry.legScale(v.stretch) - 1.0F)) * PX;
            return new Vec3(0.0, lift, 0.0);
        }
        return Vec3.ZERO;
    }
}
