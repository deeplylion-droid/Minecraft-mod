package com.filorosso.mixin;

import com.filorosso.server.Combat;
import com.filorosso.server.SharedLife;
import com.filorosso.server.Twins;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Couple hits, the boss's resistance, the shield fate; damage and healing are shared by the couple; the twins' effects. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float filorosso$damage(float amount, ServerLevel level, DamageSource source) {
        SharedLife.beforeHurt((LivingEntity) (Object) this);
        return Combat.modify((LivingEntity) (Object) this, source, amount);
    }

    @Inject(method = "heal", at = @At("HEAD"))
    private void filorosso$heal(float amount, CallbackInfo ci) {
        SharedLife.onHeal((LivingEntity) (Object) this, amount);
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"))
    private void filorosso$effect(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            Twins.onEffect((LivingEntity) (Object) this, effect);
        }
    }
}
