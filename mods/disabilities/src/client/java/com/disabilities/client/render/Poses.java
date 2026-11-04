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
            m.body.xScale = 1.55F;
            m.body.zScale = 1.8F;
            m.body.z -= 0.8F;
            m.rightArm.x -= 2.2F;
            m.leftArm.x += 2.2F;
            m.rightArm.xScale = m.leftArm.xScale = 1.2F;
            m.rightArm.zScale = m.leftArm.zScale = 1.2F;
            m.rightArm.zRot += 0.18F;
            m.leftArm.zRot -= 0.18F;
            m.rightLeg.x -= 0.8F;
            m.leftLeg.x += 0.8F;
            m.rightLeg.xScale = m.leftLeg.xScale = 1.3F;
            m.rightLeg.zScale = m.leftLeg.zScale = 1.35F;
            m.head.y += 0.6F;
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
            float k = v.frozen ? 2.4F : 1.6F;
            m.head.xRot += Noise.tremor(seed, t * 0.55F) * 0.05F * k;
            m.head.yRot += Noise.tremor(seed + 1, t * 0.55F) * 0.06F * k;
            m.rightArm.xRot += Noise.tremor(seed + 2, t * 0.6F) * 0.16F * k;
            m.rightArm.zRot += Noise.tremor(seed + 3, t * 0.6F) * 0.07F * k;
            m.leftArm.xRot += Noise.tremor(seed + 4, t * 0.6F) * 0.16F * k;
            m.leftArm.zRot += Noise.tremor(seed + 5, t * 0.6F) * 0.07F * k;
            if (!special) {
                // small shuffling steps, a little bent forward
                m.rightLeg.xRot *= 0.35F;
                m.leftLeg.xRot *= 0.35F;
                m.body.xRot += 0.1F;
                m.head.xRot += 0.1F;
            }
            if (v.frozen && !special) {
                m.rightLeg.xRot = 0.05F;
                m.leftLeg.xRot = -0.05F;
                m.body.xRot += 0.12F;
            }
        }

        if (v.has(Condition.VERTIGO) && !special) {
            float sway = Mth.sin(t * 0.08F) * 0.12F;
            m.body.zRot += Mth.sin(t * 0.07F) * 0.07F;
            m.head.zRot += Mth.sin(t * 0.07F + 0.6F) * 0.12F;
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
        more(m, s, v, special, rightFree, leftFree, t);
    }

    // ---------------------------------------------------------------- 1.1.0

    /** The arm of the main hand and the other one. */
    private static ModelPart main(PlayerModel m, Visuals v) {
        return v.missingLeftArm ? m.rightArm : m.leftArm;
    }

    private static ModelPart off(PlayerModel m, Visuals v) {
        return v.missingLeftArm ? m.leftArm : m.rightArm;
    }

    /**
     * +1 for the right side, -1 for the left. An arm turns towards the chest with yRot = -side * a
     * and moves out to the side with zRot = side * a (like the vanilla shield and crossbow poses).
     */
    private static float side(PlayerModel m, ModelPart arm) {
        return arm == m.rightArm ? 1.0F : -1.0F;
    }

    private static void more(PlayerModel m, AvatarRenderState s, Visuals v, boolean special, boolean rightFree, boolean leftFree, float t) {
        long seed = v.seed;
        ModelPart main = main(m, v);
        ModelPart off = off(m, v);
        boolean mainFree = main == m.rightArm ? rightFree : leftFree;
        boolean offFree = off == m.rightArm ? rightFree : leftFree;

        // epilepsy: convulsions on the ground
        if (v.seizing) {
            float k = 0.55F;
            m.head.xRot += Noise.tremor(seed, t * 3.1F) * 0.45F * k;
            m.head.yRot += Noise.tremor(seed + 1, t * 2.9F) * 0.5F * k;
            m.rightArm.xRot += Noise.tremor(seed + 2, t * 3.3F) * 1.1F * k;
            m.rightArm.zRot += Noise.tremor(seed + 3, t * 3.0F) * 0.6F * k;
            m.leftArm.xRot += Noise.tremor(seed + 4, t * 3.4F) * 1.1F * k;
            m.leftArm.zRot += Noise.tremor(seed + 5, t * 3.2F) * 0.6F * k;
            m.rightLeg.xRot += Noise.tremor(seed + 6, t * 3.0F) * 0.9F * k;
            m.leftLeg.xRot += Noise.tremor(seed + 7, t * 3.1F) * 0.9F * k;
            m.body.zRot += Noise.tremor(seed + 8, t * 2.5F) * 0.12F;
            return;
        }
        if (v.arrested) {
            // still, on the back
            return;
        }

        // brittle bones: the broken leg stays stiff (limp), the broken arm hangs in the sling across the chest
        if (v.brokenLeg && !special && !v.has(Condition.WHEELCHAIR)) {
            float limp = Math.min(1.0F, s.walkAnimationSpeed * 1.6F);
            m.leftLeg.xRot = 0.08F;
            m.rightLeg.xRot *= 0.7F;
            m.body.zRot += Mth.sin(s.walkAnimationPos * 0.6662F) * 0.08F * limp;
        }
        if (v.brokenArm && !special) {
            float sd = side(m, main);
            main.xRot = -0.85F;
            main.yRot = -sd * 0.7F;
            main.zRot = sd * -0.1F;
        }

        // weak heart: a hand pressed on the chest, bent over when it is critical
        if (v.heartStage > 0 && !special && offFree) {
            float sd = side(m, off);
            off.xRot = -0.95F;
            off.yRot = -sd * 0.75F;
            off.zRot = 0.0F;
            if (v.heartStage == 2) {
                m.body.xRot += 0.18F + Mth.sin(t * 0.6F) * 0.04F;
                m.head.xRot += 0.25F;
            }
        }

        // claustrophobia: shaking when anxious; panic: arms over the head
        if (v.panicking && !special) {
            m.rightArm.xRot = -2.7F + Noise.tremor(seed + 9, t * 2.0F) * 0.15F;
            m.rightArm.zRot = 0.55F;
            m.leftArm.xRot = -2.7F + Noise.tremor(seed + 10, t * 2.0F) * 0.15F;
            m.leftArm.zRot = -0.55F;
            m.head.xRot += 0.45F;
            m.head.yRot += Noise.tremor(seed + 11, t * 1.5F) * 0.25F;
        } else if (v.anxious && v.has(Condition.CLAUSTROPHOBIA)) {
            m.head.yRot += Noise.tremor(seed + 12, t * 0.9F) * 0.12F;
            m.rightArm.zRot += Noise.tremor(seed + 13, t * 2.0F) * 0.08F;
            m.leftArm.zRot += Noise.tremor(seed + 14, t * 2.0F) * 0.08F;
        }

        // alien hand: the off arm never stays still
        if (v.has(Condition.ALIEN_HAND) && offFree && !special && v.motion == null) {
            float sd = side(m, off);
            off.xRot += Noise.smooth(seed + 15, t * 0.05F) * 0.7F + Noise.tremor(seed + 16, t * 0.9F) * 0.15F;
            off.zRot += sd * (0.15F + Noise.smooth(seed + 17, t * 0.04F) * 0.35F);
            off.yRot += Noise.smooth(seed + 18, t * 0.06F) * 0.4F;
        }

        // albinism and achromatopsia in the sun: a hand to shade the eyes
        if (v.dazzled && !special && mainFree && v.motion == null && !v.brokenArm) {
            float sd = side(m, main);
            main.xRot = -2.55F;
            main.yRot = sd * -0.35F;
            main.zRot = sd * 0.25F;
            m.head.xRot += 0.12F;
        }

        // blind: the cane sweeps left and right while walking
        if (v.cane && v.has(Condition.BLIND) && !special && v.motion == null && !v.brokenArm) {
            float walk = Math.min(1.0F, s.walkAnimationSpeed * 2.0F);
            main.xRot = -0.55F;
            main.yRot = Mth.sin(s.walkAnimationPos * 0.33F) * 0.45F * walk;
        }

        // one eye: the head turns a little to see with the good one; myopia: leaning in to see
        if (v.has(Condition.ONE_EYE)) {
            m.head.zRot += 0.14F;
            m.head.yRot -= 0.12F;
        }
        if (v.has(Condition.MYOPIA) && !special) {
            m.head.xRot += v.glassesOff ? 0.35F : 0.15F;
            m.head.z -= v.glassesOff ? 1.2F : 0.5F;
        }

        // obesity: the belly bounces at every step
        if (v.has(Condition.OBESITY) && !special) {
            float walk = Math.min(1.0F, s.walkAnimationSpeed * 1.8F);
            float bounce = Mth.abs(Mth.sin(s.walkAnimationPos * 0.6662F)) * walk;
            m.body.zScale *= 1.0F + 0.08F * bounce;
            m.body.xScale *= 1.0F - 0.03F * bounce;
        }

        if (v.motion != null) {
            motion(m, s, v, main, off, mainFree, offFree, special);
        }
    }

    /** The short movements the server sends (see {@link com.disabilities.net.MotionKind}). */
    private static void motion(PlayerModel m, AvatarRenderState s, Visuals v, ModelPart main, ModelPart off, boolean mainFree, boolean offFree,
                               boolean special) {
        float g = v.motionTime;
        int ticks = v.motion.ticks;
        float k = Math.min(1.0F, g / 3.0F) * Math.min(1.0F, Math.max(0.0F, (ticks - g) / 4.0F));
        float sdOff = side(m, off);
        float sdMain = side(m, main);
        switch (v.motion) {
            case ALIEN_SLAP -> {
                // the hand flies to the face
                float swing = g < 5 ? g / 5.0F : 1.0F;
                off.xRot = Mth.lerp(k, off.xRot, -2.1F * swing);
                off.yRot = Mth.lerp(k, off.yRot, -sdOff * 0.85F * swing);
                off.zRot = Mth.lerp(k, off.zRot, sdOff * -0.2F);
                if (g > 5 && g < 9) {
                    // the head flies away from the hand
                    m.head.yRot -= sdOff * 0.5F;
                    m.head.zRot -= sdOff * 0.15F;
                }
            }
            case ALIEN_THROW -> {
                float p = Mth.clamp(g / 8.0F, 0.0F, 1.0F);
                off.xRot = Mth.lerp(k, off.xRot, Mth.lerp(p, 0.9F, -2.4F));
                off.zRot = Mth.lerp(k, off.zRot, sdOff * 0.1F);
            }
            case ALIEN_PUNCH -> {
                float p = g < 3 ? g / 3.0F : Math.max(0.0F, 1.0F - (g - 3) / 7.0F);
                off.xRot = Mth.lerp(p, off.xRot, -1.6F);
                off.yRot = Mth.lerp(p, off.yRot, sdOff * -0.15F);
                m.body.yRot += sdOff * -0.25F * p;
            }
            case ALIEN_GRAB -> {
                off.xRot = Mth.lerp(k, off.xRot, -1.45F + Mth.sin(g * 0.9F) * 0.15F);
                off.yRot = Mth.lerp(k, off.yRot, sdOff * -0.3F);
            }
            case ALIEN_WAVE -> {
                off.xRot = Mth.lerp(k, off.xRot, -2.8F);
                off.zRot = Mth.lerp(k, off.zRot, sdOff * (0.3F + Mth.sin(g * 0.9F) * 0.35F));
            }
            case CPR -> {
                // both hands pushing down on a chest
                float push = Mth.abs(Mth.sin(g * 0.32F));
                m.rightArm.xRot = -0.75F + push * 0.35F;
                m.leftArm.xRot = -0.75F + push * 0.35F;
                m.rightArm.yRot = -0.35F;
                m.leftArm.yRot = 0.35F;
                m.body.xRot += 0.55F;
                m.head.xRot += 0.4F;
            }
            case EAR -> {
                if (mainFree && !special) {
                    main.xRot = Mth.lerp(k, main.xRot, -2.3F);
                    main.yRot = Mth.lerp(k, main.yRot, sdMain * -0.15F);
                    main.zRot = Mth.lerp(k, main.zRot, sdMain * -0.55F);
                    m.head.zRot += k * sdMain * -0.2F;
                }
            }
            case ITCH -> {
                if (mainFree && !special) {
                    main.xRot = Mth.lerp(k, main.xRot, -2.15F + Mth.sin(g * 1.1F) * 0.2F);
                    main.yRot = Mth.lerp(k, main.yRot, -sdMain * 0.55F + Mth.sin(g * 1.3F) * 0.2F);
                    m.head.xRot += k * 0.2F;
                }
            }
            case INHALER -> {
                main.xRot = Mth.lerp(k, main.xRot, -1.9F);
                main.yRot = Mth.lerp(k, main.yRot, -sdMain * 0.45F);
                m.head.xRot -= k * 0.15F;
            }
            case NOD -> {
                float p = g < 6 ? g / 6.0F : g < 22 ? 1.0F : Math.max(0.0F, 1.0F - (g - 22) / 3.0F);
                m.head.xRot += p * 0.85F;
            }
            case YAWN -> {
                m.head.xRot -= k * 0.45F;
                if (mainFree && !special) {
                    main.xRot = Mth.lerp(k, main.xRot, -1.85F);
                    main.yRot = Mth.lerp(k, main.yRot, -sdMain * 0.5F);
                }
            }
            case COUGH -> {
                m.head.xRot += k * Mth.abs(Mth.sin(g * 0.8F)) * 0.45F;
                m.body.xRot += k * Mth.abs(Mth.sin(g * 0.8F)) * 0.15F;
                if (mainFree && !special) {
                    main.xRot = Mth.lerp(k, main.xRot, -1.75F);
                    main.yRot = Mth.lerp(k, main.yRot, -sdMain * 0.5F);
                }
            }
            case LOOK_AROUND -> {
                m.head.yRot += k * Mth.sin(g * 0.16F) * 0.95F;
                m.head.xRot += k * Mth.sin(g * 0.11F + 1.0F) * 0.15F;
            }
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
