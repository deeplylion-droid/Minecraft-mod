package com.filorosso.server;

import com.filorosso.data.BondLevel;
import com.filorosso.data.BondType;
import com.filorosso.data.Couple;
import com.filorosso.data.ModData;
import com.filorosso.data.Palette;
import com.filorosso.data.Powers;
import com.filorosso.entity.BondBoss;
import com.filorosso.net.Net;
import com.filorosso.sound.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** What a stronger bond lets you do: the first three with their own keys, all of them on the wheel (B). */
public final class Abilities {
    private Abilities() {
    }

    public enum Ability {
        /** Yank the partner towards you. */
        PULL(BondLevel.RIBBON, 20 * 15),
        /** Give two of your hearts to the partner. */
        GIFT(BondLevel.BRAID, 20 * 10),
        /** Fly to the partner, wherever they are. */
        REACH(BondLevel.GOLD, 20 * 300),
        /** The thread is a slingshot: the two of you are flung where you look, and crash down on whoever is there. */
        SLING(BondLevel.EMBER, 20 * 20),
        /** The thread flies like a lasso: a mob stays tied up, a player is pulled to you, a boss is slowed down. */
        LASSO(BondLevel.CRYSTAL, 20 * 25),
        /** A dome of woven threads round the two of you: monsters are pushed out, arrows bounce off. */
        DOME(BondLevel.AURORA, 20 * 90),
        /** Both press it: a kiss, and a wave of hearts that hurts every monster around and heals you. */
        KISS(BondLevel.COMET, 20 * 120),
        /** For twenty seconds: you fly, hit hard, cannot fall, and the thread burns whatever it touches. */
        ETERNAL(BondLevel.DESTINY, 20 * 480);

        public static final Ability[] ALL = values();
        public final BondLevel level;
        public final int cooldown;

        Ability(BondLevel level, int cooldown) {
            this.level = level;
            this.cooldown = cooldown;
        }
    }

    public static final double SLING_SPEED = 2.1;
    public static final double SLING_MAX = 2.4;
    public static final double LANDING_RADIUS = 3.5;
    public static final float LANDING_DAMAGE = 6.0F;
    public static final double LASSO_RANGE = 24.0;
    public static final int LASSO_TICKS = 120;
    public static final double DOME_RADIUS = 5.5;
    public static final int DOME_TICKS = 200;
    /** How long a kiss waits for the partner to answer (ticks): time to open the wheel and find it. */
    public static final int KISS_WINDOW = 200;
    public static final double KISS_DISTANCE = 8.0;
    public static final double KISS_RADIUS = 12.0;
    public static final float KISS_DAMAGE = 24.0F;
    public static final int ETERNAL_TICKS = 400;
    /** Kept on a player who flies with "Eterni", so the flight is taken back even after a crash of the server. */
    private static final String ETERNAL_TAG = "filorosso.eternal";
    /** Kept on a mob tied with the lasso (its AI is off while it is tied). */
    private static final String TIED_TAG = "filorosso.tied";

    private static final Map<UUID, long[]> READY = new HashMap<>();

    /** A mob, player or boss caught by a lasso. */
    private record Tie(LivingEntity target, ServerPlayer by, Couple couple, long start, long until, boolean hadAi, Vec3 pullTo) {
    }

    private static final Map<Integer, Tie> TIED = new HashMap<>();

    /** A dome of threads. */
    public record Dome(int id, ServerLevel level, Vec3 center, double radius, long start, long end, Couple couple) {
    }

    private static final List<Dome> DOMES = new ArrayList<>();
    private static final Set<Integer> DEFLECTED = new HashSet<>();
    private static int nextDome;

    /** Kisses waiting for an answer: who asked, when. */
    private static final Map<UUID, Long> KISSES = new HashMap<>();

    /** Kisses about to burst: the couple and the tick. */
    private record Burst(UUID a, UUID b, long at) {
    }

    private static final List<Burst> BURSTS = new ArrayList<>();
    /** Ticks in the air of everyone flung by the slingshot. */
    private static final Map<UUID, Integer> AIRBORNE = new HashMap<>();

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(Net.UseAbility.TYPE, (p, c) -> {
            if (p.ability() >= 0 && p.ability() < Ability.ALL.length) {
                use(c.player(), Ability.ALL[p.ability()]);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // a flight of "Eterni" that the server could not take back (it stopped)
            ServerPlayer p = handler.player;
            if (p.entityTags().contains(ETERNAL_TAG)) {
                p.removeTag(ETERNAL_TAG);
                if (!p.isCreative() && !p.isSpectator()) {
                    p.getAbilities().mayfly = false;
                    p.getAbilities().flying = false;
                    p.onUpdateAbilities();
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> endEternal(handler.player, false));
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            // a mob saved while it was tied gets its AI back
            if (entity instanceof Mob mob && mob.entityTags().contains(TIED_TAG) && !TIED.containsKey(mob.getId())) {
                mob.removeTag(TIED_TAG);
                mob.setNoAi(false);
            }
        });
    }

    private static long[] ready(UUID id) {
        long[] r = READY.get(id);
        if (r == null || r.length != Ability.ALL.length) {
            long[] fresh = new long[Ability.ALL.length];
            if (r != null) {
                System.arraycopy(r, 0, fresh, 0, Math.min(r.length, fresh.length));
            }
            r = fresh;
            READY.put(id, r);
        }
        return r;
    }

    public static void sendCooldowns(ServerPlayer player) {
        long[] r = ready(player.getUUID());
        List<Long> list = new ArrayList<>();
        for (long v : r) {
            list.add(v);
        }
        Bonds.send(player, new Net.Cooldowns(list));
    }

    private static void deny(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static void use(ServerPlayer player, Ability ability) {
        MinecraftServer server = player.level().getServer();
        long now = now(server);
        Couple couple = Bonds.coupleOf(player);
        if (couple == null) {
            deny(player, "filorosso.ability.single");
            return;
        }
        if (!couple.level().atLeast(ability.level)) {
            deny(player, "filorosso.ability.locked", ability.level.number());
            return;
        }
        if (ModData.downed(player) || !player.isAlive() || ModData.powers(player).puppet(now)) {
            return;
        }
        long[] r = ready(player.getUUID());
        if (now < r[ability.ordinal()]) {
            deny(player, "filorosso.ability.cooldown", (r[ability.ordinal()] - now + 19) / 20);
            return;
        }
        ServerPlayer partner = Bonds.player(server, couple.other(player.getUUID()));
        if (partner == null || !partner.isAlive()) {
            deny(player, "filorosso.ability.no_partner");
            return;
        }
        boolean done = switch (ability) {
            case PULL -> pull(player, partner);
            case GIFT -> gift(player, partner);
            case REACH -> reach(player, partner);
            case SLING -> sling(player, partner, couple, now);
            case LASSO -> lasso(player, partner, couple, now);
            case DOME -> dome(player, partner, couple, now);
            case KISS -> kiss(player, partner, couple, now);
            case ETERNAL -> eternal(player, partner, couple, now);
        };
        if (done) {
            int cooldown = ability.cooldown;
            if (ability == Ability.PULL && couple.bondType() == BondType.MAGNET) {
                cooldown /= 2;
            }
            r[ability.ordinal()] = now + cooldown;
            sendCooldowns(player);
            // the two powers you use together are spent for both of you
            if (ability == Ability.KISS || ability == Ability.ETERNAL) {
                ready(partner.getUUID())[ability.ordinal()] = now + cooldown;
                sendCooldowns(partner);
            }
        }
    }

    private static void fx(ServerPlayer near, int kind, int a, int b) {
        Net.ThreadFx fx = new Net.ThreadFx(kind, a, b);
        for (ServerPlayer viewer : near.level().getServer().getPlayerList().getPlayers()) {
            if (viewer.level() == near.level() && viewer.distanceToSqr(near) < 128 * 128) {
                Bonds.send(viewer, fx);
            }
        }
    }

    static void fling(ServerPlayer p, Vec3 v) {
        p.setDeltaMovement(v);
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        p.syncVelocity = true;
    }

    private static boolean pull(ServerPlayer player, ServerPlayer partner) {
        if (partner.level() != player.level() || partner.distanceTo(player) > 40.0) {
            deny(player, "filorosso.ability.too_far");
            return false;
        }
        // pulled away from the Burattinaio's strings
        if (com.filorosso.world.Puppets.isPuppet(partner)) {
            com.filorosso.world.Puppets.free((ServerLevel) partner.level(), partner, player);
        }
        Vec3 dir = player.position().subtract(partner.position());
        double d = dir.length();
        if (d < 1.5) {
            return false;
        }
        Vec3 v = dir.normalize().scale(Math.min(1.2, 0.3 + d * 0.09)).add(0.0, 0.35, 0.0);
        partner.setDeltaMovement(v);
        partner.connection.send(new ClientboundSetEntityMotionPacket(partner));
        partner.resetFallDistance();
        Bonds.sound(player, ModSounds.PULL, 1.0F);
        Bonds.sound(partner, ModSounds.PULL, 1.1F);
        fx(player, Net.ThreadFx.PULL, player.getId(), partner.getId());
        return true;
    }

    private static boolean gift(ServerPlayer player, ServerPlayer partner) {
        if (partner.level() != player.level() || partner.distanceTo(player) > 12.0) {
            deny(player, "filorosso.ability.too_far");
            return false;
        }
        if (player.getHealth() <= 8.0F) {
            deny(player, "filorosso.ability.gift_weak");
            return false;
        }
        if (partner.getHealth() >= partner.getMaxHealth()) {
            deny(player, "filorosso.ability.gift_full", partner.getGameProfile().name());
            return false;
        }
        player.setHealth(player.getHealth() - 4.0F);
        partner.heal(4.0F);
        ServerLevel level = (ServerLevel) player.level();
        Vec3 from = player.position().add(0, 1.2, 0);
        Vec3 to = partner.position().add(0, 1.2, 0);
        for (int i = 0; i <= 8; i++) {
            Vec3 p = from.lerp(to, i / 8.0);
            level.sendParticles(ParticleTypes.HEART, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        Bonds.sound(player, ModSounds.GIFT, 1.0F);
        Bonds.sound(partner, ModSounds.GIFT, 1.2F);
        partner.sendSystemMessage(Component.translatable("filorosso.msg.gift", player.getGameProfile().name()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    private static boolean reach(ServerPlayer player, ServerPlayer partner) {
        Vec3 to = partner.position();
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1, player.getZ(), 16, 0.4, 0.6, 0.4, 0.1);
        player.teleportTo((ServerLevel) partner.level(), to.x, to.y, to.z, Set.of(), player.getYRot(), player.getXRot(), true);
        player.resetFallDistance();
        Bonds.sound(player, ModSounds.TELEPORT, 1.0F);
        Bonds.hearts(player, 16);
        partner.sendSystemMessage(Component.translatable("filorosso.msg.reached", player.getGameProfile().name()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    // ------------------------------------------------------------------ level 6: the slingshot

    /** The velocity the slingshot gives: where you look, always a little upwards, never too fast. */
    public static Vec3 slingVelocity(Vec3 look) {
        Vec3 v = look.scale(SLING_SPEED);
        v = new Vec3(v.x, Math.max(0.55, v.y + 0.45), v.z);
        return v.length() > SLING_MAX ? v.normalize().scale(SLING_MAX) : v;
    }

    private static boolean sling(ServerPlayer player, ServerPlayer partner, Couple couple, long now) {
        if (partner.level() != player.level() || partner.distanceTo(player) > Events.length(player.level().getServer()) + 4.0) {
            deny(player, "filorosso.ability.together");
            return false;
        }
        Vec3 v = slingVelocity(player.getLookAngle());
        for (ServerPlayer p : new ServerPlayer[]{player, partner}) {
            fling(p, v);
            p.resetFallDistance();
            p.setAttached(ModData.POWERS, ModData.powers(p).withSling(now + 160));
            AIRBORNE.put(p.getUUID(), 0);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.SLING, SoundSource.PLAYERS, 1.2F, p == player ? 1.0F : 1.1F);
        }
        fx(player, Net.ThreadFx.SLING, player.getId(), partner.getId());
        return true;
    }

    /** Back on the ground after the slingshot: a crash that throws away whatever is there. */
    private static void land(ServerPlayer p, long now) {
        ServerLevel level = (ServerLevel) p.level();
        p.setAttached(ModData.POWERS, ModData.powers(p).withSling(0L));
        p.resetFallDistance();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.LANDING, SoundSource.PLAYERS, 1.4F, 0.9F + p.getRandom().nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.2, p.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 0.5, p.getZ(), 10, 1.4, 0.2, 1.4, 0.1);
        level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 24, 1.5, 0.05, 1.5, 0.08);
        DamageSource source = level.damageSources().source(ModDamage.THREAD, p, p);
        AABB box = p.getBoundingBox().inflate(LANDING_RADIUS, 1.5, LANDING_RADIUS);
        int hits = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> hostile(e) && e.distanceTo(p) < LANDING_RADIUS + 1.0)) {
            if (e.hurtServer(level, source, LANDING_DAMAGE)) {
                hits++;
                Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                e.knockback(1.0, -away.x, -away.z, source, LANDING_DAMAGE);
            }
        }
        Challenges.add(level.getServer(), Bonds.coupleOf(p), Challenges.SLING_HITS, hits);
    }

    // ------------------------------------------------------------------ level 7: the lasso

    /** Who the lasso can catch: anything alive but your partner (and armor stands). */
    private static LivingEntity lassoTarget(ServerPlayer player, ServerPlayer partner) {
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 to = from.add(look.scale(LASSO_RANGE));
        HitResult block = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            to = block.getLocation();
        }
        AABB box = player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, from, to, box,
                e -> e instanceof LivingEntity l && l.isAlive() && e != partner && !(e instanceof ArmorStand) && !e.isSpectator(), from.distanceToSqr(to));
        if (hit != null && hit.getEntity() instanceof LivingEntity l) {
            return l;
        }
        // a little help: the closest thing near where you look
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(LASSO_RANGE),
                e -> e.isAlive() && e != player && e != partner && !(e instanceof ArmorStand) && !e.isSpectator())) {
            Vec3 to2 = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(from);
            double d = to2.length();
            if (d > LASSO_RANGE || d < 0.5) {
                continue;
            }
            double angle = Math.acos(Math.max(-1.0, Math.min(1.0, to2.normalize().dot(look))));
            if (angle > 0.12 || !player.hasLineOfSight(e)) {
                continue;
            }
            double score = angle * 20.0 + d * 0.05;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private static boolean lasso(ServerPlayer player, ServerPlayer partner, Couple couple, long now) {
        LivingEntity target = lassoTarget(player, partner);
        if (target == null) {
            deny(player, "filorosso.ability.lasso_none");
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        Tie old = TIED.remove(target.getId());
        if (old != null) {
            release(old, now);
        }
        long until = now + LASSO_TICKS;
        boolean hadAi = !(target instanceof Mob mob) || !mob.isNoAi();
        Vec3 pullTo = null;
        if (target instanceof ServerPlayer victim && com.filorosso.world.Puppets.isPuppet(victim)) {
            // a puppet of the Burattinaio: the lasso pulls it off its strings
            com.filorosso.world.Puppets.free(level, victim, player);
        }
        if (target instanceof ServerPlayer victim) {
            // a friend: pulled to you, and slowed for a moment
            Vec3 dir = player.position().subtract(victim.position());
            double d = dir.length();
            if (d > 1.5) {
                fling(victim, dir.normalize().scale(Math.min(1.6, 0.4 + d * 0.1)).add(0.0, 0.4, 0.0));
            }
            victim.resetFallDistance();
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
            until = now + 30;
            victim.sendSystemMessage(Component.translatable("filorosso.msg.lassoed", player.getGameProfile().name()).withStyle(ChatFormatting.LIGHT_PURPLE),
                    true);
        } else if (target instanceof BondBoss boss) {
            boss.lassoed(until, couple);
        } else if (target instanceof Mob mob) {
            // tied up where it is, pulled a little towards you
            Vec3 dir = player.position().subtract(mob.position());
            pullTo = mob.position().add(dir.scale(Math.min(0.6, Math.max(0.0, (dir.length() - 2.5) / Math.max(1.0, dir.length())))));
            mob.getNavigation().stop();
            mob.setTarget(null);
            if (hadAi) {
                mob.setNoAi(true);
                mob.addTag(TIED_TAG);
            }
        }
        TIED.put(target.getId(), new Tie(target, player, couple, now, until, hadAi, pullTo));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.LASSO, SoundSource.PLAYERS, 1.2F, 1.0F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.LASSO, SoundSource.PLAYERS, 1.0F, 1.3F);
        fx(player, Net.ThreadFx.LASSO, player.getId(), target.getId());
        sendTied(level, target, player, until);
        return true;
    }

    private static void sendTied(ServerLevel level, Entity target, ServerPlayer by, long until) {
        Net.Tied packet = new Net.Tied(target.getId(), by.getId(), until);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(target) < 128 * 128) {
                Bonds.send(viewer, packet);
            }
        }
    }

    private static void release(Tie t, long now) {
        if (t.target() instanceof Mob mob && t.hadAi() && mob.isAlive()) {
            mob.setNoAi(false);
            mob.removeTag(TIED_TAG);
        }
        if (t.target().level() instanceof ServerLevel level) {
            sendTied(level, t.target(), t.by(), 0L);
        }
    }

    /** Is this entity tied by this couple right now? (their hits count as couple hits) */
    public static boolean tiedBy(Entity target, Couple couple) {
        Tie t = TIED.get(target.getId());
        return t != null && couple != null && t.couple() == couple;
    }

    // ------------------------------------------------------------------ level 8: the dome

    private static boolean dome(ServerPlayer player, ServerPlayer partner, Couple couple, long now) {
        if (partner.level() != player.level() || partner.distanceTo(player) > 10.0) {
            deny(player, "filorosso.ability.together");
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        Vec3 c = player.position().add(partner.position()).scale(0.5);
        c = new Vec3(c.x, Math.min(player.getY(), partner.getY()), c.z);
        Dome d = new Dome(nextDome++, level, c, DOME_RADIUS, now, now + DOME_TICKS, couple);
        DOMES.add(d);
        level.playSound(null, c.x, c.y, c.z, ModSounds.DOME, SoundSource.PLAYERS, 1.6F, 1.0F);
        Net.Dome packet = new Net.Dome(d.id(), c.x, c.y, c.z, (float) d.radius(), now, d.end(), Palette.rgb(couple.color));
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(c) < 128 * 128) {
                Bonds.send(viewer, packet);
            }
        }
        return true;
    }

    /** Inside a dome of its own couple: almost no damage. */
    public static boolean shielded(ServerPlayer player) {
        Couple couple = Bonds.coupleOf(player);
        if (couple == null) {
            return false;
        }
        for (Dome d : DOMES) {
            if (d.couple() == couple && d.level() == player.level() && player.position().distanceTo(d.center()) < d.radius() + 0.3) {
                return true;
            }
        }
        return false;
    }

    private static void tickDome(Dome d, long now) {
        ServerLevel level = d.level();
        Vec3 c = d.center();
        double r = d.radius();
        AABB box = new AABB(c.x - r - 2, c.y - 2, c.z - r - 2, c.x + r + 2, c.y + r + 2, c.z + r + 2);
        for (Entity e : level.getEntities((Entity) null, box, e -> e.isAlive() && !(e instanceof Player))) {
            Vec3 from = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(c);
            double dist = from.length();
            if (e instanceof Projectile proj) {
                if (dist < r + 0.6 && !DEFLECTED.contains(e.getId()) && !(proj.getOwner() instanceof ServerPlayer owner && d.couple().has(owner.getUUID()))) {
                    DEFLECTED.add(e.getId());
                    Vec3 v = e.getDeltaMovement();
                    Vec3 out = dist < 1.0E-3 ? new Vec3(0, 1, 0) : from.normalize();
                    // bounced off the surface, a little faster
                    Vec3 bounced = v.subtract(out.scale(2.0 * v.dot(out))).scale(1.1);
                    if (bounced.dot(out) < 0.2) {
                        bounced = bounced.add(out.scale(0.6));
                    }
                    e.setDeltaMovement(bounced);
                    e.syncVelocity = true;
                    Challenges.add(level.getServer(), d.couple(), Challenges.DEFLECTS, 1);
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.DOME_DEFLECT, SoundSource.PLAYERS, 1.0F, 1.2F + level.getRandom().nextFloat() * 0.3F);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY(), e.getZ(), 8, 0.1, 0.1, 0.1, 0.2);
                }
                continue;
            }
            if (!(e instanceof LivingEntity living) || !hostile(living) || e instanceof BondBoss) {
                continue;
            }
            if (dist < r + 0.5) {
                Vec3 out = new Vec3(from.x, 0, from.z);
                out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
                living.setDeltaMovement(out.scale(0.7).add(0, 0.3, 0));
                living.syncVelocity = true;
                if (now % 10 == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 1.0, e.getZ(), 6, 0.3, 0.4, 0.3, 0.1);
                }
            }
        }
        if (now % 40 == 0) {
            for (ServerPlayer p : level.players()) {
                if (d.couple().has(p.getUUID()) && p.position().distanceTo(c) < r + 0.3) {
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, true, false, true));
                }
            }
        }
    }

    // ------------------------------------------------------------------ level 9: the kiss

    private static boolean kiss(ServerPlayer player, ServerPlayer partner, Couple couple, long now) {
        if (partner.level() != player.level() || partner.distanceTo(player) > KISS_DISTANCE) {
            deny(player, "filorosso.ability.kiss_close");
            return false;
        }
        Long asked = KISSES.get(partner.getUUID());
        if (asked != null && now - asked <= KISS_WINDOW) {
            // they asked first: here is the kiss
            KISSES.remove(partner.getUUID());
            KISSES.remove(player.getUUID());
            for (ServerPlayer p : new ServerPlayer[]{player, partner}) {
                p.setAttached(ModData.POWERS, ModData.powers(p).withKiss(0L));
            }
            Vec3 mid = player.position().add(partner.position()).scale(0.5);
            for (ServerPlayer p : new ServerPlayer[]{player, partner}) {
                Vec3 to = mid.subtract(p.position()).multiply(1, 0, 1);
                double d = to.length();
                if (d > 0.6) {
                    fling(p, to.normalize().scale(Math.min(0.9, (d - 0.4) * 0.35)).add(0, 0.15, 0));
                }
                p.resetFallDistance();
            }
            ServerLevel level = (ServerLevel) player.level();
            level.playSound(null, mid.x, mid.y, mid.z, ModSounds.KISS, SoundSource.PLAYERS, 1.5F, 1.0F);
            level.sendParticles(ParticleTypes.HEART, mid.x, mid.y + 1.8, mid.z, 20, 0.5, 0.4, 0.5, 0.15);
            BURSTS.add(new Burst(player.getUUID(), partner.getUUID(), now + 12));
            return true;
        }
        if (KISSES.containsKey(player.getUUID()) && now - KISSES.get(player.getUUID()) <= KISS_WINDOW) {
            deny(player, "filorosso.ability.kiss_wait", partner.getGameProfile().name());
            return false;
        }
        KISSES.put(player.getUUID(), now);
        player.setAttached(ModData.POWERS, ModData.powers(player).withKiss(now + KISS_WINDOW));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.KISS_ASK, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.sendSystemMessage(Component.translatable("filorosso.ability.kiss_sent", partner.getGameProfile().name()).withStyle(ChatFormatting.LIGHT_PURPLE),
                true);
        Bonds.title(partner, Component.empty(), Component.translatable("filorosso.ability.kiss_asked", player.getGameProfile().name())
                .withStyle(ChatFormatting.LIGHT_PURPLE), 4, 60, 10);
        return false;
    }

    private static void burst(MinecraftServer server, Burst b) {
        ServerPlayer a = Bonds.player(server, b.a());
        ServerPlayer c = Bonds.player(server, b.b());
        if (a == null || c == null || a.level() != c.level()) {
            return;
        }
        ServerLevel level = (ServerLevel) a.level();
        Vec3 mid = a.position().add(c.position()).scale(0.5);
        level.playSound(null, mid.x, mid.y, mid.z, ModSounds.DOME, SoundSource.PLAYERS, 2.0F, 1.6F);
        level.sendParticles(ParticleTypes.HEART, mid.x, mid.y + 1.0, mid.z, 60, 3.0, 1.0, 3.0, 0.3);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y + 1.0, mid.z, 1, 0, 0, 0, 0);
        fx(a, Net.ThreadFx.KISS, a.getId(), c.getId());
        Couple couple = Bonds.coupleOf(a);
        DamageSource source = level.damageSources().source(ModDamage.THREAD, a, a);
        AABB box = new AABB(mid, mid).inflate(KISS_RADIUS, 6.0, KISS_RADIUS);
        int hits = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> hostile(e) && e.position().distanceTo(mid) < KISS_RADIUS + e.getBbWidth())) {
            if (e instanceof BondBoss boss) {
                boss.kissed(couple);
            }
            if (e.hurtServer(level, source, KISS_DAMAGE)) {
                hits++;
                Vec3 away = e.position().subtract(mid).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                e.knockback(1.6, -away.x, -away.z, source, KISS_DAMAGE);
            }
        }
        for (ServerPlayer p : new ServerPlayer[]{a, c}) {
            p.setHealth(p.getMaxHealth());
            p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 30, 1));
            p.clearFire();
        }
        if (couple != null) {
            Affinity.add(server, couple, 10, true);
            Challenges.add(server, couple, Challenges.KISS_HITS, hits);
        }
    }

    // ------------------------------------------------------------------ level 10: Eterni

    private static boolean eternal(ServerPlayer player, ServerPlayer partner, Couple couple, long now) {
        if (partner.level() != player.level() || partner.distanceTo(player) > 32.0) {
            deny(player, "filorosso.ability.together");
            return false;
        }
        long until = now + ETERNAL_TICKS;
        // a partner on the ground gets up: nobody falls while you are eternal
        Revive.standUp(partner, player);
        for (ServerPlayer p : new ServerPlayer[]{player, partner}) {
            p.setAttached(ModData.POWERS, ModData.powers(p).withEternal(until));
            if (!p.isCreative() && !p.isSpectator()) {
                p.getAbilities().mayfly = true;
                p.addTag(ETERNAL_TAG);
                p.onUpdateAbilities();
            }
            p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ETERNAL_TICKS, 1));
            p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ETERNAL_TICKS, 1));
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, ETERNAL_TICKS, 0));
            fling(p, new Vec3(0, 0.8, 0));
            Bonds.title(p, Component.translatable("filorosso.ability.eternal").withStyle(s -> s.withColor(BondLevel.DESTINY.color)),
                    Component.translatable("filorosso.ability.eternal.sub").withStyle(ChatFormatting.GOLD), 5, 40, 10);
        }
        ServerLevel level = (ServerLevel) player.level();
        Vec3 mid = player.position().add(partner.position()).scale(0.5);
        level.playSound(null, mid.x, mid.y, mid.z, ModSounds.ETERNAL, SoundSource.PLAYERS, 2.0F, 1.0F);
        fx(player, Net.ThreadFx.ETERNAL, player.getId(), partner.getId());
        return true;
    }

    public static boolean eternal(ServerPlayer p) {
        return ModData.powers(p).eternal(now(p.level().getServer()));
    }

    private static void endEternal(ServerPlayer p, boolean announce) {
        Powers powers = ModData.powers(p);
        if (powers.eternalUntil() == 0L && !p.entityTags().contains(ETERNAL_TAG)) {
            return;
        }
        p.setAttached(ModData.POWERS, powers.withEternal(0L));
        if (p.entityTags().contains(ETERNAL_TAG)) {
            p.removeTag(ETERNAL_TAG);
            if (!p.isCreative() && !p.isSpectator()) {
                boolean wasFlying = p.getAbilities().flying;
                p.getAbilities().mayfly = false;
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                if (wasFlying || !p.onGround()) {
                    p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0));
                }
            }
        }
        if (announce) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.ETERNAL_END, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    // ------------------------------------------------------------------ every tick

    /** Monsters, and bosses: what the powers hurt (never players, pets or villagers). */
    public static boolean hostile(LivingEntity e) {
        return e.isAlive() && !(e instanceof Player) && !(e instanceof ArmorStand) && (e instanceof Enemy || e instanceof BondBoss);
    }

    /** No fall damage while flung by the slingshot or flying with "Eterni". */
    public static boolean noFall(ServerPlayer p) {
        long now = now(p.level().getServer());
        Powers powers = ModData.powers(p);
        return powers.slinging(now) || powers.eternal(now) || AIRBORNE.containsKey(p.getUUID());
    }

    public static void tick(MinecraftServer server) {
        long now = now(server);
        // flung by the slingshot: a trail, then the crash
        Iterator<Map.Entry<UUID, Integer>> air = AIRBORNE.entrySet().iterator();
        while (air.hasNext()) {
            Map.Entry<UUID, Integer> e = air.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive()) {
                air.remove();
                continue;
            }
            int t = e.getValue() + 1;
            e.setValue(t);
            p.resetFallDistance();
            Couple couple = Bonds.coupleOf(p);
            if (t % 2 == 0) {
                int rgb = couple != null ? Palette.rgb(couple.color) : 0xD8202A;
                com.filorosso.util.Fx.forOthers((ServerLevel) p.level(), p, new DustParticleOptions(rgb, 1.6F), p.getX(), p.getY() + 0.6, p.getZ(), 3, 0.2,
                        0.3, 0.2, 0.0);
            }
            boolean down = p.onGround() || p.isInWater() || p.onClimbable();
            if (down && t > 4 || t > 160) {
                air.remove();
                if (down && !p.isInWater()) {
                    land(p, now);
                } else {
                    p.setAttached(ModData.POWERS, ModData.powers(p).withSling(0L));
                }
            }
        }
        // lassoes
        Iterator<Tie> ties = TIED.values().iterator();
        while (ties.hasNext()) {
            Tie t = ties.next();
            LivingEntity e = t.target();
            if (!e.isAlive() || e.isRemoved() || now >= t.until() || t.by().isRemoved()) {
                ties.remove();
                release(t, now);
                continue;
            }
            if (e instanceof Mob mob) {
                mob.getNavigation().stop();
                if (t.pullTo() != null && now - t.start() < 10) {
                    Vec3 step = t.pullTo().subtract(mob.position()).scale(0.25);
                    mob.move(MoverType.SELF, new Vec3(step.x, Math.max(step.y, 0.0) + 0.02, step.z));
                } else if (!mob.onGround()) {
                    mob.move(MoverType.SELF, new Vec3(0, -0.3, 0));
                }
            }
            if (now % 5 == 0 && e.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
            }
        }
        // domes
        Iterator<Dome> domes = DOMES.iterator();
        while (domes.hasNext()) {
            Dome d = domes.next();
            if (now >= d.end()) {
                domes.remove();
                d.level().playSound(null, d.center().x, d.center().y, d.center().z, ModSounds.DOME, SoundSource.PLAYERS, 1.0F, 0.6F);
                continue;
            }
            tickDome(d, now);
        }
        if (DOMES.isEmpty() && !DEFLECTED.isEmpty()) {
            DEFLECTED.clear();
        }
        // kisses
        KISSES.entrySet().removeIf(e -> now - e.getValue() > KISS_WINDOW);
        Iterator<Burst> bursts = BURSTS.iterator();
        while (bursts.hasNext()) {
            Burst b = bursts.next();
            if (now >= b.at()) {
                bursts.remove();
                burst(server, b);
            }
        }
        // waiting for a kiss: a heart over your head
        if (now % 6 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Powers powers = ModData.powers(p);
                if (powers.kissing(now)) {
                    ((ServerLevel) p.level()).sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 2.3, p.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
                }
                if (powers.eternal(now) && p.level() instanceof ServerLevel level) {
                    com.filorosso.util.Fx.forOthers(level, p, ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.4, 0.6, 0.4, 0.02);
                }
            }
        }
        // the end of "Eterni"
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Powers powers = ModData.powers(p);
            if (powers.eternalUntil() != 0L && now >= powers.eternalUntil()) {
                endEternal(p, true);
            }
            if (powers.kissUntil() != 0L && now >= powers.kissUntil()) {
                p.setAttached(ModData.POWERS, powers.withKiss(0L));
            }
        }
    }

    /** Ends everything that is going on (server stopping). */
    public static void stop(MinecraftServer server) {
        long now = now(server);
        for (Tie t : TIED.values()) {
            release(t, now);
        }
        TIED.clear();
        DOMES.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            endEternal(p, false);
        }
    }
}
