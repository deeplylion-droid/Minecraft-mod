package com.belowbedrock.mixin;

import com.belowbedrock.world.Faded;
import com.belowbedrock.world.FadedCleaner;
import com.belowbedrock.world.FadedDecorator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In the faded world nothing grows: the vegetation step of the biomes (trees, grass, flowers,
 * mushrooms, kelp, cactus...) and the grass disks are skipped. After the vanilla features, what
 * is left of the plants is removed, and then come the things that should not be there.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Unique
    private static final ResourceKey<PlacedFeature> DISK_GRASS = ResourceKey.create(Registries.PLACED_FEATURE,
            ResourceLocation.withDefaultNamespace("disk_grass"));

    @Shadow
    @Final
    private Supplier<List<FeatureSorter.StepFeatureData>> featuresPerStep;

    @Unique
    private volatile Set<PlacedFeature> belowbedrock$plants;

    @WrapOperation(method = "applyBiomeDecoration", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;placeWithBiomeCheck(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean belowbedrock$nothingGrows(PlacedFeature feature, WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                                              BlockPos pos, Operation<Boolean> original) {
        if (Faded.is(level.getLevel()) && this.belowbedrock$plants(level).contains(feature)) {
            return false;
        }
        return original.call(feature, level, generator, random, pos);
    }

    @Unique
    private Set<PlacedFeature> belowbedrock$plants(WorldGenLevel level) {
        Set<PlacedFeature> plants = this.belowbedrock$plants;
        if (plants == null) {
            plants = Collections.newSetFromMap(new IdentityHashMap<>());
            List<FeatureSorter.StepFeatureData> steps = this.featuresPerStep.get();
            int vegetation = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
            if (vegetation < steps.size()) {
                plants.addAll(steps.get(vegetation).features());
            }
            level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).getOptional(DISK_GRASS).ifPresent(plants::add);
            this.belowbedrock$plants = plants;
        }
        return plants;
    }

    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void belowbedrock$decorate(WorldGenLevel level, ChunkAccess chunk, StructureManager structures, CallbackInfo ci) {
        if (Faded.is(level.getLevel())) {
            FadedCleaner.clean(level, chunk);
            FadedDecorator.decorate(level, chunk);
        }
    }
}
