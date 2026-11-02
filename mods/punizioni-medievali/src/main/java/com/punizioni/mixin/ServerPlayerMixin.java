package com.punizioni.mixin;

import com.punizioni.misdeed.Misdeeds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

/** The king's lens remembers what was in a chest when someone opened it, and what is gone when they close it. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "openMenu", at = @At("RETURN"))
    private void punizioni$opened(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (cir.getReturnValue().isPresent()) {
            ServerPlayer self = (ServerPlayer) (Object) this;
            Misdeeds.opened(self, self.containerMenu);
        }
    }

    @Inject(method = "doCloseContainer", at = @At("HEAD"))
    private void punizioni$closing(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Misdeeds.closing(self, self.containerMenu);
    }
}
