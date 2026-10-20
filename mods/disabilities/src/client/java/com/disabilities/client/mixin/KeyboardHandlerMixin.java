package com.disabilities.client.mixin;

import com.disabilities.client.Me;
import com.disabilities.condition.Condition;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    /** Amnesia: F3 does nothing (no coordinates). */
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void disabilities$noDebug(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if (Me.has(Condition.AMNESIA)) {
            Options options = Minecraft.getInstance().options;
            if (options.keyDebugOverlay.matches(event) || options.keyDebugModifier.matches(event)) {
                ci.cancel();
            }
        }
    }
}
