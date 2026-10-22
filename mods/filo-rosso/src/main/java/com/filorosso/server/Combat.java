package com.filorosso.server;

import com.filorosso.data.BondType;
import com.filorosso.data.Couple;
import com.filorosso.data.ModData;
import com.filorosso.entity.BondBoss;
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
 * damage, and the only real way to hurt the boss), the shield and fire fates.
 */
public final class Combat {
    private Combat() {
    }

    /** Who hit what, when: target entity id, attacker, game time. */
    private static final Map<Integer, Map<UUID, Long>> HITS = new HashMap<>();
    /** Last couple hit on each target: one every half second at most (no farming a mob with fast clicks). */
    private static final Map<Integer, Long> LAST_COMBO = new HashMap<>();
    public static final int COMBO_WINDOW = 25;

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
            // flung by the slingshot or flying with "Eterni": the thread holds you
            if (source.is(DamageTypeTags.IS_FALL) && Abilities.noFall(player)) {
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
     * the boss only really takes couple hits, the shield fate protects partners standing close.
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
                combo = hitBy(target, partner, now, COMBO_WINDOW) && !target.getUUID().equals(partner) && (last == null || now - last >= 10);
                if (combo) {
                    LAST_COMBO.put(target.getId(), now);
                }
            }
            HITS.computeIfAbsent(target.getId(), k -> new HashMap<>()).put(attacker.getUUID(), now);
            // tied up by your lasso: every hit of yours is a couple hit
            if (!combo && couple != null && Abilities.tiedBy(target, couple)) {
                combo = true;
            }
            if (combo) {
                out *= comboMultiplier(couple.level());
                comboFx(server, attacker, target, couple);
            }
            if (target instanceof BondBoss boss) {
                out = boss.adjustDamage(out, combo, couple);
            }
        } else if (target instanceof BondBoss boss && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            out = boss.adjustDamage(out, false, null);
        }
        // under their dome of threads a couple takes hardly anything
        if (target instanceof ServerPlayer player && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && Abilities.shielded(player)) {
            out *= 0.25F;
        }
        // the shield fate: close together, the couple takes less damage
        if (target instanceof ServerPlayer player && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            Couple couple = Bonds.coupleOf(player);
            if (couple != null && couple.bondType() == BondType.SHIELD) {
                ServerPlayer partner = Bonds.partner(player);
                if (partner != null && partner.level() == player.level() && partner.distanceTo(player) < 4.0) {
                    out *= 0.67F;
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

    /** How much more a couple hit does: x1.4 at the first level, +0.15 a level up to the fifth, then +0.07. */
    public static float comboMultiplier(com.filorosso.data.BondLevel level) {
        int n = level.ordinal();
        return 1.4F + 0.15F * Math.min(n, 4) + 0.07F * Math.max(0, n - 4);
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
