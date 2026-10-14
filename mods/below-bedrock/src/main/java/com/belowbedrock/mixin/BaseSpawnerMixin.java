package com.belowbedrock.mixin;

import com.belowbedrock.world.Faded;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Spawners still spin in the faded world, but nothing ever comes out. */
@Mixin(BaseSpawner.class)
public abstract class BaseSpawnerMixin {
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void belowbedrock$silent(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        if (Faded.is(level)) {
            ci.cancel();
        }
    }
}
