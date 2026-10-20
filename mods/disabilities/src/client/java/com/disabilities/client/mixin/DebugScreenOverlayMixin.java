package com.disabilities.client.mixin;

import com.disabilities.client.Me;
import com.disabilities.condition.Condition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    /** Amnesia: not even the always-on debug lines. */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true)
    private void disabilities$hide(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (Me.has(Condition.AMNESIA)) {
            ci.cancel();
        }
    }
}
