package com.filorosso.client.mixin;

import com.filorosso.client.ClientState;
import com.filorosso.util.Text;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The couple's heart after the name in the player list. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void filorosso$heart(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        Integer color = ClientState.roster.get(info.getProfile().id());
        if (color != null) {
            cir.setReturnValue(cir.getReturnValue().copy().append(Component.literal(" ")).append(Text.heart(color)));
        }
    }
}
