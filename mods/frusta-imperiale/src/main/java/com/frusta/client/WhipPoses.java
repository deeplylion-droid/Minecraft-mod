package com.frusta.client;

import com.frusta.item.WhipItem;
import com.frusta.motion.V3;
import com.frusta.motion.WhipArm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * How the arm holding a whip moves (third person, and the arm drawn in first person), and the pose of
 * a kneeling player.
 */
public final class WhipPoses {
    /** The arm holding the whip: follows the moves, or rests like any held item. */
    public static final HumanoidModel.ArmPose WHIP =
            HumanoidModel.ArmPose.create("FRUSTA_WHIP", false, WhipPoses::poseWhipArm);
    /** Kneeling (Sottomissione): head bowed, arms hanging a little forward. */
    public static final HumanoidModel.ArmPose KNEEL =
            HumanoidModel.ArmPose.create("FRUSTA_KNEEL", false, WhipPoses::poseKneeling);

    private WhipPoses() {
    }

    /** Creates the poses (see {@link WhipClientExtensions}). */
    public static void init() {
    }

    /** Vanilla's arm holding an item ({@code ArmPose.ITEM}) for the walking swing {@code walkXRot}. */
    public static float[] vanillaRest(float walkXRot) {
        return new float[]{walkXRot * 0.5F - (float) (Math.PI / 10), 0.0F, 0.0F};
    }

    /** First person: the arm reaches forward and a little right, so the handle shows at the bottom right. */
    public static float[] firstPersonRest(float pitchDeg) {
        return new float[]{(float) (-Math.PI / 2 + Math.toRadians(pitchDeg) + 0.42), 0.32F, 0.0F};
    }

    /** True while the whip spins over the player's head. */
    public static boolean isSpinning(Player player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof WhipItem;
    }

    /** True while the arm is doing something with the whip (a move or the spin). */
    public static boolean isMoving(Player player) {
        WhipAnim anim = WhipAnims.find(player);
        return isSpinning(player) || anim != null && anim.strike != null;
    }

    /**
     * Right-arm angles {xRot, yRot, zRot} for what the player is doing with the whip, or {@code rest}.
     * In first person the body is taken to face where the camera does.
     */
    public static float[] angles(Player player, float partialTick, float[] rest, boolean firstPerson) {
        if (isSpinning(player)) {
            float held = player.getTicksUsingItem() + partialTick;
            float[] spin = WhipArm.twirl(held);
            float k = Math.min(1.0F, held / 4.0F);
            return new float[]{Mth.lerp(k, rest[0], spin[0]), Mth.lerp(k, rest[1], spin[1]), Mth.lerp(k, rest[2], spin[2])};
        }
        WhipAnim anim = WhipAnims.find(player);
        if (anim == null || anim.strike == null) {
            return rest;
        }
        double t = WhipAnims.clock(partialTick) - anim.moveStart;
        float bodyYaw = firstPerson ? player.getViewYRot(partialTick) : Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        V3 aim = anim.strike.aim();
        double pitch = -Math.asin(Mth.clamp(aim.y(), -1, 1));
        double yawDeg = Math.toDegrees(Math.atan2(-aim.x(), aim.z()));
        double yaw = Math.toRadians(Mth.wrapDegrees(yawDeg - bodyYaw));
        return switch (anim.strike.move()) {
            case LASH -> WhipArm.lash(anim.strike.style(), t, pitch, yaw, rest);
            case IMPERIAL -> WhipArm.imperial(t, pitch, yaw, WhipArm.twirl(anim.spunFor), rest);
            case GRAB -> WhipArm.grab(t, pitch, yaw, rest);
        };
    }

    private static void poseWhipArm(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        if (!(entity instanceof Player player)) {
            return;
        }
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float[] a = angles(player, Minecraft.getInstance().getFrameTime(), vanillaRest(part.xRot), false);
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        // Vanilla adds 0.4 to crouching arms after this: take it off during the moves.
        part.xRot = a[0] - (model.crouching && isMoving(player) ? 0.4F : 0.0F);
        part.yRot = side * a[1];
        part.zRot = side * a[2];
    }

    private static void poseKneeling(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        model.head.xRot = 0.55F;
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        part.xRot = -0.35F;
        part.yRot = 0.0F;
        part.zRot = side * 0.12F;
    }
}
