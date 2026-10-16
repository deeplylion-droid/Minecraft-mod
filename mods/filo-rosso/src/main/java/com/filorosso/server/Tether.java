package com.filorosso.server;

import com.filorosso.data.BondType;
import com.filorosso.data.Couple;
import com.filorosso.data.CoupleBook;
import com.filorosso.data.ModData;
import com.filorosso.sound.ModSounds;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The thread on the server. Each client keeps its own player within reach of the partner (smooth
 * physics); the server only makes sure nobody gets lost (pearls, portals, lag: the one left behind
 * is pulled to the other), cancels fall damage of whoever is hanging from the thread, keeps them from
 * being kicked for "flying", and lets a taut thread hit the mobs it sweeps through.
 */
public final class Tether {
    private Tether() {
    }

    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    /** Game time of the last jump (teleport, pearl, portal): the one who jumped leads. */
    private static final Map<UUID, Long> LAST_JUMP = new HashMap<>();
    /** Players held up by the thread right now (game time). */
    private static final Map<UUID, Long> HELD = new HashMap<>();
    /** Ticks spent hanging from a taut thread, and who was hanging over a real drop. */
    private static final Map<UUID, Integer> HANGING = new HashMap<>();
    private static final java.util.Set<UUID> DROP = new java.util.HashSet<>();
    /** Ticks a pair has been too far apart. */
    private static final Map<String, Integer> APART = new HashMap<>();
    /** Mobs hit by a thread recently (entity id, game time). */
    private static final Map<Integer, Long> HIT = new HashMap<>();
    private static final Map<String, Vec3> LAST_MID = new HashMap<>();

    public static void init() {
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
                LAST_JUMP.put(player.getUUID(), player.level().getServer().overworld().getGameTime()));
    }

    /** Thread length right now. */
    public static double length(MinecraftServer server) {
        return Events.length(server);
    }

    public static boolean held(ServerPlayer player) {
        Long t = HELD.get(player.getUUID());
        return t != null && t >= player.level().getServer().overworld().getGameTime() - 10;
    }

    public static void forget(ServerPlayer player) {
        LAST_POS.remove(player.getUUID());
        HELD.remove(player.getUUID());
        HANGING.remove(player.getUUID());
        DROP.remove(player.getUUID());
    }

    private static String key(Links.Pair p) {
        return p.a().getUUID() + ":" + p.b().getUUID();
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Vec3 last = LAST_POS.put(p.getUUID(), p.position());
            if (last != null && last.distanceToSqr(p.position()) > 8.0 * 8.0) {
                LAST_JUMP.put(p.getUUID(), now);
            }
        }
        double length = length(server);
        for (Links.Pair pair : Links.active(server)) {
            ServerPlayer a = pair.a();
            ServerPlayer b = pair.b();
            String key = key(pair);
            boolean elastic = pair.couple() != null && pair.couple().bondType() == BondType.ELASTIC;
            double max = elastic ? length * 1.5 : length;
            if (a.level() != b.level()) {
                int apart = APART.merge(key, 1, Integer::sum);
                if (apart > 40) {
                    reunite(a, b, now);
                    APART.remove(key);
                }
                continue;
            }
            double d = a.position().distanceTo(b.position());
            if (d > max + 8.0) {
                int apart = APART.merge(key, 1, Integer::sum);
                if (apart > 20) {
                    reunite(a, b, now);
                    APART.remove(key);
                }
            } else {
                APART.remove(key);
            }
            holdUp(a, b, d, max, now);
            holdUp(b, a, d, max, now);
            if (pair.couple() != null) {
                sweep(server, pair, d, max, now);
            }
        }
        if (now % 200 == 0) {
            HIT.values().removeIf(t -> t < now - 40);
            HELD.values().removeIf(t -> t < now - 40);
        }
    }

    /**
     * In the air below a partner who stands firm: no fall damage (the thread holds you), no
     * "flying" kick while hanging, and a reward for a real catch: hanging over a drop for a while,
     * then back on your feet.
     */
    private static void holdUp(ServerPlayer p, ServerPlayer other, double d, double max, long now) {
        boolean below = other.getY() - p.getY() > 0.6;
        boolean steady = other.onGround() || other.isInWater() || other.isPassenger();
        boolean air = !p.onGround() && !p.isInWater() && !p.onClimbable();
        if (air && below && steady) {
            p.resetFallDistance();
            HELD.put(p.getUUID(), now);
            if (d >= max - 0.8) {
                int hang = HANGING.merge(p.getUUID(), 1, Integer::sum);
                if (hang % 5 == 1 && overDrop(p)) {
                    DROP.add(p.getUUID());
                }
            }
        } else if (!air) {
            Integer hang = HANGING.remove(p.getUUID());
            boolean drop = DROP.remove(p.getUUID());
            if (hang != null && hang >= 10 && drop) {
                Couple couple = Bonds.coupleOf(p);
                if (couple != null && couple.has(other.getUUID())) {
                    Affinity.add(p.level().getServer(), couple, 12, true);
                    Challenges.add(p.level().getServer(), couple, Challenges.CATCHES, 1);
                    other.sendSystemMessage(Component.translatable("filorosso.msg.caught", p.getGameProfile().name()).withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
        }
    }

    /** At least four blocks of nothing under the feet. */
    private static boolean overDrop(ServerPlayer p) {
        net.minecraft.core.BlockPos.MutableBlockPos pos = p.blockPosition().mutable();
        for (int i = 0; i < 4; i++) {
            pos.move(net.minecraft.core.Direction.DOWN);
            if (!p.level().getBlockState(pos).getCollisionShape(p.level(), pos).isEmpty() || !p.level().getFluidState(pos).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** The one left behind is pulled to the one who jumped (pearl, portal, teleport...). */
    public static void reunite(ServerPlayer a, ServerPlayer b, long now) {
        long ja = LAST_JUMP.getOrDefault(a.getUUID(), 0L);
        long jb = LAST_JUMP.getOrDefault(b.getUUID(), 0L);
        ServerPlayer leader = ja >= jb ? a : b;
        ServerPlayer follower = leader == a ? b : a;
        if (ModData.downed(follower) && !ModData.downed(leader)) {
            ServerPlayer t = leader;
            leader = follower;
            follower = t;
        }
        Vec3 to = leader.position();
        follower.teleportTo((ServerLevel) leader.level(), to.x, to.y, to.z, Set.of(), follower.getYRot(), follower.getXRot(), true);
        follower.resetFallDistance();
        LAST_POS.put(follower.getUUID(), follower.position());
        follower.sendSystemMessage(Component.translatable("filorosso.msg.pulled_along", leader.getGameProfile().name()).withStyle(ChatFormatting.LIGHT_PURPLE),
                true);
        Bonds.sound(follower, ModSounds.TELEPORT, 1.2F);
        ((ServerLevel) follower.level()).sendParticles(ParticleTypes.HEART, to.x, to.y + 1.0, to.z, 10, 0.5, 0.5, 0.5, 0.1);
    }

    /** A thread pulled tight through a mob hurts it (and burns with the fire bond). */
    private static void sweep(MinecraftServer server, Links.Pair pair, double d, double max, long now) {
        if (d < 1.2 || !server.overworld().getGameRules().get(ModRules.THREAD_HITS)) {
            return;
        }
        ServerPlayer a = pair.a();
        ServerPlayer b = pair.b();
        Couple couple = pair.couple();
        ServerLevel level = (ServerLevel) a.level();
        Vec3 from = a.position().add(0.0, 1.0, 0.0);
        Vec3 to = b.position().add(0.0, 1.0, 0.0);
        Vec3 mid = from.add(to).scale(0.5);
        Vec3 lastMid = LAST_MID.put(key(pair), mid);
        double speed = lastMid == null ? 0.0 : lastMid.distanceTo(mid);
        boolean taut = d > max - 0.8;
        if (!taut && speed < 0.15) {
            return;
        }
        AABB box = new AABB(from, to).inflate(0.5);
        boolean fire = couple.bondType() == BondType.FIRE;
        float damage = 1.5F + 0.75F * couple.level().ordinal() + (fire ? 1.0F : 0.0F);
        Vec3 push = lastMid == null ? Vec3.ZERO : mid.subtract(lastMid);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !(e instanceof Player) && !(e instanceof ArmorStand))) {
            if (e.getBoundingBox().inflate(0.2).clip(from, to).isEmpty() && !e.getBoundingBox().inflate(0.2).contains(mid)) {
                continue;
            }
            Long last = HIT.get(e.getId());
            if (last != null && last > now - 20) {
                continue;
            }
            HIT.put(e.getId(), now);
            ServerPlayer owner = a.distanceToSqr(e) < b.distanceToSqr(e) ? a : b;
            net.minecraft.world.damagesource.DamageSource source = level.damageSources().source(ModDamage.THREAD, owner, owner);
            if (e.hurtServer(level, source, damage)) {
                Vec3 dir = push.lengthSqr() > 1.0E-4 ? push.normalize() : e.position().subtract(owner.position()).normalize();
                e.knockback(0.6, -dir.x, -dir.z, source, damage);
                if (fire) {
                    e.igniteForSeconds(5.0F);
                }
                level.sendParticles(fire ? ParticleTypes.FLAME : ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
                level.playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.TAUT, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.2F);
                Challenges.add(server, couple, Challenges.THREAD_HITS, 1);
            }
        }
    }

    /** Is this point near the thread between these two? (scissors, lightning) */
    public static double distanceToThread(Entity a, Entity b, Vec3 point) {
        Vec3 from = a.position().add(0.0, 1.0, 0.0);
        Vec3 to = b.position().add(0.0, 1.0, 0.0);
        Vec3 ab = to.subtract(from);
        double len2 = ab.lengthSqr();
        double t = len2 < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, point.subtract(from).dot(ab) / len2));
        return from.add(ab.scale(t)).distanceTo(point);
    }

    /** Cuts a couple's thread for a while (Spezzacuori's servants and scissors). */
    public static void cut(MinecraftServer server, Couple couple, int ticks) {
        long now = server.overworld().getGameTime();
        if (couple.cutUntil > now) {
            couple.cutUntil = Math.max(couple.cutUntil, now + ticks);
            Bonds.sync(server, couple);
            return;
        }
        couple.cutUntil = now + ticks;
        Bonds.sync(server, couple);
        ServerPlayer a = Bonds.player(server, couple.a);
        ServerPlayer b = Bonds.player(server, couple.b);
        if (a != null && b != null) {
            com.filorosso.net.Net.ThreadFx fx = new com.filorosso.net.Net.ThreadFx(com.filorosso.net.Net.ThreadFx.CUT, a.getId(), b.getId());
            for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                if (viewer.level() == a.level() && viewer.distanceToSqr(a) < 128 * 128) {
                    Bonds.send(viewer, fx);
                }
            }
        }
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            if (p != null) {
                Bonds.sound(p, ModSounds.CUT, 1.0F);
                p.sendSystemMessage(Component.translatable("filorosso.msg.cut", ticks / 20).withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /** Threads cut a while ago tie up again: partners far apart are brought back together. */
    public static void tickCuts(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Couple c : CoupleBook.get(server).couples) {
            if (c.cutUntil != 0 && c.cutUntil <= now) {
                c.cutUntil = 0;
                Bonds.sync(server, c);
                ServerPlayer a = Bonds.player(server, c.a);
                ServerPlayer b = Bonds.player(server, c.b);
                if (Links.usable(a) && Links.usable(b)) {
                    if (a.level() != b.level() || a.distanceTo(b) > length(server) + 3.0) {
                        reunite(a, b, now);
                    }
                    for (ServerPlayer p : new ServerPlayer[]{a, b}) {
                        p.sendSystemMessage(Component.translatable("filorosso.msg.retied").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                        Bonds.sound(p, ModSounds.BOND, 1.4F);
                    }
                }
            }
        }
    }
}
