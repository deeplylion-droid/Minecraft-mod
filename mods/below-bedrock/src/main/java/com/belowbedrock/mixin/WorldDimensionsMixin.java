package com.belowbedrock.mixin;

import com.belowbedrock.BelowBedrock;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Lifecycle;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla calls any world with an extra dimension "experimental" and warns every time it is
 * loaded. The faded world is the overworld generator with the overworld settings, so it is
 * exactly as stable as the overworld: no warning because of it.
 */
@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsMixin {
    private static final ResourceKey<LevelStem> FADED = ResourceKey.create(Registries.LEVEL_STEM, BelowBedrock.id("faded"));

    @Shadow
    @Final
    private Map<ResourceKey<LevelStem>, LevelStem> dimensions;

    @Inject(method = "checkStability", at = @At("HEAD"), cancellable = true)
    private static void belowbedrock$fadedIsStable(ResourceKey<LevelStem> key, LevelStem stem, CallbackInfoReturnable<Lifecycle> cir) {
        if (FADED.equals(key)) {
            cir.setReturnValue(Lifecycle.stable());
        }
    }

    /** The "only the three vanilla dimensions" count does not count ours. */
    @ModifyExpressionValue(method = "bake", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int belowbedrock$notCountingFaded(int size, @Local(argsOnly = true) Registry<LevelStem> registry) {
        return registry.containsKey(FADED) || this.dimensions.containsKey(FADED) ? size - 1 : size;
    }
}
