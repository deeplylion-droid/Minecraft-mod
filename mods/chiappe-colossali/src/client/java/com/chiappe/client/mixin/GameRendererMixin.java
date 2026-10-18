package com.chiappe.client.mixin;

import com.chiappe.client.Shake;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Screen shake of the big farts, added where vanilla tilts the view when you get hurt. */
    @Inject(method = "bobHurt", at = @At("TAIL"))
    private void chiappe$shake(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        Shake.apply(poseStack);
    }
}
