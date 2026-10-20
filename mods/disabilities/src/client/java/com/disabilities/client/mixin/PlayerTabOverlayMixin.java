package com.disabilities.client.mixin;

import com.disabilities.client.ClientHooks;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void disabilities$badges(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(ClientHooks.tabName(cir.getReturnValue(), info.getProfile().id()));
    }
}
