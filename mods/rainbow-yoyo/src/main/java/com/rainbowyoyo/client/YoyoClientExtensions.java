package com.rainbowyoyo.client;

import com.rainbowyoyo.client.render.YoyoItemRenderer;
import com.rainbowyoyo.entity.YoyoEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Client side of the yo-yo item: its 3D renderer and the arm pose that follows the yo-yo. */
public class YoyoClientExtensions implements IClientItemExtensions {
    /**
     * Holding the yo-yo: the arm a bit away from the body, with the yo-yo at the side like a wheel.
     * With the yo-yo out: the arm points along the string, wherever the yo-yo is.
     */
    public static final HumanoidModel.ArmPose POSE =
            HumanoidModel.ArmPose.create("RAINBOWYOYO_STRING", false, YoyoClientExtensions::poseArm);

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return YoyoItemRenderer.get();
    }

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return hand == InteractionHand.MAIN_HAND && entity instanceof Player ? POSE : null;
    }

    private static void poseArm(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        YoyoEntity yoyo = YoyoEntity.clientYoyoOf(entity);
        if (yoyo == null) {
            part.xRot = part.xRot * 0.5F - 0.15F;
            part.yRot = 0.0F;
            part.zRot = arm == HumanoidArm.RIGHT ? 0.2F : -0.2F;
            return;
        }
        float partialTick = Minecraft.getInstance().getFrameTime();
        float[] angles = YoyoArm.finalAngles(entity, partialTick, arm, yoyo.renderPosition(partialTick));
        // Vanilla adds 0.4 to crouching arms and its idle sway after this: leave room for both.
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float age = entity.tickCount + partialTick;
        part.xRot = angles[0] - side * net.minecraft.util.Mth.sin(age * 0.067F) * 0.05F - (model.crouching ? 0.4F : 0.0F);
        part.yRot = angles[1];
        part.zRot = 0.0F;
    }
}
