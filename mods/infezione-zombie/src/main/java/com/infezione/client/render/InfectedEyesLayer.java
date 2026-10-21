package com.infezione.client.render;

import com.infezione.client.ClientInfections;
import com.infezione.client.skin.InfectedSkins;
import com.infezione.infection.Stages;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The milky eyes of stage 4 and of the zombie glow in the dark, like a spider's (stage 4 dimly, flickering). */
public class InfectedEyesLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public InfectedEyesLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        ResourceLocation glow = InfectedSkins.glow(player.getUUID());
        if (glow == null) {
            return;
        }
        int stage = ClientInfections.stage(player);
        float brightness = stage == Stages.ZOMBIE ? 1.0F : 0.8F + 0.2F * Mth.sin(ageInTicks * 0.15F);
        VertexConsumer buffer = buffers.getBuffer(RenderType.eyes(glow));
        this.getParentModel().renderToBuffer(poseStack, buffer, 0xF000F0, OverlayTexture.NO_OVERLAY, brightness, brightness, brightness, 1.0F);
    }
}
