package com.belowbedrock.world;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Nothing grows in the faded world. Trees, grass, flowers and the rest of the vegetation are never
 * generated there (see the mixins); this pass removes what is left in a freshly decorated chunk:
 * plants that come with structures (crops, flower pots, vines...) and the grass on the ground,
 * which turns into bare dirt.
 */
public final class FadedCleaner {
    /** Plants that are not in the block tags used below. */
    private static final Set<Block> PLANTS = Set.of(Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN,
            Blocks.DEAD_BUSH, Blocks.VINE, Blocks.GLOW_LICHEN, Blocks.HANGING_ROOTS, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS,
            Blocks.KELP, Blocks.KELP_PLANT, Blocks.SEA_PICKLE, Blocks.LILY_PAD, Blocks.SUGAR_CANE, Blocks.CACTUS, Blocks.BAMBOO,
            Blocks.BAMBOO_SAPLING, Blocks.SWEET_BERRY_BUSH, Blocks.COCOA, Blocks.MOSS_CARPET, Blocks.AZALEA, Blocks.FLOWERING_AZALEA,
            Blocks.BIG_DRIPLEAF, Blocks.BIG_DRIPLEAF_STEM, Blocks.SMALL_DRIPLEAF, Blocks.SPORE_BLOSSOM, Blocks.BROWN_MUSHROOM,
            Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM, Blocks.PITCHER_PLANT,
            Blocks.ATTACHED_MELON_STEM, Blocks.ATTACHED_PUMPKIN_STEM, Blocks.MANGROVE_ROOTS);
    /** Living ground that becomes plain dirt. */
    private static final Set<Block> LIVING_GROUND = Set.of(Blocks.GRASS_BLOCK, Blocks.MYCELIUM, Blocks.MOSS_BLOCK, Blocks.ROOTED_DIRT);

    private FadedCleaner() {
    }

    private static boolean isTarget(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.AIR || block == Blocks.STONE || block == Blocks.WATER || block == Blocks.DEEPSLATE) {
            return false; // the common case, quickly
        }
        return PLANTS.contains(block) || LIVING_GROUND.contains(block) || state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS) || state.is(BlockTags.CAVE_VINES)
                || (state.is(BlockTags.FLOWER_POTS) && block != Blocks.FLOWER_POT);
    }

    private static BlockState replacement(BlockState state) {
        Block block = state.getBlock();
        if (LIVING_GROUND.contains(block)) {
            return Blocks.DIRT.defaultBlockState();
        }
        if (state.is(BlockTags.FLOWER_POTS)) {
            return Blocks.FLOWER_POT.defaultBlockState(); // the pot stays, empty
        }
        boolean underwater = block == Blocks.SEAGRASS || block == Blocks.TALL_SEAGRASS || block == Blocks.KELP || block == Blocks.KELP_PLANT
                || (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED));
        return underwater ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    /** Removes every plant from {@code chunk} (one section at a time, skipping sections that have none). */
    public static void clean(WorldGenLevel level, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(FadedCleaner::isTarget)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (isTarget(state)) {
                            pos.set(chunkPos.getMinBlockX() + x, baseY + y, chunkPos.getMinBlockZ() + z);
                            level.setBlock(pos, replacement(state), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
    }
}
