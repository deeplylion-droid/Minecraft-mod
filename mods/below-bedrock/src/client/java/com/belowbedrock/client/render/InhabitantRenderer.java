package com.belowbedrock.client.render;

import com.belowbedrock.BelowBedrock;
import com.belowbedrock.entity.Inhabitant;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Him: taller and thinner than a player, and he casts no shadow. */
public class InhabitantRenderer extends HumanoidMobRenderer<Inhabitant, InhabitantModel> {
    public static final ResourceLocation TEXTURE = BelowBedrock.id("textures/entity/inhabitant.png");

    public InhabitantRenderer(EntityRendererProvider.Context context) {
        super(context, new InhabitantModel(context.bakeLayer(ModelLayers.PLAYER)), 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(Inhabitant entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Inhabitant entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375F * 0.92F, 0.9375F * 1.16F, 0.9375F * 0.92F);
    }
}
