package com.jumpscares.client.mixin;

import com.jumpscares.client.scare.ScareHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During a scare the HUD disappears and the flash, static, vignette and black are drawn instead. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void jumpscares$overlay(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ScareHud.render(graphics, deltaTracker)) {
            ci.cancel();
        }
    }
}
