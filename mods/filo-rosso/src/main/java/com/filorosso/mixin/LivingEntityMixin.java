package com.filorosso.mixin;

import com.filorosso.server.Combat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Couple hits, Lo Spezzacuori's resistance, shared hearts. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float filorosso$damage(float amount, ServerLevel level, DamageSource source) {
        return Combat.modify((LivingEntity) (Object) this, source, amount);
    }
}
