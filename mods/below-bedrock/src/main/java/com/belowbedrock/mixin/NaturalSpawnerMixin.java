package com.belowbedrock.mixin;

import com.belowbedrock.world.Faded;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nothing spawns in the faded world, day or night. */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    @Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
    private static void belowbedrock$empty(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state,
                                           boolean friendlies, boolean enemies, boolean passives, CallbackInfo ci) {
        if (Faded.is(level)) {
            ci.cancel();
        }
    }
}
