package com.filorosso.world;

import com.filorosso.FiloRosso;
import com.filorosso.data.Couple;
import com.filorosso.data.Home;
import com.filorosso.data.ModData;
import com.filorosso.server.Bonds;
import com.filorosso.sound.ModSounds;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "Il Teatro": the Burattinaio's dimension, an empty theatre hanging in the dark. A stage of black
 * boards with a red curtain, the stalls full of black spectators who turn their heads to follow you,
 * a single bare lamp left on in the middle of the stage (the ghost light). Turn it off and the show
 * begins. The place is built by code the first time someone goes up there.
 *
 * Layout (x across, +z towards the back of the stage): stage boards x -20..20, z -12..12, top at
 * y 64; the curtain at z -11; the stalls from z -14 back to -50, floor at y 60 rising in steps.
 */
public final class Theatre {
    private Theatre() {
    }

    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, FiloRosso.id("teatro"));
    /** Bump to rebuild the place in worlds that already have it. */
    private static final int VERSION = 1;

    public static final int STAGE = 64;
    public static final int STAGE_X = 20;
    public static final int STAGE_FRONT = -12;
    public static final int STAGE_BACK = 12;
    public static final int CURTAIN_Z = -11;
    public static final int CURTAIN_TOP = 90;
    public static final int FLOOR = 60;
    public static final BlockPos GHOST_LIGHT = new BlockPos(0, STAGE, 3);
    /** Where you arrive: the back of the stalls, looking at the stage. */
    public static final Vec3 ENTRY = new Vec3(0.5, FLOOR + 6, -48.5);
    /** Where the Burattinaio hangs when it is not hunting someone. */
    public static final Vec3 BOSS_HOME = new Vec3(0.5, STAGE, 4.5);

    /** Seats of the stalls (where the spectators sit). */
    private static List<BlockPos> seats;

    // ------------------------------------------------------------------ saved state

    /** On the Theatre's own level: which version of the place is built. */
    public static final class Data extends SavedData {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("version", 0).forGetter(d -> d.version)
        ).apply(i, Data::new));
        public static final SavedDataType<Data> TYPE = new SavedDataType<>(FiloRosso.id("theatre"), Data::new, CODEC, null);

        public int version;

        public Data() {
            this(0);
        }

        public Data(int version) {
            this.version = version;
        }
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    public static boolean in(ServerPlayer p) {
        return p.level().dimension() == LEVEL;
    }

    /** Builds the place if it is not there yet (or is an older version). */
    public static void ensureBuilt(ServerLevel level) {
        Data data = level.getDataStorage().computeIfAbsent(Data.TYPE);
        if (data.version >= VERSION) {
            return;
        }
        long t0 = System.currentTimeMillis();
        build(level);
        data.version = VERSION;
        data.setDirty();
        FiloRosso.LOGGER.info("Filo Rosso: the Theatre was built in {} ms", System.currentTimeMillis() - t0);
    }

    // ------------------------------------------------------------------ building

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }

    private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    set(level, x, y, z, state);
                }
            }
        }
    }

    /** Floor height of the stalls at this z (they rise one block every four). */
    public static int stallsHeight(int z) {
        if (z > -17) {
            return 0;
        }
        int row = (-17 - z) / 2;
        return Math.min(6, row / 2);
    }

    /** A fold of velvet: three reds that alternate across the width. */
    private static BlockState velvet(int x, int y) {
        int k = Math.floorMod(x * 7 + (y / 9) * 3, 5);
        return k == 0 ? Blocks.DYED_TERRACOTTA.pick(DyeColor.RED).defaultBlockState()
                : k == 3 ? Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState() : Blocks.WOOL.pick(DyeColor.RED).defaultBlockState();
    }

    private static void build(ServerLevel level) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState boards = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState black = Blocks.WOOL.pick(DyeColor.BLACK).defaultBlockState();
        BlockState blackstone = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState gilded = Blocks.GILDED_BLACKSTONE.defaultBlockState();
        BlockState log = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        // clear the space (re-builds too)
        fill(level, -26, 56, -52, 26, 101, 15, air);

        // the stage: black boards, a dark front
        fill(level, -STAGE_X, STAGE - 1, STAGE_FRONT, STAGE_X, STAGE - 1, STAGE_BACK, boards);
        fill(level, -STAGE_X, STAGE - 1, STAGE_FRONT, STAGE_X, STAGE - 1, STAGE_FRONT, log.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                Direction.Axis.X));
        fill(level, -STAGE_X, FLOOR, STAGE_FRONT - 1, STAGE_X, STAGE - 2, STAGE_FRONT - 1, black);
        // the backdrop and the wings: black, with a wall of red velvet behind the stage
        fill(level, -21, STAGE, STAGE_BACK + 1, 21, 100, STAGE_BACK + 1, black);
        for (int x = -16; x <= 16; x++) {
            for (int y = STAGE; y <= 92; y++) {
                set(level, x, y, STAGE_BACK + 1, velvet(x, y));
            }
        }
        fill(level, -21, STAGE, STAGE_FRONT, -21, 100, STAGE_BACK + 1, black);
        fill(level, 21, STAGE, STAGE_FRONT, 21, 100, STAGE_BACK + 1, black);
        // the arch: pillars and a beam of black stone with gilded lines
        for (int side = -1; side <= 1; side += 2) {
            fill(level, side * 21, FLOOR - 1, -14, side * 23, 99, -13, blackstone);
            for (int y = FLOOR + 5; y < 99; y += 7) {
                fill(level, side * 21, y, -14, side * 23, y, -14, gilded);
            }
        }
        fill(level, -23, 95, -14, 23, 99, -13, blackstone);
        fill(level, -23, 95, -14, 23, 95, -14, gilded);
        // the valance and the side drapes of the curtain (always there)
        for (int x = -STAGE_X; x <= STAGE_X; x++) {
            for (int y = CURTAIN_TOP + 1; y <= 94; y++) {
                set(level, x, y, CURTAIN_Z, velvet(x, y));
            }
        }
        for (int x : new int[]{-20, -19, -18, 18, 19, 20}) {
            for (int y = STAGE; y <= CURTAIN_TOP; y++) {
                set(level, x, y, CURTAIN_Z, velvet(x, y));
            }
        }
        curtain(level, true);
        // footlights: red candles along the edge (not in front of the steps)
        for (int x = -18; x <= 18; x += 3) {
            if (Math.abs(x) > 3) {
                set(level, x, STAGE, STAGE_FRONT, Blocks.DYED_CANDLE.pick(DyeColor.RED).defaultBlockState().setValue(CandleBlock.CANDLES, 3)
                        .setValue(CandleBlock.LIT, true));
            }
        }
        ghostLight(level, true);

        // the stalls: a floor that rises towards the back, rows of seats, a red carpet in the aisle
        for (int z = -51; z <= -13; z++) {
            int h = stallsHeight(z);
            fill(level, -25, FLOOR - 1, z, 25, FLOOR - 1 + h, z, boards);
            for (int x = -2; x <= 2; x++) {
                set(level, x, FLOOR + h, z, Blocks.CARPET.pick(DyeColor.RED).defaultBlockState());
            }
        }
        seats = null;
        for (BlockPos seat : seats()) {
            set(level, seat.getX(), seat.getY(), seat.getZ(), Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        }
        // steps up to the stage, in the middle and on both sides
        for (int k = 0; k < 4; k++) {
            int z = -16 + k;
            int y = FLOOR + k;
            for (int x : new int[]{-2, -1, 0, 1, 2, -17, -16, 16, 17}) {
                fill(level, x, FLOOR - 1, z, x, y - 1, z, boards);
                set(level, x, y, z, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
            }
        }
        // walls round the stalls, black with dark wooden pilasters
        fill(level, -25, FLOOR - 1, -51, 25, 96, -51, black);
        for (int side = -1; side <= 1; side += 2) {
            fill(level, side * 25, FLOOR - 1, -51, side * 25, 96, -13, black);
            fill(level, side * 24, FLOOR - 1, -14, side * 24, 99, -13, blackstone);
            for (int z = -50; z <= -14; z += 6) {
                fill(level, side * 25, FLOOR - 1, z, side * 25, 96, z, log);
            }
        }
        // two chandeliers over the stalls
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 86; y <= 96; y++) {
                set(level, side * 12, y, -30, Blocks.IRON_CHAIN.defaultBlockState());
            }
            set(level, side * 12, 85, -30, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        lights(level, true);
    }

    /** Invisible lights: brighter on the stage before the show, dimmer while it is on. */
    public static void lights(ServerLevel level, boolean waiting) {
        lightLevels(level, waiting ? 9 : 7, waiting ? 6 : 4);
    }

    /** Sets the invisible lights of the stage and of the stalls (0 = out). */
    public static void lightLevels(ServerLevel level, int stage, int stalls) {
        for (int x = -15; x <= 15; x += 6) {
            for (int z = -8; z <= 10; z += 6) {
                set(level, x, STAGE + 2, z, stage > 0 ? Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, stage) : Blocks.AIR.defaultBlockState());
            }
        }
        for (int x = -20; x <= 20; x += 8) {
            for (int z = -46; z <= -18; z += 7) {
                set(level, x, FLOOR + stallsHeight(z) + 3, z,
                        stalls > 0 ? Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, stalls) : Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** Closes (or opens) the whole curtain at once. */
    public static void curtain(ServerLevel level, boolean closed) {
        for (int x = -17; x <= 17; x++) {
            curtainColumn(level, x, closed);
        }
    }

    /** One column of the curtain. */
    public static void curtainColumn(ServerLevel level, int x, boolean closed) {
        for (int y = STAGE; y <= CURTAIN_TOP; y++) {
            set(level, x, y, CURTAIN_Z, closed ? velvet(x, y) : Blocks.AIR.defaultBlockState());
        }
    }

    /** The ghost light: a bare lantern on an iron post in the middle of the stage. */
    public static void ghostLight(ServerLevel level, boolean on) {
        BlockPos p = GHOST_LIGHT;
        set(level, p.getX(), p.getY(), p.getZ(), on ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState());
        set(level, p.getX(), p.getY() + 1, p.getZ(), on ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState());
        set(level, p.getX(), p.getY() + 2, p.getZ(), on ? Blocks.LANTERN.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }

    public static boolean isGhostLight(BlockPos pos) {
        return pos.getX() == GHOST_LIGHT.getX() && pos.getZ() == GHOST_LIGHT.getZ() && pos.getY() >= GHOST_LIGHT.getY()
                && pos.getY() <= GHOST_LIGHT.getY() + 2;
    }

    /** Every seat of the stalls (stairs facing the stage). */
    public static List<BlockPos> seats() {
        if (seats == null) {
            List<BlockPos> list = new ArrayList<>();
            for (int r = 0; r < 14; r++) {
                int z = -18 - 2 * r;
                int y = FLOOR + stallsHeight(z);
                for (int x = -22; x <= 22; x++) {
                    if (Math.abs(x) >= 4) {
                        list.add(new BlockPos(x, y, z));
                    }
                }
            }
            seats = list;
        }
        return seats;
    }

    // ------------------------------------------------------------------ going up and coming back

    /** Pairs waiting to be lifted up (game time of the jump). */
    private record Lift(UUID player, long at, boolean up) {
    }

    private static final List<Lift> LIFTS = new ArrayList<>();
    private static final Set<UUID> LIFTING = new HashSet<>();

    /**
     * The golden heart, used with the partner close by: the red thread pulls both of you up into the
     * Theatre (or back home, if you are already there).
     */
    public static boolean useHeart(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Couple couple = Bonds.coupleOf(player);
        if (couple == null) {
            player.sendSystemMessage(Component.translatable("filorosso.theatre.single").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        ServerPlayer partner = Bonds.player(server, couple.other(player.getUUID()));
        boolean up = !in(player);
        if (partner == null || partner.level() != player.level() || partner.distanceTo(player) > 8.0) {
            player.sendSystemMessage(Component.translatable("filorosso.theatre.together").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (LIFTING.contains(player.getUUID()) || LIFTING.contains(partner.getUUID())) {
            return false;
        }
        long now = server.overworld().getGameTime();
        for (ServerPlayer p : new ServerPlayer[]{player, partner}) {
            LIFTING.add(p.getUUID());
            LIFTS.add(new Lift(p.getUUID(), now + 40, up));
            p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 1, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0, false, false));
            Bonds.hear(p, ModSounds.THEATRE_CALL, 1.0F, 1.0F);
            Bonds.title(p, Component.translatable(up ? "filorosso.theatre.up" : "filorosso.theatre.down").withStyle(ChatFormatting.DARK_RED),
                    Component.translatable(up ? "filorosso.theatre.up.sub" : "filorosso.theatre.down.sub").withStyle(ChatFormatting.GRAY), 10, 50, 20);
        }
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (LIFTS.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        List<Lift> due = new ArrayList<>();
        LIFTS.removeIf(l -> {
            if (now >= l.at()) {
                due.add(l);
                return true;
            }
            return false;
        });
        for (Lift l : due) {
            LIFTING.remove(l.player());
            ServerPlayer p = server.getPlayerList().getPlayer(l.player());
            if (p == null || !p.isAlive()) {
                continue;
            }
            if (l.up) {
                goUp(p);
            } else {
                goHome(p);
            }
        }
        // threads pulling the lifted ones up
        for (UUID id : LIFTING) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.level() instanceof ServerLevel level) {
                level.sendParticles(new DustParticleOptions(0xD8202A, 1.2F), p.getX(), p.getY() + 1.0 + level.getRandom().nextDouble() * 3.0, p.getZ(), 4,
                        0.05, 1.5, 0.05, 0.0);
            }
        }
    }

    /** Into the Theatre, at the back of the stalls. */
    public static void goUp(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        ServerLevel theatre = level(server);
        if (theatre == null) {
            FiloRosso.LOGGER.error("Filo Rosso: the Theatre dimension is missing");
            return;
        }
        ensureBuilt(theatre);
        if (!in(p)) {
            p.setAttached(ModData.HOME, new Home(p.level().dimension().identifier().toString(), p.getX(), p.getY(), p.getZ(), p.getYRot()));
        }
        double dx = (p.getRandom().nextDouble() - 0.5) * 3.0;
        p.teleportTo(theatre, ENTRY.x + dx, ENTRY.y, ENTRY.z, Set.of(), 0.0F, 5.0F, true);
        p.resetFallDistance();
        Stage.onEnter(theatre, p);
    }

    /** Back where you came from (or to your bed / the world spawn if that is lost). */
    public static void goHome(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        Home home = p.getAttached(ModData.HOME);
        ServerLevel target = null;
        if (home != null) {
            Identifier id = Identifier.tryParse(home.dimension());
            if (id != null) {
                target = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            }
        }
        if (target == null || target.dimension() == LEVEL) {
            net.minecraft.world.level.storage.LevelData.RespawnData spawn = server.getRespawnData();
            target = server.getLevel(spawn.dimension());
            if (target == null) {
                target = server.overworld();
            }
            BlockPos pos = spawn.pos();
            p.teleportTo(target, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), spawn.yaw(), 0.0F, true);
        } else {
            p.teleportTo(target, home.x(), home.y(), home.z(), Set.of(), home.yaw(), 0.0F, true);
        }
        p.resetFallDistance();
        p.removeAttached(ModData.HOME);
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.0, p.getZ(), 12, 0.5, 0.6, 0.5, 0.1);
    }
}
