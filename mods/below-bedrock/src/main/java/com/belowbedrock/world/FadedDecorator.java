package com.belowbedrock.world;

import com.belowbedrock.BelowBedrock;
import com.belowbedrock.registry.ModLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The details of the faded world, added to each chunk after the vanilla features: signs that know
 * too much, leafless trees, 2x2 tunnels, sand pyramids in the sea, doors to nowhere, beds in the
 * grass, eternal fires, abandoned camps, buried chests, empty rooms, shafts down to the bedrock,
 * figures standing in the fog, and maps of Him nailed to trees.
 *
 * <p>Everything is deterministic per chunk and stays within the chunk and its direct neighbours,
 * which is what a feature may touch while the chunk is generated.</p>
 */
public final class FadedDecorator {
    /** About one chunk in eleven has something. */
    private static final float CHANCE = 0.09F;
    private static final Block[] SIGNS = {Blocks.OAK_SIGN, Blocks.SPRUCE_SIGN, Blocks.BIRCH_SIGN, Blocks.DARK_OAK_SIGN};
    private static final Block[] LOGS = {Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG, Blocks.DARK_OAK_LOG};

    private enum Feature {
        SIGN(18), DEAD_TREE(14), DOOR(7), BED(6), FLOATING_BLOCK(6), TORCH_RING(6), TUNNEL(6), PILLAR(6), CAMP(5),
        BURIED_CHEST(5), ROOM(5), STATUE(4), ETERNAL_FIRE(4), SHAFT(3);

        final int weight;

        Feature(int weight) {
            this.weight = weight;
        }
    }

    private FadedDecorator() {
    }

    public static void decorate(WorldGenLevel level, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        RandomSource random = RandomSource.create(level.getSeed() ^ chunkPos.x * 0x4F9939F508L ^ chunkPos.z * 0x1EF1565BD5L ^ 0x6661646564L);
        if (random.nextFloat() >= CHANCE) {
            return;
        }
        int x = chunkPos.getMinBlockX() + 4 + random.nextInt(8);
        int z = chunkPos.getMinBlockZ() + 4 + random.nextInt(8);
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        if (pos.getY() <= level.getMinBuildHeight() + 1 || pos.getY() >= level.getMaxBuildHeight() - 32) {
            return;
        }
        try {
            if (!level.getFluidState(pos.below()).isEmpty()) {
                if (random.nextInt(4) == 0) {
                    signIsland(level, random, pos);
                } else {
                    sandPyramid(level, random, x, z);
                }
                return;
            }
            switch (pick(random)) {
                case SIGN -> sign(level, random, pos, Signs.random(random));
                case DEAD_TREE -> deadTree(level, random, pos);
                case DOOR -> door(level, random, pos);
                case BED -> bed(level, random, pos);
                case FLOATING_BLOCK -> floatingBlock(level, random, pos);
                case TORCH_RING -> torchRing(level, random, pos);
                case TUNNEL -> tunnel(level, random, pos);
                case PILLAR -> pillar(level, random, pos);
                case CAMP -> camp(level, random, pos);
                case BURIED_CHEST -> buriedChest(level, random, pos);
                case ROOM -> room(level, random, pos);
                case STATUE -> statue(level, random, pos);
                case ETERNAL_FIRE -> eternalFire(level, random, pos);
                case SHAFT -> shaft(level, random, pos);
            }
        } catch (RuntimeException e) {
            BelowBedrock.LOGGER.warn("Could not decorate faded chunk {}", chunkPos, e);
        }
    }

    private static Feature pick(RandomSource random) {
        int total = 0;
        for (Feature feature : Feature.values()) {
            total += feature.weight;
        }
        int roll = random.nextInt(total);
        for (Feature feature : Feature.values()) {
            roll -= feature.weight;
            if (roll < 0) {
                return feature;
            }
        }
        return Feature.SIGN;
    }

    // ------------------------------------------------------------------ helpers

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static boolean free(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return (state.isAir() || state.canBeReplaced()) && level.getFluidState(pos).isEmpty();
    }

    private static boolean ground(WorldGenLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getFluidState(below).isEmpty();
    }

    /** The first free block above the ground in this column (only inside the chunk being decorated). */
    private static BlockPos surface(WorldGenLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static Direction horizontal(RandomSource random) {
        return Direction.Plane.HORIZONTAL.getRandomDirection(random);
    }

    private static void writeSign(WorldGenLevel level, BlockPos pos, Component[] lines) {
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText text = new SignText();
            for (int i = 0; i < 4; i++) {
                text = text.setMessage(i, lines[i]);
            }
            CompoundTag tag = new CompoundTag();
            SignText.DIRECT_CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), text)
                    .result().ifPresent(encoded -> tag.put("front_text", encoded));
            tag.putBoolean("is_waxed", true);
            // Loading the text instead of setting it: setText() would try to notify a world that does not exist yet.
            sign.loadWithComponents(tag, level.registryAccess());
        }
    }

    private static void chest(WorldGenLevel level, RandomSource random, BlockPos pos, Direction facing, ResourceKey<LootTable> loot) {
        set(level, pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
        RandomizableContainer.setBlockEntityLootTable(level, random, pos, loot);
    }

    /** An item frame holding one of His maps, hanging on the block behind {@code pos}. */
    private static void mapFrame(WorldGenLevel level, RandomSource random, BlockPos pos, Direction facing) {
        if (!free(level, pos)) {
            return;
        }
        ItemFrame frame = new ItemFrame(level.getLevel(), pos, facing);
        frame.setSilent(true); // no sound: there is no world to play it in yet
        frame.setItem(FadedMaps.placeholder(FadedMaps.random(random)), false);
        frame.setSilent(false);
        level.addFreshEntity(frame);
    }

    // ------------------------------------------------------------------ features

    private static void sign(WorldGenLevel level, RandomSource random, BlockPos pos, Component[] text) {
        if (!ground(level, pos) || !free(level, pos)) {
            return;
        }
        Block sign = SIGNS[random.nextInt(SIGNS.length)];
        set(level, pos, sign.defaultBlockState().setValue(StandingSignBlock.ROTATION, random.nextInt(16)));
        writeSign(level, pos, text);
    }

    /** One block of dirt in the sea, and a sign on it. */
    private static void signIsland(WorldGenLevel level, RandomSource random, BlockPos pos) {
        set(level, pos.below(), Blocks.DIRT.defaultBlockState());
        set(level, pos, SIGNS[random.nextInt(SIGNS.length)].defaultBlockState().setValue(StandingSignBlock.ROTATION, random.nextInt(16)));
        writeSign(level, pos, Signs.random(random));
    }

    /** A stepped sand pyramid rising out of the water. */
    private static void sandPyramid(WorldGenLevel level, RandomSource random, int x, int z) {
        int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        int top = level.getSeaLevel() + 2 + random.nextInt(3);
        if (top - floor > 40) {
            return;
        }
        for (int y = floor; y <= top; y++) {
            int radius = Math.min(4, top - y);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    set(level, new BlockPos(x + dx, y, z + dz), Blocks.SAND.defaultBlockState());
                }
            }
        }
    }

    /** A tree with no leaves. */
    private static void deadTree(WorldGenLevel level, RandomSource random, BlockPos pos) {
        if (!ground(level, pos)) {
            return;
        }
        BlockState log = LOGS[random.nextInt(LOGS.length)].defaultBlockState();
        int height = 4 + random.nextInt(4);
        for (int i = 0; i < height; i++) {
            if (!free(level, pos.above(i))) {
                return;
            }
            set(level, pos.above(i), log);
        }
        int branches = 1 + random.nextInt(3);
        for (int b = 0; b < branches; b++) {
            Direction side = horizontal(random);
            BlockPos start = pos.above(height - 1 - random.nextInt(2));
            int length = 1 + random.nextInt(2);
            for (int i = 1; i <= length; i++) {
                BlockPos branch = start.relative(side, i).above(i == length ? 1 : 0);
                if (free(level, branch)) {
                    set(level, branch, log.setValue(RotatedPillarBlock.AXIS, i == length ? Direction.Axis.Y : side.getAxis()));
                }
            }
        }
        if (random.nextInt(3) == 0) {
            Direction facing = horizontal(random);
            mapFrame(level, random, pos.above(1).relative(facing), facing);
        }
    }

    /** A door standing in the grass, leading nowhere. */
    private static void door(WorldGenLevel level, RandomSource random, BlockPos pos) {
        if (!ground(level, pos) || !free(level, pos) || !free(level, pos.above())) {
            return;
        }
        Block[] doors = {Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.DARK_OAK_DOOR, Blocks.IRON_DOOR};
        BlockState door = doors[random.nextInt(doors.length)].defaultBlockState()
                .setValue(DoorBlock.FACING, horizontal(random))
                .setValue(DoorBlock.OPEN, random.nextInt(3) == 0);
        set(level, pos, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, pos.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** A made bed in the middle of nowhere. */
    private static void bed(WorldGenLevel level, RandomSource random, BlockPos pos) {
        Direction facing = horizontal(random);
        BlockPos head = pos.relative(facing);
        if (!ground(level, pos) || !ground(level, head) || !free(level, pos) || !free(level, head)) {
            return;
        }
        Block bed = random.nextBoolean() ? Blocks.WHITE_BED : Blocks.RED_BED;
        BlockState state = bed.defaultBlockState().setValue(BedBlock.FACING, facing);
        set(level, pos, state.setValue(BedBlock.PART, BedPart.FOOT));
        set(level, head, state.setValue(BedBlock.PART, BedPart.HEAD));
    }

    private static void floatingBlock(WorldGenLevel level, RandomSource random, BlockPos pos) {
        Block[] blocks = {Blocks.DIRT, Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.GLASS, Blocks.WHITE_WOOL, Blocks.GRASS_BLOCK};
        BlockPos at = pos.above(4 + random.nextInt(7));
        if (free(level, at)) {
            set(level, at, blocks[random.nextInt(blocks.length)].defaultBlockState());
        }
    }

    /** Eight torches in a circle, around nothing (or around a redstone torch). */
    private static void torchRing(WorldGenLevel level, RandomSource random, BlockPos center) {
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            BlockPos at = surface(level, center.getX() + (int) Math.round(Math.cos(angle) * 3), center.getZ() + (int) Math.round(Math.sin(angle) * 3));
            if (ground(level, at) && free(level, at)) {
                set(level, at, Blocks.TORCH.defaultBlockState());
            }
        }
        if (random.nextBoolean() && ground(level, center) && free(level, center)) {
            set(level, center, Blocks.REDSTONE_TORCH.defaultBlockState());
        }
    }

    /** A perfectly straight 2x2 tunnel dug into the nearest hill. */
    private static void tunnel(WorldGenLevel level, RandomSource random, BlockPos pos) {
        Direction best = null;
        int bestRise = 2;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos ahead = pos.relative(direction, 7);
            int rise = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, ahead.getX(), ahead.getZ()) - pos.getY();
            if (rise > bestRise) {
                best = direction;
                bestRise = rise;
            }
        }
        if (best == null) {
            pillar(level, random, pos);
            return;
        }
        Direction side = best.getClockWise();
        int length = 10 + random.nextInt(7);
        for (int i = 0; i < length; i++) {
            BlockPos step = pos.relative(best, i);
            for (int w = 0; w < 2; w++) {
                for (int h = 0; h < 2; h++) {
                    BlockPos carve = step.relative(side, w).above(h);
                    if (!level.getBlockState(carve).isAir() && level.getFluidState(carve).isEmpty()) {
                        set(level, carve, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    /** A one-block pillar, as if someone had climbed it, with a torch on top. */
    private static void pillar(WorldGenLevel level, RandomSource random, BlockPos pos) {
        if (!ground(level, pos)) {
            return;
        }
        BlockState block = random.nextBoolean() ? Blocks.DIRT.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
        int height = 10 + random.nextInt(15);
        for (int i = 0; i < height; i++) {
            if (!free(level, pos.above(i))) {
                return;
            }
            set(level, pos.above(i), block);
        }
        if (free(level, pos.above(height))) {
            set(level, pos.above(height), Blocks.TORCH.defaultBlockState());
        }
    }

    /** An abandoned camp: cold campfire, two log benches, a bed, a chest with someone's diary. */
    private static void camp(WorldGenLevel level, RandomSource random, BlockPos center) {
        if (!ground(level, center) || !free(level, center)) {
            return;
        }
        set(level, center, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        Direction facing = horizontal(random);
        for (Direction side : new Direction[]{facing, facing.getOpposite()}) {
            BlockPos bench = surface(level, center.getX() + side.getStepX() * 2, center.getZ() + side.getStepZ() * 2);
            if (ground(level, bench) && free(level, bench)) {
                set(level, bench, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, side.getClockWise().getAxis()));
            }
        }
        Direction chestSide = facing.getClockWise();
        BlockPos chestPos = surface(level, center.getX() + chestSide.getStepX() * 2, center.getZ() + chestSide.getStepZ() * 2);
        if (ground(level, chestPos) && free(level, chestPos)) {
            chest(level, random, chestPos, chestSide.getOpposite(), ModLoot.CAMP_CHEST);
        }
        Direction bedSide = chestSide.getOpposite();
        BlockPos foot = surface(level, center.getX() + bedSide.getStepX() * 3, center.getZ() + bedSide.getStepZ() * 3);
        BlockPos head = foot.relative(bedSide);
        if (ground(level, foot) && ground(level, head) && free(level, foot) && free(level, head) && head.getY() == foot.getY()) {
            BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, bedSide);
            set(level, foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
            set(level, head, bed.setValue(BedBlock.PART, BedPart.HEAD));
        }
        if (random.nextBoolean()) {
            BlockPos signPos = surface(level, center.getX() + facing.getStepX() * 3 + chestSide.getStepX() * 2,
                    center.getZ() + facing.getStepZ() * 3 + chestSide.getStepZ() * 2);
            sign(level, random, signPos, Signs.random(random));
        }
    }

    /** "HERE", and three blocks below, a chest. */
    private static void buriedChest(WorldGenLevel level, RandomSource random, BlockPos pos) {
        if (!ground(level, pos) || !free(level, pos)) {
            return;
        }
        sign(level, random, pos, Signs.message("here", 1));
        BlockPos chestPos = pos.below(3);
        if (level.getFluidState(chestPos).isEmpty()) {
            chest(level, random, chestPos, horizontal(random), ModLoot.BURIED_CHEST);
        }
    }

    /** A sealed room standing in a field: yellow "backrooms", all white, or someone's bedroom. */
    private static void room(WorldGenLevel level, RandomSource random, BlockPos pos) {
        if (!ground(level, pos)) {
            return;
        }
        int style = random.nextInt(3);
        BlockState wall = switch (style) {
            case 0 -> Blocks.YELLOW_TERRACOTTA.defaultBlockState();
            case 1 -> Blocks.WHITE_CONCRETE.defaultBlockState();
            default -> Blocks.OAK_PLANKS.defaultBlockState();
        };
        BlockState floor = switch (style) {
            case 0 -> Blocks.YELLOW_WOOL.defaultBlockState();
            case 1 -> Blocks.SMOOTH_QUARTZ.defaultBlockState();
            default -> Blocks.SPRUCE_PLANKS.defaultBlockState();
        };
        BlockState ceiling = style == 2 ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState light = style == 2 ? Blocks.GLOWSTONE.defaultBlockState() : Blocks.SEA_LANTERN.defaultBlockState();
        int r = 4;
        int floorY = pos.getY() - 1;
        int top = floorY + 5;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                // foundation down to the ground, so it never floats
                for (int y = floorY - 1; y > floorY - 8; y--) {
                    BlockPos below = new BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
                    if (!free(level, below)) {
                        break;
                    }
                    set(level, below, wall);
                }
                for (int y = floorY; y <= top; y++) {
                    BlockPos at = new BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
                    BlockState state;
                    if (y == floorY) {
                        state = floor;
                    } else if (y == top) {
                        state = (!edge && dx % 3 == 0 && dz % 3 == 0) ? light : ceiling;
                    } else {
                        state = edge ? wall : Blocks.AIR.defaultBlockState();
                    }
                    set(level, at, state);
                }
            }
        }
        Direction doorSide = horizontal(random);
        BlockPos doorPos = pos.relative(doorSide, r);
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, doorSide.getOpposite());
        set(level, doorPos, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, doorPos.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        Direction back = doorSide.getOpposite();
        // a map of Him on the far wall, and a chair facing it
        mapFrame(level, random, pos.relative(back, r - 1).above(1), doorSide);
        BlockPos chair = pos.relative(back, r - 3);
        set(level, chair, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, doorSide));
        Direction side = doorSide.getClockWise();
        BlockPos corner = pos.relative(back, r - 1).relative(side, r - 1);
        chest(level, random, corner, side.getOpposite(), ModLoot.ROOM_CHEST);
        if (style == 2) {
            BlockPos foot = pos.relative(side.getOpposite(), r - 2);
            BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, back);
            set(level, foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
            set(level, foot.relative(back), bed.setValue(BedBlock.PART, BedPart.HEAD));
        }
        BlockPos signPos = pos.relative(side.getOpposite(), r - 1).above(1);
        set(level, signPos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, side));
        writeSign(level, signPos, Signs.random(random));
    }

    /** From far away, in the fog, it looks like someone standing there. */
    private static void statue(WorldGenLevel level, RandomSource random, BlockPos pos) {
        if (!ground(level, pos) || !free(level, pos) || !free(level, pos.above())) {
            return;
        }
        ArmorStand stand = new ArmorStand(level.getLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        stand.setSilent(true);
        stand.setYRot(random.nextFloat() * 360.0F);
        stand.setYHeadRot(stand.getYRot());
        stand.setYBodyRot(stand.getYRot());
        stand.setNoBasePlate(true);
        stand.setShowArms(true);
        stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(random.nextBoolean() ? Items.SKELETON_SKULL : Items.PLAYER_HEAD));
        stand.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE));
        stand.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS));
        stand.setItemSlot(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS));
        stand.setSilent(false);
        level.addFreshEntity(stand);
    }

    private static ItemStack dyed(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0xE6E3DA, false));
        return stack;
    }

    /** A fire on netherrack that has been burning forever. */
    private static void eternalFire(WorldGenLevel level, RandomSource random, BlockPos pos) {
        if (!ground(level, pos)) {
            return;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos at = surface(level, pos.getX() + dx, pos.getZ() + dz);
                if (!level.getBlockState(at).isAir() && free(level, at)) {
                    set(level, at, Blocks.AIR.defaultBlockState()); // no grass to spread to
                }
                BlockPos groundPos = at.below();
                if (level.getBlockState(groundPos).is(Blocks.GRASS_BLOCK)) {
                    set(level, groundPos, Blocks.COARSE_DIRT.defaultBlockState());
                }
            }
        }
        set(level, pos.below(), Blocks.NETHERRACK.defaultBlockState());
        set(level, pos, Blocks.FIRE.defaultBlockState());
    }

    /** A one-block shaft straight down to the bedrock, a ladder, and water at the bottom. */
    private static void shaft(WorldGenLevel level, RandomSource random, BlockPos pos) {
        if (!ground(level, pos)) {
            return;
        }
        Direction ladderWall = horizontal(random);
        int bottom = level.getMinBuildHeight() + 5;
        BlockPos.MutableBlockPos at = pos.below().mutable();
        for (int y = pos.getY() - 1; y > bottom; y--) {
            at.setY(y);
            if (level.getBlockState(at).is(Blocks.BEDROCK)) {
                break;
            }
            set(level, at, y == bottom + 1 ? Blocks.WATER.defaultBlockState()
                    : Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, ladderWall.getOpposite()));
            BlockPos wallPos = at.relative(ladderWall);
            if (level.getBlockState(wallPos).isAir() || !level.getFluidState(wallPos).isEmpty()) {
                set(level, wallPos, Blocks.COBBLESTONE.defaultBlockState());
            }
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int k = -1; k <= 1; k++) {
                BlockPos rim = pos.relative(side).relative(side.getClockWise(), k);
                if (free(level, rim)) {
                    set(level, rim, Blocks.COBBLESTONE.defaultBlockState());
                }
            }
        }
        BlockPos torch = pos.relative(ladderWall.getOpposite(), 2);
        if (free(level, torch) && ground(level, torch)) {
            set(level, torch, Blocks.TORCH.defaultBlockState());
        }
        Direction signSide = ladderWall.getClockWise();
        BlockPos signPos = pos.relative(signSide, 2);
        sign(level, random, signPos, Signs.message("dig", 3));
    }
}
