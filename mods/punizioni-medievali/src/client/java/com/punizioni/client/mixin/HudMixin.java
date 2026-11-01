package com.punizioni.client.mixin;

import com.punizioni.client.fx.ScreenFx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The victim's screen (blood, dark, heat), drawn over the HUD and also with F1. */
@Mixin(Hud.class)
public abstract class HudMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void punizioni$victim(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!(Minecraft.getInstance().gui.screen() instanceof LevelLoadingScreen)) {
            ScreenFx.draw(graphics, delta);
        }
    }
}
