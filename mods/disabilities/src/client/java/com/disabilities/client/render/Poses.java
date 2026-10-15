package com.disabilities.client.render;

import com.disabilities.condition.Condition;
import com.disabilities.gesture.Gesture;
import com.disabilities.util.Noise;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;

/**
 * Changes the player model's pose for the conditions everybody can see: sitting in the wheelchair,
 * the crutch gait and the missing leg, the missing arm, a big body, tremors, gestures, a hand
 * scratching a confused head, panting, a hand pressed on a wound.
 */
public final class Poses {
    private Poses() {
    }

    /** True when the player is doing something that already has its own pose (riding, swimming, gliding, lying). */
    private static boolean special(AvatarRenderState s) {
        return s.isPassenger || s.isFallFlying || s.swimAmount > 0.0F || s.pose == Pose.SLEEPING || s.isAutoSpinAttack
                || s.isVisuallySwimming;
    }

    /** Arms not busy with an item or an attack. */
    private static boolean freeArm(AvatarRenderState s, boolean right) {
        HumanoidModel.ArmPose pose = right ? s.rightArmPose : s.leftArmPose;
        return s.swingAnimation <= 0.0F && !s.isUsingItem && (pose == HumanoidModel.ArmPose.EMPTY || pose == HumanoidModel.ArmPose.ITEM);
    }

    /** Crutch angle (both crutches together), from the walking cycle. */
    public static float crutchSwing(AvatarRenderState s) {
        float speed = Math.min(1.0F, s.walkAnimationSpeed * 1.6F);
        return Mth.sin(s.walkAnimationPos * 0.6662F) * 0.5F * speed;
    }

    public static void apply(PlayerModel m, AvatarRenderState s) {
        Visuals v = s.getData(Visuals.KEY);
        if (v == null || s.isSpectator) {
            return;
        }
        boolean special = special(s);
        float t = s.ageInTicks;
        boolean rightFree = freeArm(s, true);
        boolean leftFree = freeArm(s, false);

        if (v.has(Condition.OBESITY)) {
            m.body.xScale = 1.42F;
            m.body.zScale = 1.6F;
            m.body.z -= 0.6F;
            m.rightArm.x -= 1.7F;
            m.leftArm.x += 1.7F;
            m.rightArm.zRot += 0.12F;
            m.leftArm.zRot -= 0.12F;
            m.rightLeg.x -= 0.6F;
            m.leftLeg.x += 0.6F;
            m.rightLeg.xScale = m.leftLeg.xScale = 1.22F;
            m.rightLeg.zScale = m.leftLeg.zScale = 1.25F;
        }

        if (v.has(Condition.WHEELCHAIR) && !special) {
            m.rightLeg.xRot = -0.95F;
            m.leftLeg.xRot = -0.95F;
            m.rightLeg.yRot = 0.08F;
            m.leftLeg.yRot = -0.08F;
            m.rightLeg.zRot = 0.0F;
            m.leftLeg.zRot = 0.0F;
            float speed = Math.min(1.0F, s.walkAnimationSpeed * 1.5F);
            float push = Mth.sin(s.walkAnimationPos * 0.45F);
            float armX = Mth.lerp(speed, -0.35F, -0.2F + push * 0.55F);
            if (rightFree) {
                m.rightArm.xRot = armX;
                m.rightArm.zRot = 0.2F;
            }
            if (leftFree) {
                m.leftArm.xRot = armX;
                m.leftArm.zRot = -0.2F;
            }
        }

        if (v.has(Condition.ONE_LEG)) {
            m.leftLeg.yScale = 0.42F;
            if (!special) {
                float swing = crutchSwing(s);
                m.leftLeg.xRot = 0.18F;
                m.leftLeg.yRot = 0.0F;
                m.rightLeg.xRot = swing * 0.9F;
                if (rightFree) {
                    m.rightArm.xRot = -0.22F - swing * 0.85F;
                    m.rightArm.zRot = 0.14F;
                }
                if (leftFree) {
                    m.leftArm.xRot = -0.22F - swing * 0.85F;
                    m.leftArm.zRot = -0.14F;
                }
            }
        }

        if (v.has(Condition.ONE_ARM)) {
            ModelPart arm = v.missingLeftArm ? m.leftArm : m.rightArm;
            arm.yScale = 0.3F;
            arm.xRot = 0.0F;
            arm.yRot = 0.0F;
            arm.zRot = v.missingLeftArm ? -0.08F : 0.08F;
        }

        if (v.has(Condition.PARKINSON)) {
            long seed = v.seed;
            float k = v.frozen ? 1.6F : 1.0F;
            m.head.xRot += Noise.tremor(seed, t * 0.55F) * 0.05F * k;
            m.head.yRot += Noise.tremor(seed + 1, t * 0.55F) * 0.06F * k;
            m.rightArm.xRot += Noise.tremor(seed + 2, t * 0.6F) * 0.16F * k;
            m.rightArm.zRot += Noise.tremor(seed + 3, t * 0.6F) * 0.07F * k;
            m.leftArm.xRot += Noise.tremor(seed + 4, t * 0.6F) * 0.16F * k;
            m.leftArm.zRot += Noise.tremor(seed + 5, t * 0.6F) * 0.07F * k;
            if (v.frozen && !special) {
                m.rightLeg.xRot = 0.05F;
                m.leftLeg.xRot = -0.05F;
                m.body.xRot += 0.12F;
            }
        }

        if (v.has(Condition.VERTIGO) && !special) {
            float sway = Mth.sin(t * 0.08F) * 0.12F;
            if (rightFree) {
                m.rightArm.zRot += 0.22F + sway;
            }
            if (leftFree) {
                m.leftArm.zRot -= 0.22F - sway;
            }
        }

        if ((v.breathless || v.winded) && !special) {
            m.head.xRot += 0.15F + Mth.sin(t * 0.5F) * 0.12F;
            if (rightFree) {
                m.rightArm.xRot = -0.55F;
            }
            if (leftFree) {
                m.leftArm.xRot = -0.55F;
            }
        }

        if (v.bleeding && leftFree && !special && !v.has(Condition.ONE_ARM)) {
            m.leftArm.xRot = -0.85F;
            m.leftArm.yRot = 0.55F;
            m.leftArm.zRot = 0.1F;
        }

        if (v.confused && rightFree && !special) {
            m.rightArm.xRot = -2.55F;
            m.rightArm.yRot = -0.2F;
            m.rightArm.zRot = 0.35F + Mth.sin(t * 1.3F) * 0.12F;
            m.head.zRot += 0.18F;
        }

        if (v.gesture != null && !special) {
            gesture(m, s, v, rightFree, leftFree);
        }
    }

    private static void gesture(PlayerModel m, AvatarRenderState s, Visuals v, boolean rightFree, boolean leftFree) {
        float g = v.gestureTime;
        float k = Math.min(1.0F, g / 4.0F) * Math.min(1.0F, Math.max(0.0F, (Gesture.DURATION - g) / 6.0F));
        switch (v.gesture) {
            case HELLO -> {
                if (rightFree) {
                    m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -2.75F);
                    m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, 0.35F + Mth.sin(g * 0.9F) * 0.35F);
                }
            }
            case YES -> m.head.xRot += k * (0.15F + Mth.sin(g * 0.9F) * 0.35F);
            case NO -> m.head.yRot += k * Mth.sin(g * 0.9F) * 0.55F;
            case COME -> {
                if (rightFree) {
                    m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -1.35F + Mth.sin(g * 0.7F) * 0.4F);
                    m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, -0.15F);
                }
            }
            case HELP -> {
                float wave = Mth.sin(g * 0.8F) * 0.3F;
                if (rightFree) {
                    m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -2.9F);
                    m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, 0.4F + wave);
                }
                if (leftFree) {
                    m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -2.9F);
                    m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, -0.4F + wave);
                }
            }
            case DANGER -> {
                float pulse = Mth.sin(g * 0.6F) * 0.1F;
                if (rightFree) {
                    m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -1.6F + pulse);
                    m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, -0.6F);
                }
                if (leftFree) {
                    m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -1.6F + pulse);
                    m.leftArm.yRot = Mth.lerp(k, m.leftArm.yRot, 0.6F);
                }
            }
            case THANKS -> {
                if (rightFree) {
                    m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -1.05F);
                    m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, -0.7F);
                }
                m.head.xRot += k * 0.45F * Math.max(0.0F, Mth.sin(g * 0.12F));
            }
            case FOLLOW -> {
                if (rightFree) {
                    m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -1.55F);
                    m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, -0.2F + Mth.sin(g * 0.35F) * 0.5F);
                }
            }
        }
    }
}
