package com.disabilities.client.mixin;

import com.disabilities.client.ClientHooks;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    /** Crutches, no breath, frozen, asleep: no sprinting. */
    @Inject(method = "isSprintingPossible", at = @At("RETURN"), cancellable = true)
    private void disabilities$noSprint(boolean allowedInShallowWater, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !ClientHooks.canSprint((LocalPlayer) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
