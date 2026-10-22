package com.filorosso.mixin;

import com.filorosso.FiloRosso;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla calls a world with an extra dimension "experimental" and warns every time it is loaded.
 * The Theatre is an empty flat world with a building in it: as stable as anything, so no warning.
 */
@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsMixin {
    private static final ResourceKey<LevelStem> THEATRE = ResourceKey.create(Registries.LEVEL_STEM, FiloRosso.id("teatro"));

    @Inject(method = "checkStability", at = @At("HEAD"), cancellable = true)
    private static void filorosso$theatreIsStable(ResourceKey<LevelStem> key, LevelStem stem, CallbackInfoReturnable<Lifecycle> cir) {
        if (THEATRE.equals(key)) {
            cir.setReturnValue(Lifecycle.stable());
        }
    }
}
