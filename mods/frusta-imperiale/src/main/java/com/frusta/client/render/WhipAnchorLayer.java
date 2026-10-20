package com.frusta.client.render;

import com.frusta.client.WhipAnims;
import com.frusta.item.WhipItem;
import com.frusta.motion.V3;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemStack;

/**
 * Draws nothing: while a player is drawn, it notes where the lash leaves their whip's handle, so the
 * whip (drawn later, in the world) hangs exactly from it.
 */
public class WhipAnchorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public WhipAnchorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        ItemStack whip = WhipItem.held(player);
        // The inventory preview draws the player with an orthographic projection: not a real position.
        if (whip.isEmpty() || RenderSystem.getProjectionMatrix().m33() > 0.5F) {
            return;
        }
        poseStack.pushPose();
        HandleTip.toItem(getParentModel(), player.getMainArm(), poseStack);
        V3[] tip = HandleTip.find(player, whip, player.getMainArm(), poseStack);
        poseStack.popPose();
        WhipAnims.anchor(player, tip[0], tip[1]);
    }
}
