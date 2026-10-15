package com.disabilities.mixin;

import com.disabilities.condition.Condition;
import com.disabilities.condition.ModData;
import com.disabilities.server.Ailments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    /** Asthma: breath comes back slowly, and not at all while running, swimming or climbing. */
    @Inject(method = "increaseAirSupply", at = @At("RETURN"), cancellable = true)
    private void disabilities$slowBreath(int current, CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof Player player && ModData.has(player, Condition.ASTHMA)) {
            cir.setReturnValue(Ailments.exerting(player) ? current : Math.min(current + 1, player.getMaxAirSupply()));
        }
    }

    /** Hemophilia: nothing heals you while you bleed. */
    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void disabilities$noHealing(float amount, CallbackInfo ci) {
        if ((Object) this instanceof Player player && Ailments.blocksHealing(player)) {
            ci.cancel();
        }
    }
}
