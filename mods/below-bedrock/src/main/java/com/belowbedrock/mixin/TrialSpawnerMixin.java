package com.belowbedrock.mixin;

import com.belowbedrock.world.Faded;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Trial chambers are empty too. */
@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerMixin {
    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
    private void belowbedrock$silent(ServerLevel level, BlockPos pos, boolean ominous, CallbackInfo ci) {
        if (Faded.is(level)) {
            ci.cancel();
        }
    }
}
