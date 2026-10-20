package com.disabilities.client.mixin;

import com.disabilities.client.Fx;
import com.disabilities.client.Me;
import com.disabilities.condition.Condition;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    /** Blind: black fog a couple of blocks away (wider for a moment after a cane tap). Myopia: haze. */
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void disabilities$fog(Camera camera, int renderDistance, DeltaTracker delta, float darken, ClientLevel level,
                                  CallbackInfoReturnable<FogData> cir) {
        FogData fog = cir.getReturnValue();
        if (Me.has(Condition.BLIND)) {
            float radius = Fx.blindRadius(delta.getGameTimeDeltaPartialTick(false));
            fog.environmentalStart = 0.4F;
            fog.environmentalEnd = radius;
            fog.renderDistanceStart = 0.4F;
            fog.renderDistanceEnd = radius;
            fog.skyEnd = radius;
            fog.cloudEnd = radius;
            fog.color.set(0.0F, 0.0F, 0.0F, 1.0F);
        } else if (Me.has(Condition.MYOPIA)) {
            fog.environmentalStart = Math.min(fog.environmentalStart, 12.0F);
            fog.environmentalEnd = Math.min(fog.environmentalEnd, 72.0F);
            fog.cloudEnd = Math.min(fog.cloudEnd, 72.0F);
        }
    }
}
