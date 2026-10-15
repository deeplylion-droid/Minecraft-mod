package com.disabilities.client.mixin;

import com.disabilities.client.Fx;
import com.disabilities.client.Me;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    /** Parkinson: the aim trembles, every frame. */
    @Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
    private void disabilities$tremor(CallbackInfo ci) {
        if (this.minecraft.player != null && this.minecraft.level != null) {
            Fx.tremor(this.minecraft, this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        }
    }

    /** Asleep: you cannot look around. */
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void disabilities$asleep(double mousea, CallbackInfo ci) {
        if (Me.asleep()) {
            ci.cancel();
        }
    }
}
