package com.belowbedrock.haunt;

import com.belowbedrock.BelowBedrock;
import com.belowbedrock.entity.Inhabitant;
import com.belowbedrock.entity.Inhabitant.Mode;
import com.belowbedrock.fall.VoidFall;
import com.belowbedrock.net.ScreenFxPayload;
import com.belowbedrock.net.ScreenFxPayload.Fx;
import com.belowbedrock.registry.ModAttachments;
import com.belowbedrock.registry.ModGameRules;
import com.belowbedrock.util.SoundFx;
import com.belowbedrock.world.Diaries;
import com.belowbedrock.world.Faded;
import com.belowbedrock.world.FadedMaps;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Decides, for each player in the faded world, when something strange happens: small things often
 * (sounds behind you, torches going out, doors opening, whispers, glitches) and, every few minutes,
 * Him. The longer you stay, the more often and the worse. Everything is per player, so in
 * multiplayer everyone lives a different story (but everyone can see Him).
 */
public final class Director {
    public static final ResourceKey<DamageType> TAKEN = ResourceKey.create(Registries.DAMAGE_TYPE, BelowBedrock.id("taken"));
    /** Minutes in the faded world before the dread is at its worst. */
    private static final float DREAD_MINUTES = 8.0F;
    private static final Map<UUID, Haunt> HAUNTS = new HashMap<>();

    private Director() {
    }

    /** What the director remembers about one player. */
    private static final class Haunt {
        int clock;
        int ticksHere;
        int nextMinor = 700;
        int nextMajor = 1300;
        boolean firstSighting = true;
        boolean jukeboxDone;
        int homeTimer = 20 * 60 * 6;
        @Nullable
        UUID him;
        final ArrayDeque<Vec3> trail = new ArrayDeque<>();
        final List<Scheduled> later = new ArrayList<>();

        void schedule(int delay, Consumer<ServerPlayer> action) {
            this.later.add(new Scheduled(this.clock + delay, action));
        }

        float dread() {
            return Mth.clamp(this.ticksHere / (20.0F * 60.0F * DREAD_MINUTES), 0.0F, 1.0F);
        }
    }

    private record Scheduled(int at, Consumer<ServerPlayer> action) {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Director::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HAUNTS.remove(handler.player.getUUID()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> HAUNTS.remove(newPlayer.getUUID()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            Haunt haunt = HAUNTS.get(player.getUUID());
            if (haunt != null) {
                haunt.ticksHere = 0;
                haunt.trail.clear();
            }
        });
    }

    private static Haunt haunt(ServerPlayer player) {
        return HAUNTS.computeIfAbsent(player.getUUID(), id -> new Haunt());
    }

    /** Called when a player wakes up on either side of the fall. */
    public static void onArrive(ServerPlayer player) {
        Haunt haunt = haunt(player);
        haunt.ticksHere = 0;
        haunt.trail.clear();
        haunt.later.clear();
        if (Faded.is(player.level())) {
            // Let them look around first. It is quiet. Too quiet.
            haunt.nextMinor = 700 + player.getRandom().nextInt(300);
            haunt.nextMajor = 1300 + player.getRandom().nextInt(500);
            haunt.firstSighting = true;
            haunt.jukeboxDone = false;
        } else if (player.server.getGameRules().getBoolean(ModGameRules.SIGHTINGS)) {
            // Back home. Are you, though?
            haunt.schedule(600, p -> p.sendSystemMessage(Component.translatable("multiplayer.player.joined",
                    Component.translatable("entity.belowbedrock.inhabitant")).withStyle(ChatFormatting.YELLOW)));
            haunt.homeTimer = 20 * 60 * 4;
        }
    }

    private static void tick(MinecraftServer server) {
        boolean enabled = server.getGameRules().getBoolean(ModGameRules.HAUNTINGS);
        boolean sightings = server.getGameRules().getBoolean(ModGameRules.SIGHTINGS);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Haunt haunt = haunt(player);
            haunt.clock++;
            runScheduled(player, haunt);
            if (!enabled || player.isSpectator() || !player.isAlive() || VoidFall.isFalling(player)) {
                continue;
            }
            ServerLevel level = player.serverLevel();
            if (Faded.is(level)) {
                tickFaded(player, level, haunt);
            } else if (sightings && level.dimension() == Level.OVERWORLD && ModAttachments.flag(player, ModAttachments.RETURNED)) {
                tickHome(player, level, haunt);
            }
        }
    }

    private static void runScheduled(ServerPlayer player, Haunt haunt) {
        if (haunt.later.isEmpty()) {
            return;
        }
        List<Scheduled> due = new ArrayList<>();
        for (Iterator<Scheduled> it = haunt.later.iterator(); it.hasNext(); ) {
            Scheduled task = it.next();
            if (task.at() <= haunt.clock) {
                due.add(task);
                it.remove();
            }
        }
        for (Scheduled task : due) {
            try {
                task.action().accept(player);
            } catch (RuntimeException e) {
                BelowBedrock.LOGGER.warn("Haunting event failed", e);
            }
        }
    }

    // ------------------------------------------------------------------ the faded world

    private static void tickFaded(ServerPlayer player, ServerLevel level, Haunt haunt) {
        haunt.ticksHere++;
        if (haunt.ticksHere % 10 == 0) {
            haunt.trail.addLast(player.position());
            if (haunt.trail.size() > 8) {
                haunt.trail.removeFirst();
            }
        }
        float dread = haunt.dread();
        RandomSource random = player.getRandom();
        if (--haunt.nextMinor <= 0) {
            minor(player, level, haunt, dread);
            haunt.nextMinor = (int) ((500 + random.nextInt(900)) * (1.0F - 0.5F * dread));
        }
        if (current(level, haunt) == null && --haunt.nextMajor <= 0) {
            major(player, level, haunt, dread);
            haunt.nextMajor = (int) ((1700 + random.nextInt(2300)) * (1.0F - 0.45F * dread));
        }
    }

    @Nullable
    private static Inhabitant current(ServerLevel level, Haunt haunt) {
        if (haunt.him == null) {
            return null;
        }
        Entity entity = level.getEntity(haunt.him);
        if (entity instanceof Inhabitant him && him.isAlive()) {
            return him;
        }
        haunt.him = null;
        return null;
    }

    private enum Minor {
        SOUND_BEHIND(7, 0.0F), FOOTSTEPS(4, 0.0F), CAVE(3, 0.0F), WHISPER(2, 0.2F), ACTIONBAR(2, 0.0F), GLITCH(3, 0.0F),
        FAKE_LEAVE(1, 0.3F), TORCHES(3, 0.1F), DOOR(2, 0.0F), FAKE_BLOCKS(2, 0.15F), NAME_SIGN(1, 0.4F),
        RUBBER_BAND(1, 0.3F), DARKNESS(1, 0.5F), JUKEBOX(1, 0.2F), FOG(1, 0.1F), FACE(1, 0.6F), INVERT(1, 0.25F);

        final int weight;
        final float minDread;

        Minor(int weight, float minDread) {
            this.weight = weight;
            this.minDread = minDread;
        }
    }

    private static void minor(ServerPlayer player, ServerLevel level, Haunt haunt, float dread) {
        RandomSource random = player.getRandom();
        int total = 0;
        for (Minor event : Minor.values()) {
            if (dread >= event.minDread) {
                total += event.weight;
            }
        }
        int roll = random.nextInt(total);
        Minor chosen = Minor.SOUND_BEHIND;
        for (Minor event : Minor.values()) {
            if (dread >= event.minDread) {
                roll -= event.weight;
                if (roll < 0) {
                    chosen = event;
                    break;
                }
            }
        }
        if (!runMinor(player, level, haunt, chosen)) {
            soundBehind(player);
        }
    }

    private static boolean runMinor(ServerPlayer player, ServerLevel level, Haunt haunt, Minor event) {
        RandomSource random = player.getRandom();
        return switch (event) {
            case SOUND_BEHIND -> soundBehind(player);
            case FOOTSTEPS -> footsteps(player, haunt);
            case CAVE -> cave(player);
            case WHISPER -> whisper(player);
            case ACTIONBAR -> actionbar(player);
            case GLITCH -> glitch(player);
            case FAKE_LEAVE -> fakeLeave(player);
            case TORCHES -> torches(player, level);
            case DOOR -> door(player, level);
            case FAKE_BLOCKS -> fakeBlocks(player, level, haunt);
            case NAME_SIGN -> nameSign(player, level);
            case RUBBER_BAND -> rubberBand(player, haunt);
            case DARKNESS -> darkness(player, haunt);
            case JUKEBOX -> jukebox(player, level, haunt);
            case FOG -> fog(player, haunt);
            case FACE -> {
                ScreenFxPayload.send(player, Fx.FACE, 2, 1.0F);
                yield true;
            }
            case INVERT -> {
                ScreenFxPayload.send(player, Fx.INVERT, 5 + random.nextInt(6), 1.0F);
                ScreenFxPayload.send(player, Fx.GLITCH, 8, 0.6F);
                yield true;
            }
        };
    }

    private static final SoundEvent[] BEHIND = {SoundEvents.WOODEN_DOOR_OPEN, SoundEvents.WOODEN_DOOR_CLOSE, SoundEvents.CHEST_OPEN,
            SoundEvents.CHEST_CLOSE, SoundEvents.STONE_BREAK, SoundEvents.WOOD_BREAK, SoundEvents.GRAVEL_STEP, SoundEvents.GENERIC_EAT,
            SoundEvents.PLAYER_BREATH, SoundEvents.FLINTANDSTEEL_USE, SoundEvents.ITEM_PICKUP, SoundEvents.BELL_BLOCK};
    /** Animals that do not exist here, played too low. */
    private static final SoundEvent[] ANIMALS = {SoundEvents.COW_AMBIENT, SoundEvents.PIG_AMBIENT, SoundEvents.SHEEP_AMBIENT,
            SoundEvents.VILLAGER_AMBIENT, SoundEvents.ZOMBIE_AMBIENT, SoundEvents.ENDERMAN_AMBIENT};

    private static boolean soundBehind(ServerPlayer player) {
        RandomSource random = player.getRandom();
        Vec3 at = SoundFx.behind(player, 3.0 + random.nextDouble() * 6.0);
        if (random.nextInt(4) == 0) {
            SoundFx.at(player, ANIMALS[random.nextInt(ANIMALS.length)], SoundSource.AMBIENT, at, 0.8F, 0.4F + random.nextFloat() * 0.15F);
        } else {
            SoundFx.at(player, BEHIND[random.nextInt(BEHIND.length)], SoundSource.BLOCKS, at, 0.7F, 0.8F + random.nextFloat() * 0.2F);
        }
        return true;
    }

    /** Steps coming closer from behind. Then nothing. */
    private static boolean footsteps(ServerPlayer player, Haunt haunt) {
        BlockState ground = player.level().getBlockState(player.blockPosition().below());
        SoundEvent step = ground.isAir() ? SoundEvents.GRASS_STEP : ground.getSoundType().getStepSound();
        for (int i = 0; i < 6; i++) {
            double distance = 9.0 - i * 1.5;
            haunt.schedule(i * 9, p -> SoundFx.at(p, step, SoundSource.HOSTILE, SoundFx.behind(p, distance).subtract(0, 1.4, 0), 0.6F, 0.95F));
        }
        return true;
    }

    private static boolean cave(ServerPlayer player) {
        RandomSource random = player.getRandom();
        Vec3 at = player.position().add((random.nextDouble() - 0.5) * 24, random.nextDouble() * 4 - 2, (random.nextDouble() - 0.5) * 24);
        SoundFx.at(player, SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, at, 1.0F, 0.6F + random.nextFloat() * 0.4F);
        return true;
    }

    private static final int WHISPERS = 10;

    private static Component whisperText(ServerPlayer player) {
        int n = 1 + player.getRandom().nextInt(WHISPERS);
        return Component.translatable("whisper.belowbedrock." + n, player.getName());
    }

    /** "Lui ti sussurra: girati" - the vanilla whisper format, from someone who is not on the server. */
    private static boolean whisper(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("commands.message.display.incoming",
                Component.translatable("entity.belowbedrock.inhabitant"), whisperText(player)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return true;
    }

    private static boolean actionbar(ServerPlayer player) {
        player.displayClientMessage(whisperText(player).copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
        return true;
    }

    private static boolean glitch(ServerPlayer player) {
        RandomSource random = player.getRandom();
        ScreenFxPayload.send(player, Fx.GLITCH, 6 + random.nextInt(14), 0.4F + random.nextFloat() * 0.6F);
        SoundFx.onPlayer(player, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.MASTER, 0.25F, 1.8F);
        return true;
    }

    /** "<you> left the game", or "Him joined the game". */
    private static boolean fakeLeave(ServerPlayer player) {
        Component message = player.getRandom().nextBoolean()
                ? Component.translatable("multiplayer.player.left", player.getDisplayName())
                : Component.translatable("multiplayer.player.joined", Component.translatable("entity.belowbedrock.inhabitant"));
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.YELLOW));
        return true;
    }

    /** Your torches go out while you are not looking. */
    private static boolean torches(ServerPlayer player, ServerLevel level) {
        BlockPos center = player.blockPosition();
        Vec3 look = player.getViewVector(1.0F);
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-12, -6, -12), center.offset(12, 6, 12))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)
                    || state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)) {
                Vec3 to = Vec3.atCenterOf(pos).subtract(player.getEyePosition()).normalize();
                if (to.dot(look) < 0.2) {
                    found.add(pos.immutable());
                }
            }
        }
        if (found.isEmpty()) {
            return false;
        }
        RandomSource random = player.getRandom();
        for (int i = 0; i < 2 && !found.isEmpty(); i++) {
            BlockPos pos = found.remove(random.nextInt(found.size()));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        return true;
    }

    private static boolean door(ServerPlayer player, ServerLevel level) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-10, -4, -10), center.offset(10, 4, 10))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.WOODEN_DOORS) && state.getBlock() instanceof DoorBlock door) {
                door.setOpen(null, level, state, pos.immutable(), !state.getValue(DoorBlock.OPEN));
                return true;
            }
        }
        return false;
    }

    /** For a moment, the ground in front of you is wrong. Only you see it. */
    private static boolean fakeBlocks(ServerPlayer player, ServerLevel level, Haunt haunt) {
        RandomSource random = player.getRandom();
        BlockState[] wrong = {Blocks.BEDROCK.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState()};
        BlockState fake = wrong[random.nextInt(wrong.length)];
        Vec3 look = player.getViewVector(1.0F);
        List<BlockPos> changed = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            double distance = 3.0 + random.nextDouble() * 10.0;
            double spread = (random.nextDouble() - 0.5) * 8.0;
            int x = Mth.floor(player.getX() + look.x * distance - look.z * spread);
            int z = Mth.floor(player.getZ() + look.z * distance + look.x * spread);
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            if (Math.abs(pos.getY() - player.getBlockY()) < 6 && !level.getBlockState(pos).isAir()) {
                player.connection.send(new ClientboundBlockUpdatePacket(pos, fake));
                changed.add(pos);
            }
        }
        if (changed.isEmpty()) {
            return false;
        }
        ScreenFxPayload.send(player, Fx.GLITCH, 5, 0.35F);
        haunt.schedule(12 + random.nextInt(20), p -> {
            for (BlockPos pos : changed) {
                p.connection.send(new ClientboundBlockUpdatePacket(p.level(), pos));
            }
        });
        return true;
    }

    /** A sign with your name on it, right behind you. */
    private static boolean nameSign(ServerPlayer player, ServerLevel level) {
        Vec3 spot = spotBehind(level, player, 3.0, 6.0);
        if (spot == null) {
            return false;
        }
        BlockPos at = BlockPos.containing(spot);
        if (!level.getBlockState(at).canBeReplaced() || !level.getFluidState(at).isEmpty()) {
            return false;
        }
        // It faces you, so you can read it as soon as you turn around (yaw: from the sign towards you).
        float yaw = (float) (Mth.atan2(player.getZ() - spot.z, player.getX() - spot.x) * Mth.RAD_TO_DEG) - 90.0F;
        level.setBlock(at, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION,
                RotationSegment.convertToSegment(yaw)), Block.UPDATE_ALL);
        if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
            SignText text = new SignText()
                    .setMessage(1, player.getName())
                    .setMessage(2, Component.translatable("sign.belowbedrock.name"));
            sign.setText(text, true);
            sign.setWaxed(true);
        }
        return true;
    }

    /** As if the server had lagged: you are back where you were a few seconds ago. */
    private static boolean rubberBand(ServerPlayer player, Haunt haunt) {
        Vec3 old = haunt.trail.peekFirst();
        if (old == null || old.distanceToSqr(player.position()) < 9.0 || player.isPassenger()) {
            return false;
        }
        ScreenFxPayload.send(player, Fx.GLITCH, 6, 0.7F);
        player.connection.teleport(old.x, old.y, old.z, player.getYRot(), player.getXRot());
        haunt.trail.clear();
        return true;
    }

    private static boolean darkness(ServerPlayer player, Haunt haunt) {
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 180, 0, false, false, false));
        for (int i = 0; i < 8; i++) {
            haunt.schedule(i * 20, p -> SoundFx.onPlayer(p, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 1.0F));
        }
        return true;
    }

    /** Somewhere in the fog a jukebox starts playing 11. Follow it, if you want. */
    private static boolean jukebox(ServerPlayer player, ServerLevel level, Haunt haunt) {
        if (haunt.jukeboxDone) {
            return false;
        }
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < 8; attempt++) {
            Vec3 spot = spotAround(level, player, 100 + random.nextInt(160), 30 + random.nextInt(10));
            if (spot == null) {
                continue;
            }
            BlockPos pos = BlockPos.containing(spot);
            level.setBlock(pos, Blocks.JUKEBOX.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) {
                jukebox.setTheItem(new ItemStack(random.nextBoolean() ? Items.MUSIC_DISC_11 : Items.MUSIC_DISC_13));
                haunt.jukeboxDone = true;
                return true;
            }
        }
        return false;
    }

    /** The fog closes in... and at its edge, someone is standing. */
    private static boolean fog(ServerPlayer player, Haunt haunt) {
        ScreenFxPayload.send(player, Fx.FOG, 700, 16.0F);
        haunt.schedule(220, p -> {
            if (p.level() instanceof ServerLevel level && Faded.is(level) && current(level, haunt) == null) {
                Vec3 spot = spotAround(level, p, p.getRandom().nextInt(60) - 30, 15 + p.getRandom().nextInt(4));
                if (spot != null) {
                    Inhabitant him = Inhabitant.spawn(level, p, Mode.WATCH, spot, 600);
                    if (him != null) {
                        haunt.him = him.getUUID();
                    }
                }
            }
        });
        return true;
    }

    // ------------------------------------------------------------------ Him

    private enum Major {
        WATCH(5, 0.0F), PEEK(4, 0.0F), STALK(3, 0.25F), MIMIC(2, 0.15F), GIFT(2, 0.0F), CHASE(1, 0.6F);

        final int weight;
        final float minDread;

        Major(int weight, float minDread) {
            this.weight = weight;
            this.minDread = minDread;
        }
    }

    private static void major(ServerPlayer player, ServerLevel level, Haunt haunt, float dread) {
        RandomSource random = player.getRandom();
        Major chosen = Major.WATCH;
        if (!haunt.firstSighting) {
            int total = 0;
            for (Major event : Major.values()) {
                if (dread >= event.minDread) {
                    total += event.weight;
                }
            }
            int roll = random.nextInt(total);
            for (Major event : Major.values()) {
                if (dread >= event.minDread) {
                    roll -= event.weight;
                    if (roll < 0) {
                        chosen = event;
                        break;
                    }
                }
            }
        }
        Inhabitant him = summon(player, level, haunt, chosen);
        if (him != null) {
            haunt.firstSighting = false;
        }
    }

    @Nullable
    private static Inhabitant summon(ServerPlayer player, ServerLevel level, Haunt haunt, Major chosen) {
        RandomSource random = player.getRandom();
        Inhabitant him = switch (chosen) {
            case WATCH -> appear(level, player, Mode.WATCH, random.nextInt(100) - 50, 30 + random.nextInt(16), 1200);
            case PEEK -> appear(level, player, Mode.PEEK, (random.nextBoolean() ? 1 : -1) * (38 + random.nextInt(10)), 11 + random.nextInt(6), 100);
            case STALK -> appear(level, player, Mode.STALK, 150 + random.nextInt(60), 22 + random.nextInt(8), 1800);
            case MIMIC -> appear(level, player, Mode.MIMIC, random.nextInt(20) - 10, 22 + random.nextInt(6), 900);
            case GIFT -> {
                Inhabitant gifter = appear(level, player, Mode.GIFT, random.nextInt(80) - 40, 18 + random.nextInt(6), 1200);
                if (gifter != null) {
                    gifter.setGift(gift(player, level));
                }
                yield gifter;
            }
            case CHASE -> {
                Inhabitant chaser = appear(level, player, Mode.CHASE, 150 + random.nextInt(60), 26 + random.nextInt(6), 400);
                if (chaser != null) {
                    for (int i = 0; i < 20; i++) {
                        haunt.schedule(i * 20, p -> SoundFx.onPlayer(p, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 1.2F));
                    }
                }
                yield chaser;
            }
        };
        if (him != null) {
            haunt.him = him.getUUID();
        }
        return him;
    }

    // ------------------------------------------------------------------ commands (for videos and tests)

    /** Names accepted by {@code /faded event}. */
    public static List<String> eventNames() {
        List<String> names = new ArrayList<>();
        for (Minor event : Minor.values()) {
            names.add(event.name().toLowerCase(java.util.Locale.ROOT));
        }
        return names;
    }

    /** Names accepted by {@code /faded him}. */
    public static List<String> himNames() {
        List<String> names = new ArrayList<>();
        for (Major event : Major.values()) {
            names.add(event.name().toLowerCase(java.util.Locale.ROOT));
        }
        return names;
    }

    /** Runs one strange event for this player now. False if it could not happen here (no torches, no doors...). */
    public static boolean triggerEvent(ServerPlayer player, String name) {
        Minor event = Minor.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        if (event == Minor.JUKEBOX) {
            haunt(player).jukeboxDone = false;
        }
        return runMinor(player, player.serverLevel(), haunt(player), event);
    }

    /** Makes Him appear for this player now, in the given mode (he replaces the current one). */
    public static boolean summonHim(ServerPlayer player, String name) {
        Major event = Major.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        Haunt haunt = haunt(player);
        Inhabitant current = current(player.serverLevel(), haunt);
        if (current != null) {
            current.vanish();
        }
        return summon(player, player.serverLevel(), haunt, event) != null;
    }

    private static ItemStack gift(ServerPlayer player, ServerLevel level) {
        return switch (player.getRandom().nextInt(5)) {
            case 0, 1 -> FadedMaps.create(level, FadedMaps.random(player.getRandom()));
            case 2 -> Diaries.nameBook(player);
            case 3 -> Diaries.diary(Diaries.randomVolume(player.getRandom()));
            default -> new ItemStack(Items.POPPY);
        };
    }

    /** Spawns Him {@code distance} blocks from the player, {@code angle} degrees from where they look. */
    @Nullable
    private static Inhabitant appear(ServerLevel level, ServerPlayer player, Mode mode, int angle, int distance, int life) {
        for (int attempt = 0; attempt < 6; attempt++) {
            Vec3 spot = spotAround(level, player, angle + attempt * 7 * (attempt % 2 == 0 ? 1 : -1), distance - attempt);
            if (spot != null) {
                return Inhabitant.spawn(level, player, mode, spot, life);
            }
        }
        return null;
    }

    /** A place to stand, {@code distance} blocks away at {@code angle} degrees from the player's view. */
    @Nullable
    public static Vec3 spotAround(ServerLevel level, ServerPlayer player, float angle, double distance) {
        float yaw = (player.getYRot() + angle) * Mth.DEG_TO_RAD;
        Vec3 target = player.position().add(-Mth.sin(yaw) * distance, 0.0, Mth.cos(yaw) * distance);
        return standableNear(level, target, 8);
    }

    /**
     * A free spot {@code min}..{@code max} blocks from the player, at about their height and out of their sight:
     * right behind them first, then more and more to the sides.
     */
    @Nullable
    public static Vec3 spotBehind(ServerLevel level, ServerPlayer player, double min, double max) {
        int[] angles = {180, 160, 200, 140, 220, 120, 240};
        for (double distance = min; distance <= max; distance += 1.0) {
            for (int angle : angles) {
                Vec3 spot = spotAround(level, player, angle, distance);
                if (spot != null && Math.abs(spot.y - player.getY()) < 2.5) {
                    return spot;
                }
            }
        }
        return null;
    }

    /** The closest spot (in height) where Him fits, around {@code target}. */
    @Nullable
    public static Vec3 standableNear(ServerLevel level, Vec3 target, int range) {
        BlockPos column = BlockPos.containing(target);
        if (!level.isLoaded(column)) {
            return null;
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        if (Math.abs(surface - target.y) <= range + 4 && standable(level, new BlockPos(column.getX(), surface, column.getZ()))) {
            return new Vec3(column.getX() + 0.5, surface, column.getZ() + 0.5);
        }
        for (int dy = 0; dy <= range; dy++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                BlockPos pos = column.atY(Mth.floor(target.y) + dy * sign);
                if (standable(level, pos)) {
                    return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                }
            }
        }
        return null;
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getFluidState(feet).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getBlockState(feet.above(2)).getCollisionShape(level, feet.above(2)).isEmpty();
    }

    // ------------------------------------------------------------------ contact

    /** He was right behind you. */
    public static void jumpscare(ServerPlayer player, Inhabitant him, boolean touched) {
        ScreenFxPayload.send(player, Fx.JUMPSCARE, 18, 1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false, false));
        if (touched) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0, false, false, false));
            player.hurt(player.damageSources().source(TAKEN, him), 4.0F);
        }
    }

    /** He caught you. */
    public static void caught(ServerPlayer player, Inhabitant him) {
        float dread = haunt(player).dread();
        ScreenFxPayload.send(player, Fx.JUMPSCARE, 22, 1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 240, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3, false, false, false));
        player.hurt(player.damageSources().source(TAKEN, him), 6.0F + 8.0F * dread);
    }

    /** You hit Him. Nothing happens to Him. */
    public static void hitHim(ServerPlayer player, Inhabitant him) {
        ScreenFxPayload.send(player, Fx.GLITCH, 14, 0.8F);
        SoundFx.onPlayer(player, SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 1.0F, 0.5F);
        if (player.getRandom().nextInt(3) == 0) {
            haunt(player).schedule(10, p -> ScreenFxPayload.send(p, Fx.FACE, 2, 1.0F));
        }
    }

    // ------------------------------------------------------------------ back home

    /** Every now and then, far away in your own world, someone is watching. */
    private static void tickHome(ServerPlayer player, ServerLevel level, Haunt haunt) {
        if (--haunt.homeTimer > 0) {
            return;
        }
        RandomSource random = player.getRandom();
        haunt.homeTimer = 20 * 60 * (8 + random.nextInt(12));
        if (current(level, haunt) != null || random.nextInt(3) != 0 || !level.canSeeSky(player.blockPosition())) {
            return;
        }
        Inhabitant him = appear(level, player, Mode.WATCH, random.nextInt(90) - 45, 40 + random.nextInt(20), 600);
        if (him != null) {
            haunt.him = him.getUUID();
        }
    }
}
