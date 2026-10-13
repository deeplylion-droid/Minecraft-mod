package com.rainbowyoyo.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rainbowyoyo.client.YoyoRenderState;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The yo-yo item in 3D: in the hand (held at the side like a wheel), on the ground, in item frames
 * and slowly spinning in the inventory. Hidden in the hand while the yo-yo is thrown.
 */
public class YoyoItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static YoyoItemRenderer instance;

    public static YoyoItemRenderer get() {
        if (instance == null) {
            instance = new YoyoItemRenderer();
        }
        return instance;
    }

    private YoyoItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay) {
        boolean inHand = context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        if (inHand && YoyoRenderState.heldYoyoIsOut(context.firstPerson())) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        if (context == ItemDisplayContext.GUI || context == ItemDisplayContext.GROUND
                || context == ItemDisplayContext.FIXED) {
            float turn = (Util.getMillis() % 4000L) / 4000.0F;
            poseStack.mulPose(Axis.XP.rotation(turn * (float) Math.PI * 2.0F));
        }
        YoyoModelRenderer.render(poseStack, buffers, light, overlay, 0.2F);
        poseStack.popPose();
    }
}
