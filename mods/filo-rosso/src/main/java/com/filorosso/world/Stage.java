package com.filorosso.world;

import com.filorosso.FiloRosso;
import com.filorosso.data.Couple;
import com.filorosso.data.CoupleBook;
import com.filorosso.entity.Burattinaio;
import com.filorosso.entity.ModEntities;
import com.filorosso.entity.Spectator;
import com.filorosso.item.ModItems;
import com.filorosso.server.Affinity;
import com.filorosso.server.Bonds;
import com.filorosso.server.Challenges;
import com.filorosso.server.Scores;
import com.filorosso.sound.ModSounds;
import com.filorosso.util.Text;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The show in the Theatre: turn off the ghost light and the curtain opens, the Burattinaio comes
 * down from the dark; three acts; when it falls the stalls applaud, the curtain closes, "FINE", and
 * everyone is sent home. Nobody can break or build anything up there.
 */
public final class Stage {
    private Stage() {
    }

    public enum State {
        IDLE, OPENING, FIGHT, ENDING
    }

    /** Health per couple in the Theatre (at least two couples' worth). */
    public static final float HEALTH_PER_COUPLE = 450.0F;
    public static final int SPECTATORS = 44;
    private static final int MUSIC_EVERY = 20 * 32;

    private static State state = State.IDLE;
    /** Columns of the curtain open on each side of the middle (-1 = closed, 17 = all open). */
    private static int curtainColumns = -1;
    private static long since;
    private static UUID boss;
    private static long lastMusic;
    private static int emptyTicks;

    public static State state() {
        return state;
    }

    public static void init() {
        // on both sides: the client must not fall back to using the item in hand, nor show blocks
        // being placed or broken that the server would put back
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.dimension() != Theatre.LEVEL) {
                return InteractionResult.PASS;
            }
            if (Theatre.isGhostLight(hit.getBlockPos())) {
                if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
                    if (server.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
                        // a Monster would vanish at once: no show on Peaceful
                        sp.sendSystemMessage(Component.translatable("filorosso.msg.boss_peaceful").withStyle(ChatFormatting.GRAY), true);
                    } else if (state == State.IDLE) {
                        startShow(server, sp);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            ItemStack held = player.getItemInHand(hand);
            if (!player.isCreative() && (held.getItem() instanceof BlockItem || held.getItem() instanceof BucketItem)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        // the Burattinaio's mask: crouch and hit a friend
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer wearer) || !(entity instanceof ServerPlayer victim) || !player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            ItemStack head = wearer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
            if (!head.is(ModItems.PUPPETEER_MASK)) {
                return InteractionResult.PASS;
            }
            if (wearer.getCooldowns().isOnCooldown(head)) {
                return InteractionResult.PASS;
            }
            if (Puppets.prank((ServerLevel) level, wearer, victim)) {
                wearer.getCooldowns().addCooldown(head, Puppets.PRANK_COOLDOWN);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                level.dimension() == Theatre.LEVEL && !player.isCreative() ? InteractionResult.FAIL : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, blockState, blockEntity) ->
                level.dimension() != Theatre.LEVEL || player.isCreative());
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    private static List<ServerPlayer> audience(ServerLevel level) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator()) {
                list.add(p);
            }
        }
        return list;
    }

    /** Couples with at least one partner in the Theatre (at least two, for the boss's health). */
    public static int couplesHere(ServerLevel level) {
        Set<Integer> ids = new HashSet<>();
        for (ServerPlayer p : audience(level)) {
            Couple c = Bonds.coupleOf(p);
            if (c != null) {
                ids.add(c.id);
            }
        }
        return ids.size();
    }

    public static void onEnter(ServerLevel level, ServerPlayer p) {
        fillStalls(level);
        if (state == State.IDLE) {
            Bonds.title(p, Component.translatable("filorosso.theatre.title").withStyle(ChatFormatting.DARK_RED),
                    Component.translatable("filorosso.theatre.hint").withStyle(ChatFormatting.GRAY), 20, 100, 30);
        }
        Bonds.hear(p, ModSounds.THEATRE_CALL, 0.7F, 0.6F);
    }

    /** Spectators in every seat that has none (they are not saved: the stalls fill up again). */
    private static void fillStalls(ServerLevel level) {
        AABB box = new AABB(-26, Theatre.FLOOR - 2, -52, 26, Theatre.FLOOR + 12, -13);
        List<Spectator> sitting = level.getEntitiesOfClass(Spectator.class, box, s -> !s.awake());
        if (sitting.size() >= SPECTATORS) {
            return;
        }
        Set<BlockPos> taken = new HashSet<>();
        for (Spectator s : sitting) {
            taken.add(BlockPos.containing(s.getX(), s.getY() + 0.7, s.getZ()));
        }
        List<BlockPos> seats = new ArrayList<>(Theatre.seats());
        Collections.shuffle(seats, new Random(1234));
        int need = SPECTATORS - sitting.size();
        for (BlockPos seat : seats) {
            if (need <= 0) {
                break;
            }
            if (taken.contains(seat)) {
                continue;
            }
            Spectator s = ModEntities.SPECTATOR.create(level, EntitySpawnReason.EVENT);
            if (s == null) {
                return;
            }
            // sitting on the seat, facing the stage (+z)
            s.snapTo(seat.getX() + 0.5, seat.getY() - 0.12, seat.getZ() + 0.62, 0.0F, 0.0F);
            s.setYHeadRot(0.0F);
            s.setYBodyRot(0.0F);
            level.addFreshEntity(s);
            need--;
        }
    }

    // ------------------------------------------------------------------ the show

    /** The ghost light goes out: the show begins. */
    public static void startShow(ServerLevel level, ServerPlayer by) {
        if (state != State.IDLE) {
            return;
        }
        state = State.OPENING;
        since = now(level.getServer());
        curtainColumns = 0;
        Theatre.curtainColumn(level, 0, false);
        Theatre.ghostLight(level, false);
        Theatre.lights(level, false);
        level.playSound(null, Theatre.GHOST_LIGHT, ModSounds.GHOST_OFF, SoundSource.BLOCKS, 3.0F, 1.0F);
        for (ServerPlayer p : audience(level)) {
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 60, 0, false, false));
            Bonds.title(p, Component.translatable("filorosso.theatre.begin").withStyle(ChatFormatting.DARK_RED),
                    Component.translatable("filorosso.theatre.begin.sub", by.getGameProfile().name()).withStyle(ChatFormatting.GRAY), 10, 60, 20);
        }
    }

    public static Burattinaio summon(ServerLevel level) {
        Burattinaio b = ModEntities.BURATTINAIO.create(level, EntitySpawnReason.TRIGGERED);
        if (b == null) {
            return null;
        }
        b.snapTo(Theatre.BOSS_HOME.x, Theatre.STAGE + 28.0, Theatre.BOSS_HOME.z, 180.0F, 0.0F);
        b.setYHeadRot(180.0F);
        b.setYBodyRot(180.0F);
        float health = HEALTH_PER_COUPLE * Math.max(2, couplesHere(level));
        var max = b.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(health);
        }
        b.setHealth(health);
        b.arrive();
        level.addFreshEntity(b);
        boss = b.getUUID();
        return b;
    }

    private static Burattinaio boss(ServerLevel level) {
        if (boss == null) {
            return null;
        }
        Entity e = level.getEntity(boss);
        return e instanceof Burattinaio b ? b : null;
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = Theatre.level(server);
        if (level == null) {
            return;
        }
        long now = now(server);
        List<ServerPlayer> players = audience(level);
        for (ServerPlayer p : players) {
            // the Theatre hangs in nothing: whoever falls off it (flying over the walls, a pearl, a
            // fling) is caught by the threads and put back at the back of the stalls
            if (p.getY() < Theatre.FLOOR - 12) {
                p.teleportTo(level, Theatre.ENTRY.x, Theatre.ENTRY.y, Theatre.ENTRY.z, java.util.Set.of(), p.getYRot(), 0.0F, true);
                p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                p.resetFallDistance();
                p.sendSystemMessage(Component.translatable("filorosso.theatre.caught").withStyle(ChatFormatting.DARK_RED), true);
                Bonds.hear(p, ModSounds.SNAP, 0.8F, 0.7F);
            }
        }
        if (players.isEmpty()) {
            // nobody left watching: the show stops and the place gets ready again
            if (state != State.IDLE && ++emptyTicks > 600) {
                reset(level);
            }
            return;
        }
        emptyTicks = 0;
        if (now % 200 == 0 && state == State.IDLE) {
            fillStalls(level);
        }
        long t = now - since;
        switch (state) {
            case OPENING -> {
                // the curtain opens from the middle outwards, a column every 3 ticks (counted, not
                // matched on the tick: the first tick of the show is not always t = 0)
                int k = (int) Math.min(17, t / 3);
                while (curtainColumns < k) {
                    curtainColumns++;
                    Theatre.curtainColumn(level, curtainColumns, false);
                    Theatre.curtainColumn(level, -curtainColumns, false);
                    if (curtainColumns % 3 == 0) {
                        level.playSound(null, new BlockPos(0, Theatre.STAGE + 8, Theatre.CURTAIN_Z), ModSounds.CURTAIN, SoundSource.BLOCKS, 2.5F,
                                0.8F + curtainColumns * 0.02F);
                    }
                }
                if (t == 30) {
                    if (boss(level) == null) {
                        summon(level);
                    }
                    music(level, players);
                }
                if (t >= 70) {
                    state = State.FIGHT;
                    since = now;
                }
            }
            case FIGHT -> {
                Burattinaio b = boss(level);
                if (b == null || b.isRemoved()) {
                    // gone without dying (a command, a crash): back to the start
                    if (t > 40) {
                        reset(level);
                    }
                    return;
                }
                if (now - lastMusic >= MUSIC_EVERY) {
                    music(level, players);
                }
            }
            case ENDING -> ending(level, players, t);
            default -> {
            }
        }
    }

    private static void music(ServerLevel level, List<ServerPlayer> players) {
        lastMusic = now(level.getServer());
        for (ServerPlayer p : players) {
            Bonds.hear(p, ModSounds.THEATRE_MUSIC, 0.55F, 1.0F);
        }
    }

    public static void onPhase(ServerLevel level, Burattinaio b, int phase) {
        for (ServerPlayer p : audience(level)) {
            Bonds.title(p, Component.translatable("filorosso.theatre.act" + phase).withStyle(ChatFormatting.DARK_RED),
                    Component.translatable("filorosso.theatre.act" + phase + ".sub").withStyle(ChatFormatting.GRAY), 10, 60, 20);
            Bonds.hear(p, ModSounds.THEATRE_CALL, 1.0F, 0.5F);
        }
    }

    /** The lights of the stage go out (DARK) and come back. */
    public static void darkness(ServerLevel level, boolean dark) {
        Theatre.lightLevels(level, dark ? 0 : 10, dark ? 0 : 8);
    }

    /** Some spectators stand up and come down to the stage. */
    public static void wakeSpectators(ServerLevel level, int perCouple) {
        int want = Math.min(12, perCouple * Math.max(1, couplesHere(level)));
        AABB box = new AABB(-26, Theatre.FLOOR - 2, -52, 26, Theatre.FLOOR + 12, -13);
        List<Spectator> sitting = level.getEntitiesOfClass(Spectator.class, box, s -> !s.awake());
        // the ones nearest the stage first
        sitting.sort((a, b) -> Double.compare(-a.getZ(), -b.getZ()));
        for (int i = 0; i < want && i < sitting.size(); i++) {
            Spectator s = sitting.get(i);
            s.wake();
            ServerPlayer target = level.getNearestPlayer(s, 64.0) instanceof ServerPlayer p ? p : null;
            if (target != null && !target.isCreative()) {
                s.setTarget(target);
            }
        }
    }

    // ------------------------------------------------------------------ the end

    public static void onDeath(ServerLevel level, Burattinaio b) {
        MinecraftServer server = level.getServer();
        state = State.ENDING;
        since = now(server);
        boss = null;
        // the house lights come up: the stalls can be seen applauding
        Theatre.lights(level, true);
        CoupleBook book = CoupleBook.get(server);
        book.puppeteerBeaten = true;
        for (Couple c : book.couples) {
            if (c.bossHits > 0) {
                c.points += c.bossHits >= 5 ? 10 : 4;
                c.bossHits = 0;
                Affinity.add(server, c, 400, false);
                Challenges.add(server, c, Challenges.PUPPETEER, 1);
            }
        }
        book.setDirty();
        Scores.refresh(server);
        // the awake spectators sit back down in the dark: they are gone
        for (Spectator s : level.getEntitiesOfClass(Spectator.class, new AABB(-30, 40, -60, 30, 110, 20), Spectator::awake)) {
            s.discard();
        }
        for (ServerPlayer p : audience(level)) {
            ItemStack mask = new ItemStack(ModItems.PUPPETEER_MASK);
            if (!p.getInventory().add(mask)) {
                p.drop(mask, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        server.getPlayerList().broadcastSystemMessage(Component.empty().append(Text.icon(Text.HEART, 0xFF5090)).append(Component.literal(" "))
                .append(Component.translatable("filorosso.msg.puppeteer_beaten").withStyle(ChatFormatting.LIGHT_PURPLE)), false);
        for (Couple c : book.couples) {
            Challenges.sendInfo(server, c);
        }
    }

    /** Applause from the stalls, the curtain closes, FINE, and everyone goes home. */
    private static void ending(ServerLevel level, List<ServerPlayer> players, long t) {
        AABB stalls = new AABB(-26, Theatre.FLOOR - 2, -52, 26, Theatre.FLOOR + 12, -13);
        if (t == 90) {
            for (Spectator s : level.getEntitiesOfClass(Spectator.class, stalls, s -> !s.awake())) {
                s.clap(true);
            }
        }
        if (t >= 90 && t < 260 && t % 2 == 0) {
            // a crowd that claps: a few hands at a time, from all over the stalls
            List<Spectator> all = level.getEntitiesOfClass(Spectator.class, stalls, s -> !s.awake());
            for (int i = 0; i < 3 && !all.isEmpty(); i++) {
                Spectator s = all.get(level.getRandom().nextInt(all.size()));
                level.playSound(null, s.getX(), s.getY() + 1.2, s.getZ(), ModSounds.APPLAUSE, SoundSource.AMBIENT, 1.2F,
                        0.85F + level.getRandom().nextFloat() * 0.3F);
            }
        }
        if (t == 260) {
            for (Spectator s : level.getEntitiesOfClass(Spectator.class, stalls, s -> true)) {
                s.clap(false);
            }
        }
        if (t >= 140) {
            // and closes from the sides inwards
            int k = 17 - (int) Math.min(17, (t - 140) / 3);
            while (curtainColumns > k - 1 && curtainColumns >= 0) {
                Theatre.curtainColumn(level, curtainColumns, true);
                Theatre.curtainColumn(level, -curtainColumns, true);
                if (curtainColumns % 4 == 0) {
                    level.playSound(null, new BlockPos(0, Theatre.STAGE + 8, Theatre.CURTAIN_Z), ModSounds.CURTAIN, SoundSource.BLOCKS, 2.5F, 0.7F);
                }
                curtainColumns--;
            }
        }
        if (t == 200) {
            for (ServerPlayer p : players) {
                Bonds.title(p, Component.translatable("filorosso.theatre.end").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        Component.translatable("filorosso.theatre.end.sub").withStyle(ChatFormatting.GRAY), 20, 120, 40);
                Bonds.sound(p, ModSounds.VICTORY, 1.0F);
            }
        }
        if (t == 520) {
            for (ServerPlayer p : players) {
                Theatre.goHome(p);
            }
            reset(level);
        }
    }

    /** Back to the start: curtain closed, ghost light on, stalls full and quiet. */
    public static void reset(ServerLevel level) {
        Burattinaio b = boss(level);
        if (b != null) {
            b.discard();
        }
        boss = null;
        Puppets.freeAll(level.getServer());
        for (Spectator s : level.getEntitiesOfClass(Spectator.class, new AABB(-30, 40, -60, 30, 110, 20), Spectator::awake)) {
            s.discard();
        }
        Theatre.curtain(level, true);
        curtainColumns = -1;
        Theatre.ghostLight(level, true);
        Theatre.lights(level, true);
        state = State.IDLE;
        since = now(level.getServer());
        FiloRosso.LOGGER.info("Filo Rosso: the Theatre is ready for a new show");
    }

    /** /filo burattinaio: the show starts now, from wherever the command is given. */
    public static boolean forceShow(MinecraftServer server, ServerPlayer by) {
        ServerLevel level = Theatre.level(server);
        if (level == null) {
            return false;
        }
        Theatre.ensureBuilt(level);
        if (state == State.ENDING || state == State.FIGHT && boss(level) != null) {
            return false;
        }
        if (state != State.IDLE) {
            reset(level);
        }
        startShow(level, by);
        return true;
    }

    public static Burattinaio current(MinecraftServer server) {
        ServerLevel level = Theatre.level(server);
        return level == null ? null : boss(level);
    }

    /** A boss that was loaded from the save (the server restarted mid-show) takes the stage again. */
    public static void adopt(Burattinaio b) {
        if (boss == null || !boss.equals(b.getUUID())) {
            boss = b.getUUID();
            state = State.FIGHT;
            since = b.level().getServer() != null ? now(b.level().getServer()) : 0L;
        }
    }

    public static void particles(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 20, 0.5, 0.5, 0.5, 0.05);
    }
}
