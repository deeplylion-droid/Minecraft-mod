package com.punizioni.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.punizioni.client.render.Visuals;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The pose a player is drawn from in this pass, before the renderer turns and scales the model: the
 * mantle and the lash live in world space and are drawn through it (in any pass, shadows included).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
    private void punizioni$entityPose(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
                                      CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar) {
            Visuals v = avatar.getData(Visuals.KEY);
            if (v != null) {
                v.entityPose.set(poseStack.last().pose());
                v.hasEntityPose = true;
            }
        }
    }
}
