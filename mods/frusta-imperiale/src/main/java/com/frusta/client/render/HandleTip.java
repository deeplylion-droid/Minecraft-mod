package com.frusta.client.render;

import com.frusta.motion.V3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Where the lash leaves the handle: the top of the handle model, found with exactly the transforms
 * vanilla uses to draw an item in a hand ({@code ItemInHandLayer}) and the model's own display
 * transform, so the lash sticks to the handle whatever the arm does.
 */
public final class HandleTip {
    /** Top centre of the handle model, in its pixels (see models/item/whip_handle.json). */
    private static final float TIP_X = 8.0F / 16.0F;
    private static final float TIP_Y = 11.0F / 16.0F;
    private static final float TIP_Z = 8.0F / 16.0F;

    private HandleTip() {
    }

    /**
     * Moves {@code poseStack} (in the model space of a humanoid whose arms are already posed) to the
     * held item's space, like {@code ItemInHandLayer.renderArmWithItem} does before drawing it.
     */
    public static void toItem(HumanoidModel<?> model, HumanoidArm arm, PoseStack poseStack) {
        model.translateToHand(arm, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        boolean left = arm == HumanoidArm.LEFT;
        poseStack.translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
    }

    /**
     * From the held item's space (see {@link #toItem}): the tip of the handle and the direction the
     * handle points, in world space. Returns {tip, direction}.
     */
    public static V3[] find(LivingEntity entity, ItemStack stack, HumanoidArm arm, PoseStack poseStack) {
        boolean left = arm == HumanoidArm.LEFT;
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getModel(stack, entity.level(), entity, entity.getId());
        poseStack.pushPose();
        ForgeHooksClient.handleCameraTransforms(poseStack, model,
                left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        Matrix4f m = poseStack.last().pose();
        Vector3f tip = m.transformPosition(new Vector3f(TIP_X, TIP_Y, TIP_Z));
        Vector3f dir = m.transformDirection(new Vector3f(0, 1, 0));
        poseStack.popPose();
        return new V3[]{ViewSpace.toWorld(tip), ViewSpace.dirToWorld(dir).normalize(new V3(0, -1, 0))};
    }
}
