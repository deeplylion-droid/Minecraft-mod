package com.belowbedrock.client.mixin;

import com.belowbedrock.client.ScreenFx;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While you wake up on the other side, the loading screen is just black. */
@Mixin(ReceivingLevelScreen.class)
public abstract class ReceivingLevelScreenMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void belowbedrock$black(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (ScreenFx.black(0.0F) > 0.0F) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
            ci.cancel();
        }
    }
}
