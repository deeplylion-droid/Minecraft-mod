package com.infezione.client.render;

import com.infezione.client.ClientInfections;
import com.infezione.infection.Stages;
import com.infezione.infection.Symptom;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderPlayerEvent;

/**
 * How an infected player moves, for everyone who sees them: hunched more at every stage, trembling hands
 * in the agony, the zombie's arms held forward, and the episodes (the hand to the bite, coughing into the
 * hand, twitches, convulsions, the head dropping, the transformation). The arms and the head go through
 * an arm pose of the player model; leaning and shaking move the whole body.
 */
public final class InfectedPoses {
    /** The arms (and the head) of an infected player, over what the hands hold. */
    public static final HumanoidModel.ArmPose INFECTED = HumanoidModel.ArmPose.create("INFEZIONE_INFECTED", false, InfectedPoses::poseArm);
    /** Injecting the antidote into one's own neck. */
    public static final HumanoidModel.ArmPose INJECT = HumanoidModel.ArmPose.create("INFEZIONE_INJECT", false, InfectedPoses::poseInject);

    private static final float PI = (float) Math.PI;

    /** What the player being drawn does this frame (one at a time: drawing is on one thread). */
    private static final Motion MOTION = new Motion();
    private static HumanoidModel.ArmPose originalRight = HumanoidModel.ArmPose.EMPTY;
    private static HumanoidModel.ArmPose originalLeft = HumanoidModel.ArmPose.EMPTY;
    private static boolean headDone;
    private static final Deque<Boolean> PUSHED = new ArrayDeque<>();

    private InfectedPoses() {
    }

    /** Creates the poses: called while the items are registered, before any player is drawn. */
    public static void init() {
    }

    /** The movement of one player in one frame. Angles of the arms in radians, of the body in degrees. */
    static final class Motion {
        boolean active;
        int stage;
        boolean zombieArms;
        /** Bent double: the model is drawn crouching (it bends at the hips, not like a plank). */
        boolean crouch;
        float lean;
        float roll;
        float pitch;
        float drop;
        float headX;
        float headY;
        float headZ;
        /** For each arm (0 right, 1 left): a pose to reach (x, y, z), how much (0 to 1), and a shake on top. */
        final float[][] target = new float[2][3];
        final float[] weight = new float[2];
        final float[][] add = new float[2][3];

        void reset() {
            active = false;
            zombieArms = false;
            crouch = false;
            lean = roll = pitch = drop = 0;
            headX = headY = headZ = 0;
            for (int i = 0; i < 2; i++) {
                weight[i] = 0;
                for (int k = 0; k < 3; k++) {
                    target[i][k] = 0;
                    add[i][k] = 0;
                }
            }
        }
    }

    /** Before a player is drawn: their pose for this frame, the arms that follow it, and the body leaning or shaking. */
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            PUSHED.push(false);
            return;
        }
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        // vanilla never resets the roll of the head: no player keeps another's
        model.head.zRot = 0.0F;
        headDone = false;
        compute(player, event.getPartialTick(), MOTION);
        if (!MOTION.active) {
            PUSHED.push(false);
            return;
        }
        originalRight = model.rightArmPose;
        originalLeft = model.leftArmPose;
        if (simple(originalRight)) {
            model.rightArmPose = INFECTED;
        }
        if (simple(originalLeft)) {
            model.leftArmPose = INFECTED;
        }
        if (MOTION.crouch) {
            model.crouching = true;
        }
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        PUSHED.push(true);
        if (MOTION.lean != 0 || MOTION.roll != 0 || MOTION.pitch != 0 || MOTION.drop != 0) {
            float bodyYaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
            float scale = player.isCrouching() ? 0.6F : 1.0F;
            poseStack.translate(0.0F, -MOTION.drop, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(-(MOTION.lean * scale + MOTION.pitch)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(MOTION.roll));
            poseStack.mulPose(Axis.YP.rotationDegrees(-(180.0F - bodyYaw)));
        }
    }

    public static void onRenderPost(RenderPlayerEvent.Post event) {
        Boolean pushed = PUSHED.poll();
        if (pushed != null && pushed) {
            event.getPoseStack().popPose();
        }
    }

    /** The arm poses that are safe to draw over: an empty hand, an item held still, a shield. */
    private static boolean simple(HumanoidModel.ArmPose pose) {
        return pose == HumanoidModel.ArmPose.EMPTY || pose == HumanoidModel.ArmPose.ITEM || pose == HumanoidModel.ArmPose.BLOCK;
    }

    private static void poseArm(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart part = right ? model.rightArm : model.leftArm;
        HumanoidModel.ArmPose original = right ? originalRight : originalLeft;
        float side = right ? 1.0F : -1.0F;
        // vanilla's arm for what the hand holds (HumanoidModel.poseRightArm / poseLeftArm)
        float x = part.xRot;
        float y = 0.0F;
        if (original == HumanoidModel.ArmPose.ITEM) {
            x = x * 0.5F - PI / 10.0F;
        } else if (original == HumanoidModel.ArmPose.BLOCK) {
            x = x * 0.5F - 0.9424779F;
            y = -side * PI / 6.0F;
        }
        float z = 0.0F;
        Motion m = MOTION;
        if (!headDone) {
            headDone = true;
            model.head.xRot += m.headX;
            model.head.yRot += m.headY;
            model.head.zRot += m.headZ;
        }
        if (m.zombieArms) {
            // a zombie's arms (AnimationUtils.animateZombieArms), swinging a little as it walks
            x = -PI / 2.25F + (x - (original == HumanoidModel.ArmPose.ITEM ? -PI / 10.0F : 0.0F)) * 0.25F;
            y = -side * 0.1F;
        }
        int i = right ? 0 : 1;
        float w = m.weight[i];
        x = Mth.lerp(w, x, m.target[i][0]) + m.add[i][0];
        y = Mth.lerp(w, y, side * m.target[i][1]) + side * m.add[i][1];
        z = Mth.lerp(w, z, side * m.target[i][2]) + side * m.add[i][2];
        part.xRot = x;
        part.yRot = y;
        part.zRot = z;
    }

    /**
     * The antidote, seen from outside: the other arm held out, the syringe (held like an ice pick, see the
     * item model) comes down on its forearm, the head bent to look, then the needle goes in, trembling.
     * Only the arm that uses an item is posed by the model, so this one sets the other arm too.
     */
    private static void poseInject(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart part = right ? model.rightArm : model.leftArm;
        ModelPart other = right ? model.leftArm : model.rightArm;
        float side = right ? 1.0F : -1.0F;
        float used = entity.getTicksUsingItem() + Minecraft.getInstance().getFrameTime();
        float reach = smooth(Math.min(1.0F, used / 8.0F));
        float stab = used > 12 ? smooth(Math.min(1.0F, (used - 12) / 5.0F)) : 0.0F;
        float tremble = used > 12 ? 0.03F * Mth.sin(used * 2.3F) : 0.0F;
        part.xRot = Mth.lerp(reach, part.xRot * 0.5F - PI / 10.0F, -1.05F + 0.18F * stab) + tremble;
        part.yRot = Mth.lerp(reach, 0.0F, -side * 0.45F);
        part.zRot = 0.0F;
        other.xRot = Mth.lerp(reach, other.xRot, -1.3F);
        other.yRot = Mth.lerp(reach, other.yRot, side * 0.35F);
        other.zRot = 0.0F;
        model.head.xRot += 0.35F * reach;
    }

    // =====================================================================================
    // What each stage and each episode does
    // =====================================================================================

    private static void compute(AbstractClientPlayer player, float partialTick, Motion m) {
        m.reset();
        int stage = ClientInfections.stage(player);
        if (stage == Stages.HEALTHY || player.isSleeping() || player.isFallFlying() || player.isVisuallySwimming()) {
            return;
        }
        m.active = true;
        m.stage = stage;
        float age = player.tickCount + partialTick;
        // hunched more at every stage
        m.lean = switch (stage) {
            case 3 -> 4.0F;
            case 4 -> 7.0F;
            case 5 -> 5.0F;
            default -> 0.0F;
        };
        if (stage == 4) {
            // trembling hands
            for (int i = 0; i < 2; i++) {
                m.add[i][0] += 0.035F * Mth.sin(age * 2.3F + i * 1.7F);
                m.add[i][2] += 0.04F * Mth.sin(age * 3.1F + i);
            }
        }
        if (stage == Stages.ZOMBIE) {
            m.zombieArms = true;
            m.headZ += 0.12F * Mth.sin(age * 0.05F);
        }
        ClientInfections.Episode episode = ClientInfections.episode(player);
        if (episode != null) {
            episode(episode.symptom(), episode.time(partialTick), episode.seed(), player, m);
        }
    }

    private static void episode(Symptom symptom, float t, int seed, AbstractClientPlayer player, Motion m) {
        float total = symptom.ticks;
        // the free hand: the left one for a right-handed player
        int free = player.getMainArm() == HumanoidArm.RIGHT ? 1 : 0;
        switch (symptom) {
            case THROB -> {
                // the left hand goes to the bite, on the left of the neck; the head leans away
                float e = envelope(t, 6, 10, total);
                reach(m, 1, -1.75F - 0.15F * Mth.sin(t * 0.9F), -0.35F, -0.05F, e);
                m.headZ -= 0.28F * e;
                m.headX += 0.12F * e;
            }
            case COUGH -> {
                // three coughs into the hand: the head and the body jerk forward each time
                float e = envelope(t, 3, 4, total);
                reach(m, free, -2.0F, -0.45F, 0.0F, e);
                float jerk = coughs(t, 3, 4.5F);
                m.headX += 0.45F * jerk;
                m.pitch += 7.0F * jerk;
            }
            case BLOOD_COUGH -> {
                // bent double, hands out, the head down, heaving
                float e = envelope(t, 4, 6, total);
                float heave = coughs(t, 4, 5.0F);
                m.crouch = e > 0.3F;
                reach(m, 0, -0.95F, -0.1F, 0.15F, e);
                reach(m, 1, -0.95F, -0.1F, 0.15F, e);
                m.headX += (0.35F + 0.3F * heave) * e;
                m.pitch += (2.0F + 6.0F * heave) * e;
                m.lean = 0.0F;
            }
            case TWITCH -> {
                // a jerk of the head and of one arm, gone at once
                float sign = (seed & 1) == 0 ? 1.0F : -1.0F;
                float e = t < 1.0F ? t : (float) Math.exp(-(t - 1.0F) * 0.7F);
                m.headY += 0.7F * sign * e;
                m.headZ += 0.35F * sign * e;
                m.add[(seed >> 1) & 1][2] += 0.9F * e;
                m.add[(seed >> 1) & 1][0] -= 0.5F * e;
                m.roll += 6.0F * sign * e;
            }
            case DIZZY -> {
                // swaying, arms out for balance
                float e = envelope(t, 10, 20, total);
                m.roll += 5.0F * Mth.sin(t * 0.13F) * e;
                m.pitch += 3.0F * Mth.sin(t * 0.09F + 1.0F) * e;
                m.headZ += 0.25F * Mth.sin(t * 0.13F + 0.6F) * e;
                m.headX += 0.15F * e;
                m.add[0][2] += 0.35F * e;
                m.add[1][2] += 0.35F * e;
            }
            case CONVULSION -> {
                // the whole body shakes, the arms seize up, the head is thrown back
                float e = envelope(t, 3, 8, total);
                float fast = t * 2.9F + seed;
                reach(m, 0, -0.8F, 0.0F, 0.35F, e);
                reach(m, 1, -0.8F, 0.0F, 0.35F, e);
                for (int i = 0; i < 2; i++) {
                    m.add[i][0] += 0.45F * noise(fast + i * 3.1F) * e;
                    m.add[i][2] += 0.35F * noise(fast * 1.3F + i * 5.7F) * e;
                }
                m.headX += (-0.45F + 0.3F * noise(fast * 1.1F + 9.0F)) * e;
                m.headY += 0.3F * noise(fast * 0.9F + 4.0F) * e;
                m.roll += 5.0F * noise(fast * 1.2F + 2.0F) * e;
                m.pitch += 4.0F * noise(fast + 7.0F) * e;
                m.drop += (0.03F + 0.03F * noise(fast * 1.7F)) * e;
            }
            case BLACKOUT -> {
                // the head drops, the arms hang, the knees give
                float e = envelope(t, 8, 14, total);
                m.crouch = e > 0.4F;
                m.headX += 0.75F * e;
                reach(m, 0, 0.05F, 0.0F, 0.05F, e);
                reach(m, 1, 0.05F, 0.0F, 0.05F, e);
                m.drop += 0.08F * e;
                m.roll += 3.0F * Mth.sin(t * 0.2F) * e;
                m.lean = 0.0F;
            }
            case GROAN -> {
                // the zombie throws its head back and to the side
                float e = envelope(t, 6, 10, total);
                m.headX += -0.5F * e;
                m.headZ += 0.3F * e;
            }
            case TRANSFORM -> {
                // arms thrown wide, head back, shaking all over
                float e = envelope(t, 4, 14, total);
                float fast = t * 2.5F + seed;
                reach(m, 0, -0.5F, 0.0F, 1.25F, e);
                reach(m, 1, -0.5F, 0.0F, 1.25F, e);
                for (int i = 0; i < 2; i++) {
                    m.add[i][0] += 0.3F * noise(fast + i * 2.3F) * e;
                    m.add[i][2] += 0.25F * noise(fast * 1.4F + i * 4.1F) * e;
                }
                m.headX += (-0.7F + 0.25F * noise(fast + 8.0F)) * e;
                m.roll += 6.0F * noise(fast * 1.1F + 1.0F) * e;
                m.pitch += -6.0F * e + 4.0F * noise(fast * 0.8F + 3.0F) * e;
                m.zombieArms = false;
            }
        }
    }

    /**
     * An arm reaches a pose, given as for the right arm (the left one mirrors it): x negative raises the
     * arm forward, y negative turns it in towards the chest, z positive lifts it out to the side.
     */
    private static void reach(Motion m, int arm, float x, float y, float z, float weight) {
        m.target[arm][0] = x;
        m.target[arm][1] = y;
        m.target[arm][2] = z;
        m.weight[arm] = Math.max(m.weight[arm], weight);
    }

    /** 0 to 1 in {@code in} ticks, 1, then back to 0 in the last {@code out} ticks. */
    private static float envelope(float t, float in, float out, float total) {
        float up = smooth(Mth.clamp(t / in, 0.0F, 1.0F));
        float down = smooth(Mth.clamp((total - t) / out, 0.0F, 1.0F));
        return Math.min(up, down);
    }

    /** {@code count} sharp jerks, one every {@code every} ticks. */
    private static float coughs(float t, int count, float every) {
        if (t < 0 || t > count * every) {
            return 0.0F;
        }
        float phase = (t % every) / every;
        return phase < 0.2F ? phase / 0.2F : (float) Math.exp(-(phase - 0.2F) * 6.0F);
    }

    private static float smooth(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    /** A smooth shake between -1 and 1. */
    private static float noise(float t) {
        return 0.6F * Mth.sin(t) + 0.3F * Mth.sin(t * 2.17F + 1.3F) + 0.1F * Mth.sin(t * 4.31F + 0.4F);
    }
}
