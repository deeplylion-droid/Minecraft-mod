package com.filorosso.server;

import com.filorosso.data.BondType;
import com.filorosso.data.Couple;
import com.filorosso.data.ModData;
import com.filorosso.entity.Spezzacuori;
import com.filorosso.net.Net;
import com.filorosso.sound.ModSounds;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Damage rules of the bond: couple hits (both partners hit the same target within a second: more
 * damage, and the only real way to hurt Lo Spezzacuori), shared hearts, the fire bond's immunity.
 */
public final class Combat {
    private Combat() {
    }

    /** Who hit what, when: target entity id, attacker, game time. */
    private static final Map<Integer, Map<UUID, Long>> HITS = new HashMap<>();
    /** Last couple hit on each target: one every half second at most (no farming a mob with fast clicks). */
    private static final Map<Integer, Long> LAST_COMBO = new HashMap<>();
    public static final int COMBO_WINDOW = 25;
    private static boolean sharing;

    public static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(Combat::allow);
    }

    /** The player who caused this damage (also through arrows and tridents). */
    public static ServerPlayer attacker(DamageSource source) {
        Entity e = source.getEntity();
        if (e instanceof ServerPlayer p) {
            return p;
        }
        if (source.getDirectEntity() instanceof Projectile proj && proj.getOwner() instanceof ServerPlayer p) {
            return p;
        }
        return null;
    }

    public static boolean hitBy(Entity target, UUID attacker, long now, int window) {
        Map<UUID, Long> m = HITS.get(target.getId());
        if (m == null) {
            return false;
        }
        Long t = m.get(attacker);
        return t != null && now - t <= window;
    }

    private static boolean allow(LivingEntity entity, DamageSource source, float amount) {
        if (entity instanceof ServerPlayer player) {
            if (ModData.downed(player) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return false;
            }
            Couple couple = Bonds.coupleOf(player);
            if (couple != null && couple.bondType() == BondType.FIRE && source.is(DamageTypeTags.IS_FIRE)) {
                ServerPlayer partner = Bonds.partner(player);
                if (partner != null && partner.level() == player.level() && partner.distanceTo(player) < 3.5) {
                    player.clearFire();
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Changes the damage about to be dealt (called from the LivingEntity mixin). Couple hits do more,
     * Lo Spezzacuori only really takes couple hits, shared hearts split the damage between partners.
     */
    public static float modify(LivingEntity target, DamageSource source, float amount) {
        if (!(target.level() instanceof ServerLevel level) || amount <= 0.0F) {
            return amount;
        }
        MinecraftServer server = level.getServer();
        long now = server.overworld().getGameTime();
        ServerPlayer attacker = attacker(source);
        float out = amount;
        if (attacker != null && attacker != target) {
            Couple couple = Bonds.coupleOf(attacker);
            boolean combo = false;
            if (couple != null) {
                UUID partner = couple.other(attacker.getUUID());
                Long last = LAST_COMBO.get(target.getId());
                combo = hitBy(target, partner, now, COMBO_WINDOW) && !target.getUUID().equals(partner) && (last == null || now - last >= 10)
                        && target.getInvulnerableTime() <= 10;
                if (combo) {
                    LAST_COMBO.put(target.getId(), now);
                }
            }
            HITS.computeIfAbsent(target.getId(), k -> new HashMap<>()).put(attacker.getUUID(), now);
            if (combo) {
                out *= 1.4F + 0.15F * couple.level().ordinal();
                comboFx(server, attacker, target, couple);
            }
            if (target instanceof Spezzacuori boss) {
                out = boss.adjustDamage(out, combo, couple);
            }
        } else if (target instanceof Spezzacuori boss && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            out = boss.adjustDamage(out, false, null);
        }
        if (target instanceof ServerPlayer player && !sharing && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !ModData.downed(player)
                && !player.isCreative() && !player.isSpectator() && player.getInvulnerableTime() <= 10 && !player.isInvulnerableTo(level, source)) {
            Couple couple = Bonds.coupleOf(player);
            if (couple != null && couple.bondType() == BondType.SHARED) {
                ServerPlayer partner = Bonds.partner(player);
                if (partner != null && partner.isAlive() && !ModData.downed(partner) && partner.level() == player.level()) {
                    float half = out / 2.0F;
                    out -= half;
                    sharing = true;
                    try {
                        partner.hurtServer(level, source, half);
                    } finally {
                        sharing = false;
                    }
                }
            }
        }
        if (now % 600 == 0) {
            LAST_COMBO.values().removeIf(t -> now - t > 200);
            HITS.values().removeIf(m -> {
                m.values().removeIf(t -> now - t > 200);
                return m.isEmpty();
            });
        }
        return out;
    }

    private static void comboFx(MinecraftServer server, ServerPlayer attacker, LivingEntity target, Couple couple) {
        ServerLevel level = (ServerLevel) target.level();
        level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(), 12, 0.4, 0.4, 0.4, 0.2);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.COMBO, SoundSource.PLAYERS, 1.0F, 1.0F);
        ServerPlayer partner = Bonds.partner(attacker);
        for (ServerPlayer p : new ServerPlayer[]{attacker, partner}) {
            if (p != null) {
                p.sendSystemMessage(Component.translatable("filorosso.msg.combo").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
            }
        }
        Net.ThreadFx fx = new Net.ThreadFx(Net.ThreadFx.COMBO, attacker.getId(), target.getId());
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer.level() == level && viewer.distanceToSqr(target) < 64 * 64) {
                Bonds.send(viewer, fx);
            }
        }
        Affinity.add(server, couple, 1, true);
        Challenges.add(server, couple, Challenges.COMBOS, 1);
    }
}
