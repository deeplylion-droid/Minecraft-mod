package com.infezione.client;

import com.infezione.client.render.InfectedPoses;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Client side of the antidote: the arm takes the needle to the neck, in third person and in first person. */
public class AntidoteClientExtensions implements IClientItemExtensions {
    static {
        // The arm poses of the mod are new values of vanilla's ArmPose enum: they must exist before the game first
        // switches over that enum (the first player it draws), or that switch is too short for them and crashes.
        // This class is made while the items are registered, early enough.
        InfectedPoses.init();
    }

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return entity.isUsingItem() && entity.getUsedItemHand() == hand ? InfectedPoses.INJECT : null;
    }

    /**
     * First person: from the corner of the screen the syringe swings round, its needle down towards the
     * neck, then it is pushed in, trembling. (The pose was found with a simulation of vanilla's held item
     * transform: the syringe lies across the lower right of the screen, the needle at the bottom left.)
     */
    @Override
    public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           float partialTick, float equipProgress, float swingProgress) {
        if (!player.isUsingItem() || player.getUseItem() != stack) {
            return false;
        }
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float used = player.getTicksUsingItem() + partialTick;
        float up = smooth(Mth.clamp(used / 9.0F, 0.0F, 1.0F));
        float push = smooth(Mth.clamp((used - 12.0F) / 7.0F, 0.0F, 1.0F));
        float tremble = used > 9 ? 0.004F * Mth.sin(used * 2.7F) : 0.0F;
        poseStack.translate(side * 0.56F, -0.52F, -0.72F);
        poseStack.translate(side * (-0.155F * up - 0.05F * push) + tremble, 0.432F * up - 0.16F * push + tremble, -0.24F * up);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 117.1F * up));
        return true;
    }

    private static float smooth(float x) {
        return x * x * (3.0F - 2.0F * x);
    }
}
