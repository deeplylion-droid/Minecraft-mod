package com.disabilities.client.mixin;

import com.disabilities.client.Fx;
import com.disabilities.client.Me;
import com.disabilities.condition.Condition;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    /** Blind: no sky either. */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void disabilities$noSky(CameraRenderState cameraState, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Me.has(Condition.BLIND)) {
            cameraState.entityRenderState.doesMobEffectBlockSky = true;
        }
    }

    /** Vertigo "dolly zoom", sneezes and stumbles. */
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void disabilities$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        float factor = Fx.fov(partialTicks);
        if (factor != 1.0F) {
            cir.setReturnValue(cir.getReturnValueF() * factor);
        }
    }
}
