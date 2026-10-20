package com.disabilities.client.mixin;

import com.disabilities.client.hud.Overlays;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The screen effects of the conditions, also with the HUD hidden (F1). */
@Mixin(Hud.class)
public abstract class HudMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void disabilities$vision(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!(Minecraft.getInstance().gui.screen() instanceof LevelLoadingScreen)) {
            Overlays.vision(graphics, delta);
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void disabilities$blackout(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!(Minecraft.getInstance().gui.screen() instanceof LevelLoadingScreen)) {
            Overlays.blackout(graphics, delta);
        }
    }
}
