package com.belowbedrock.mixin;

import com.belowbedrock.world.Faded;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nobody is born in the faded world: no villagers, no animals, no mobs in structures. */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void belowbedrock$noLife(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Mob && Faded.is(this.level)) {
            cir.setReturnValue(false);
        }
    }
}
