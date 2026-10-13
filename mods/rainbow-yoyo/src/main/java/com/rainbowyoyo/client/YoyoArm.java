package com.rainbowyoyo.client;

import com.rainbowyoyo.motion.ArmAim;
import com.rainbowyoyo.motion.V3;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The arm holding the string, for a real player: where the shoulder is, how the arm turns to
 * point at the yo-yo and where the hand ends up. The player model (third person), the arm drawn
 * in first person and the string all use these, so the string always leaves from the hand.
 */
public final class YoyoArm {

    private YoyoArm() {
    }

    public static double bodyYaw(LivingEntity entity, float partialTick) {
        return Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
    }

    /** Shoulder pivot in model pixels. */
    public static V3 pivot(HumanoidArm arm, boolean crouching) {
        double x = arm == HumanoidArm.RIGHT ? ArmAim.SHOULDER_X : -ArmAim.SHOULDER_X;
        return new V3(x, crouching ? ArmAim.SHOULDER_Y_CROUCHING : ArmAim.SHOULDER_Y, 0);
    }

    private static V3 feet(LivingEntity entity, float partialTick) {
        Vec3 feet = entity.getPosition(partialTick);
        // The player renderer lowers crouching players by 1/8 of a block.
        return new V3(feet.x, feet.y + (entity.isCrouching() ? -0.125 : 0.0), feet.z);
    }

    public static V3 shoulder(LivingEntity entity, float partialTick, HumanoidArm arm) {
        boolean crouching = entity.isCrouching();
        return feet(entity, partialTick).add(ArmAim.armPoint(pivot(arm, crouching), 0, 0, 0, V3.ZERO,
                bodyYaw(entity, partialTick), ArmAim.PLAYER_SCALE));
    }

    /**
     * Final arm rotation {xRot, yRot, zRot} pointing at {@code target}, including the small idle
     * sway vanilla adds to arms ({@code AnimationUtils.bobModelPart}).
     */
    public static float[] finalAngles(LivingEntity entity, float partialTick, HumanoidArm arm, Vec3 target) {
        V3 dir = new V3(target.x, target.y, target.z).sub(shoulder(entity, partialTick, arm));
        float[] aim = ArmAim.angles(dir, bodyYaw(entity, partialTick));
        float age = entity.tickCount + partialTick;
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float zRot = side * (Mth.cos(age * 0.09F) * 0.05F + 0.05F);
        float xRot = aim[0] + side * Mth.sin(age * 0.067F) * 0.05F;
        return new float[]{xRot, aim[1], zRot};
    }

    /** Where the string leaves the hand for an arm turned by {@code angles} ({xRot, yRot, zRot}). */
    public static Vec3 handTip(LivingEntity entity, float partialTick, HumanoidArm arm, float[] angles) {
        boolean crouching = entity.isCrouching();
        V3 handPx = arm == HumanoidArm.RIGHT ? ArmAim.HAND_PX : new V3(-ArmAim.HAND_PX.x(), ArmAim.HAND_PX.y(), 0);
        V3 p = feet(entity, partialTick).add(ArmAim.armPoint(pivot(arm, crouching), angles[0], angles[1], angles[2],
                handPx, bodyYaw(entity, partialTick), ArmAim.PLAYER_SCALE));
        return new Vec3(p.x(), p.y(), p.z());
    }
}
