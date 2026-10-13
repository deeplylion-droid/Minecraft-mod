package com.wurstel.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wurstel.item.WurstelBeltItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;

/** Adds the giant würstel to humanoid models (players, armor stands, zombies, ...) wearing the belt. */
public class WurstelLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    public WurstelLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!WurstelBeltItem.isWearing(entity)) {
            return;
        }
        M model = this.getParentModel();
        poseStack.pushPose();
        if (model.young) {
            // Baby humanoids draw their body at half size, moved down (see AgeableListModel).
            poseStack.scale(0.5F, 0.5F, 0.5F);
            poseStack.translate(0.0F, 1.5F, 0.0F);
        }
        // In the inventory preview a 4-block würstel would cover the whole screen: shorten it there.
        float length = WurstelRenderer.isGuiRender() ? 0.3F : 1.0F;
        WurstelRenderer.renderOnModel(poseStack, buffers, packedLight,
                LivingEntityRenderer.getOverlayCoords(entity, 0.0F),
                entity, model.body, model.head, partialTick, length);
        poseStack.popPose();
    }
}
