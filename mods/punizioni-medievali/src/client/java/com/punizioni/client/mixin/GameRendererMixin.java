package com.punizioni.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.punizioni.client.fx.ScreenFx;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The jolts of the blows, where vanilla tilts the view when you get hurt. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobHurt", at = @At("TAIL"))
    private void punizioni$shake(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        ScreenFx.shake(poseStack, cameraState.cameraEntityPartialTicks);
    }
}
