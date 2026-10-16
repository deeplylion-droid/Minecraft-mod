package com.filorosso.mixin;

import com.filorosso.server.Combat;
import com.filorosso.server.SharedLife;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Couple hits, the boss's resistance, the shield fate; healing is shared by the couple. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float filorosso$damage(float amount, ServerLevel level, DamageSource source) {
        return Combat.modify((LivingEntity) (Object) this, source, amount);
    }

    @Inject(method = "heal", at = @At("HEAD"))
    private void filorosso$heal(float amount, CallbackInfo ci) {
        SharedLife.onHeal((LivingEntity) (Object) this, amount);
    }
}
