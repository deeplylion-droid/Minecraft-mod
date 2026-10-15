package com.disabilities.client.mixin;

import com.disabilities.client.ClientHooks;
import com.disabilities.client.Fx;
import com.disabilities.client.Me;
import com.disabilities.condition.Condition;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void disabilities$attack(CallbackInfoReturnable<Boolean> cir) {
        if (ClientHooks.handsBlocked(false)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void disabilities$mine(boolean down, CallbackInfo ci) {
        if (down && ClientHooks.handsBlocked(false)) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void disabilities$use(CallbackInfo ci) {
        if (ClientHooks.handsBlocked(true)) {
            ci.cancel();
        }
    }

    /** Blind: what made a sound shows as an outline, through the darkness. */
    @Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
    private void disabilities$echo(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && Fx.echoing(entity) && Me.has(Condition.BLIND)) {
            cir.setReturnValue(true);
        }
    }
}
