package com.belowbedrock.mixin;

import com.belowbedrock.world.Faded;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Villages in the faded world have no trees or flowers either; only their piles of hay and snow are left. */
@Mixin(FeaturePoolElement.class)
public abstract class FeaturePoolElementMixin {
    @Shadow
    @Final
    private Holder<PlacedFeature> feature;

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void belowbedrock$noTrees(StructureTemplateManager templates, WorldGenLevel level, StructureManager structures,
                                      ChunkGenerator generator, BlockPos pos, BlockPos pivot, Rotation rotation, BoundingBox box,
                                      RandomSource random, LiquidSettings liquids, boolean keepJigsaws, CallbackInfoReturnable<Boolean> cir) {
        if (Faded.is(level.getLevel()) && this.feature.value().feature().value().feature() != Feature.BLOCK_PILE) {
            cir.setReturnValue(true);
        }
    }
}
