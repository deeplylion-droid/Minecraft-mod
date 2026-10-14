package com.jumpscares.client.mixin;

import com.jumpscares.client.scare.ScareClient;
import com.jumpscares.client.scare.ScareRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** The monster goes in your face right after the world is drawn, where the hand would go. */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void jumpscares$monster(DeltaTracker deltaTracker, CallbackInfo ci) {
        ScareRenderer.renderVictim(deltaTracker.getGameTimeDeltaPartialTick(true));
    }

    /** Screen shake, added where vanilla tilts the view when you get hurt. */
    @Inject(method = "bobHurt", at = @At("TAIL"))
    private void jumpscares$shake(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        ScareClient.shake(poseStack, partialTick);
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void jumpscares$fovPunch(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        if (useFovSetting) {
            double factor = ScareClient.fovFactor(partialTick);
            if (factor != 1.0) {
                cir.setReturnValue(cir.getReturnValue() * factor);
            }
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void jumpscares$hideHand(Camera camera, float partialTick, Matrix4f matrix, CallbackInfo ci) {
        if (ScareClient.hidesHand()) {
            ci.cancel();
        }
    }
}
