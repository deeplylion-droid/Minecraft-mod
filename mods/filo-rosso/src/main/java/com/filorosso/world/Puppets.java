package com.filorosso.world;

import com.filorosso.data.Couple;
import com.filorosso.data.ModData;
import com.filorosso.entity.Burattinaio;
import com.filorosso.entity.ModEntities;
import com.filorosso.entity.StringKnot;
import com.filorosso.server.Affinity;
import com.filorosso.server.Bonds;
import com.filorosso.server.Challenges;
import com.filorosso.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Burattinaio's puppets: players whose limbs it has tied with its strings. It walks them (on
 * their own clients, see client/Puppet) towards their partner and makes them hit, until someone cuts
 * the knot over their head: three hits, one with the scissors, or the partner pulling them free with
 * "Tira a te" or the lasso. If nobody does, it lifts them up and lets them drop.
 */
public final class Puppets {
    private Puppets() {
    }

    /** The puppet hits its partner at most this often (ticks). */
    public static final int HIT_EVERY = 24;
    public static final float HIT_DAMAGE = 3.0F;

    private static final class Puppet {
        final UUID victim;
        final int puppeteer;
        final int knot;
        final long until;
        long lastHit;

        Puppet(UUID victim, int puppeteer, int knot, long until) {
            this.victim = victim;
            this.puppeteer = puppeteer;
            this.knot = knot;
            this.until = until;
        }
    }

    private static final Map<UUID, Puppet> ACTIVE = new HashMap<>();

    public static int count(MinecraftServer server) {
        return ACTIVE.size();
    }

    public static boolean isPuppet(ServerPlayer p) {
        return ACTIVE.containsKey(p.getUUID());
    }

    /** Who it ties: players close enough who are not puppets yet, the ones with their partner around first. */
    public static List<ServerPlayer> chooseVictims(ServerLevel level, Burattinaio boss, int n) {
        List<ServerPlayer> withPartner = new ArrayList<>();
        List<ServerPlayer> others = new ArrayList<>();
        for (ServerPlayer p : boss.playersNear(level, 32.0)) {
            if (isPuppet(p) || ModData.downed(p)) {
                continue;
            }
            ServerPlayer partner = Bonds.partner(p);
            if (partner != null && partner.level() == level && partner.isAlive() && !isPuppet(partner)) {
                withPartner.add(p);
            } else {
                others.add(p);
            }
        }
        Collections.shuffle(withPartner, new java.util.Random(boss.getRandom().nextLong()));
        Collections.shuffle(others, new java.util.Random(boss.getRandom().nextLong()));
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : withPartner) {
            // never both halves of a couple: one of them must stay free to cut the strings
            ServerPlayer partner = Bonds.partner(p);
            if (out.size() < n && !out.contains(partner)) {
                out.add(p);
            }
        }
        for (ServerPlayer p : others) {
            if (out.size() < n) {
                out.add(p);
            }
        }
        return out;
    }

    public static void start(ServerLevel level, Burattinaio boss, ServerPlayer victim, int ticks) {
        long now = level.getServer().overworld().getGameTime();
        ServerPlayer partner = Bonds.partner(victim);
        boolean partnerHere = partner != null && partner.level() == level && partner.isAlive();
        StringKnot knot = ModEntities.STRING_KNOT.create(level, EntitySpawnReason.EVENT);
        int knotId = -1;
        if (knot != null) {
            knot.tie(victim, boss);
            level.addFreshEntity(knot);
            knotId = knot.getId();
        }
        ACTIVE.put(victim.getUUID(), new Puppet(victim.getUUID(), boss.getId(), knotId, now + ticks));
        victim.setAttached(ModData.POWERS, ModData.powers(victim).withPuppet(now + ticks, boss.getId(), partnerHere ? partner.getId() : -1));
        level.playSound(null, victim.getX(), victim.getY() + 1.0, victim.getZ(), ModSounds.PUPPET_STRINGS, SoundSource.HOSTILE, 1.6F, 1.1F);
        level.sendParticles(new DustParticleOptions(0xD8202A, 1.6F), victim.getX(), victim.getY() + 2.5, victim.getZ(), 30, 0.4, 1.0, 0.4, 0.0);
        Bonds.title(victim, Component.translatable("filorosso.puppet.title").withStyle(ChatFormatting.DARK_RED),
                Component.translatable(partnerHere ? "filorosso.puppet.sub" : "filorosso.puppet.sub_alone",
                        partnerHere ? partner.getGameProfile().name() : "").withStyle(ChatFormatting.GRAY), 5, 50, 10);
        if (partnerHere) {
            Bonds.title(partner, Component.empty(), Component.translatable("filorosso.puppet.partner", victim.getGameProfile().name())
                    .withStyle(ChatFormatting.RED), 5, 60, 10);
        }
    }

    /** The strings snap: the puppet is free. */
    public static void free(ServerLevel level, ServerPlayer victim, ServerPlayer by) {
        Puppet p = ACTIVE.remove(victim.getUUID());
        if (p == null) {
            return;
        }
        end(level, victim, p);
        level.playSound(null, victim.getX(), victim.getY() + 2.0, victim.getZ(), ModSounds.SNAP, SoundSource.PLAYERS, 1.6F, 1.0F);
        level.sendParticles(new DustParticleOptions(0xFF4050, 1.8F), victim.getX(), victim.getY() + 2.6, victim.getZ(), 40, 0.6, 0.6, 0.6, 0.0);
        victim.sendSystemMessage(Component.translatable("filorosso.puppet.freed", by.getGameProfile().name()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        Couple couple = Bonds.coupleOf(victim);
        if (couple != null && couple.has(by.getUUID())) {
            Affinity.add(level.getServer(), couple, 6, true);
            Challenges.add(level.getServer(), couple, Challenges.FREED, 1);
        }
    }

    private static void end(ServerLevel level, ServerPlayer victim, Puppet p) {
        victim.setAttached(ModData.POWERS, ModData.powers(victim).withPuppet(0L, -1, -1));
        Entity knot = level.getEntity(p.knot);
        if (knot != null) {
            knot.discard();
        }
    }

    public static void freeAll(MinecraftServer server) {
        for (Puppet p : ACTIVE.values()) {
            ServerPlayer victim = server.getPlayerList().getPlayer(p.victim);
            if (victim != null) {
                end((ServerLevel) victim.level(), victim, p);
            }
        }
        ACTIVE.clear();
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        Iterator<Puppet> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Puppet p = it.next();
            ServerPlayer victim = server.getPlayerList().getPlayer(p.victim);
            if (victim == null || !victim.isAlive() || ModData.downed(victim)) {
                it.remove();
                if (victim != null) {
                    end((ServerLevel) victim.level(), victim, p);
                }
                continue;
            }
            ServerLevel level = (ServerLevel) victim.level();
            Entity boss = level.getEntity(p.puppeteer);
            if (now >= p.until || !(boss instanceof Burattinaio b) || !b.isAlive()) {
                // nobody cut the strings: it lifts the puppet up and lets go
                it.remove();
                end(level, victim, p);
                if (boss instanceof Burattinaio b && b.isAlive()) {
                    victim.setDeltaMovement(new Vec3(0.0, 1.35, 0.0));
                    victim.connection.send(new ClientboundSetEntityMotionPacket(victim));
                    victim.syncVelocity = true;
                    level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), ModSounds.SNAP, SoundSource.HOSTILE, 1.4F, 0.6F);
                    victim.sendSystemMessage(Component.translatable("filorosso.puppet.dropped").withStyle(ChatFormatting.DARK_RED), true);
                }
                continue;
            }
            // close to the partner: it makes the puppet hit
            int target = ModData.powers(victim).puppetTarget();
            Entity partner = target >= 0 ? level.getEntity(target) : null;
            if (partner instanceof ServerPlayer q && q.isAlive() && !q.isSpectator() && q.distanceTo(victim) < 2.3 && now - p.lastHit >= HIT_EVERY) {
                p.lastHit = now;
                victim.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                q.hurtServer(level, b.damageSources().mobAttack(b), HIT_DAMAGE);
                level.playSound(null, q.getX(), q.getY() + 1.0, q.getZ(), ModSounds.PUPPET_HIT, SoundSource.HOSTILE, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
            }
        }
    }
}
