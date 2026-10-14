package com.jumpscares.scare;

import com.jumpscares.Jumpscares;
import com.jumpscares.entity.ScarePearl;
import com.jumpscares.net.ScareNet;
import com.jumpscares.registry.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Server side of a jumpscare: tells the victim's client to play it, lets the players around
 * watch it, and at the moment of impact makes the scream heard nearby and the victim jump back.
 */
public final class Scares {
    /** How close to a pearl's splash you have to be. */
    public static final double SPLASH = 2.5;
    /** Players this close to a victim see the monster jump at them. */
    public static final double WITNESS_RANGE = 48.0;
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(long due, UUID player, Consumer<ServerPlayer> action) {
    }

    private Scares() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Scares::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        List<Pending> due = new ArrayList<>();
        PENDING.removeIf(p -> {
            if (p.due() <= now) {
                due.add(p);
                return true;
            }
            return false;
        });
        for (Pending p : due) {
            ServerPlayer player = server.getPlayerList().getPlayer(p.player());
            if (player != null) {
                try {
                    p.action().accept(player);
                } catch (RuntimeException e) {
                    Jumpscares.LOGGER.error("Jumpscare for {} failed", player.getScoreboardName(), e);
                }
            }
        }
    }

    /** Runs {@code action} on the player in {@code ticks} ticks, if they are still online. */
    public static void later(ServerPlayer player, int ticks, Consumer<ServerPlayer> action) {
        PENDING.add(new Pending(player.server.getTickCount() + Math.max(1, ticks), player.getUUID(), action));
    }

    /** Scares one player now. False if their client does not have the mod (then nothing is shown to them). */
    public static boolean scare(ServerPlayer victim, ScareKind kind) {
        long seed = victim.getRandom().nextLong();
        boolean shown = ScareNet.sendScare(victim, kind, seed);
        for (ServerPlayer other : victim.serverLevel().players()) {
            if (other != victim && other.distanceToSqr(victim) < WITNESS_RANGE * WITNESS_RANGE) {
                ScareNet.sendWitness(other, victim, kind, seed);
            }
        }
        later(victim, kind.impactTick, p -> impact(p, kind));
        return shown;
    }

    private static void impact(ServerPlayer victim, ScareKind kind) {
        if (!victim.isAlive()) {
            return;
        }
        ServerLevel level = victim.serverLevel();
        // everyone else hears it screaming in the victim's face (the victim plays its own, louder)
        level.playSound(victim, victim.getX(), victim.getEyeY(), victim.getZ(), ModSounds.scream(kind), SoundSource.HOSTILE, 2.5F, 1.0F);
        level.playSound(victim, victim.getX(), victim.getEyeY(), victim.getZ(), ModSounds.STING, SoundSource.HOSTILE, 1.5F, 1.0F);
        burst(level, kind, victim.getEyePosition().add(victim.getViewVector(1.0F).scale(0.7)));
        // the victim jumps back in fright
        if (!victim.isSpectator() && !victim.getAbilities().flying) {
            Vec3 look = victim.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
            Vec3 back = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize().scale(kind == ScareKind.DEMON ? -0.25 : -0.15);
            victim.setDeltaMovement(victim.getDeltaMovement().add(back.x, victim.onGround() ? 0.36 : 0.0, back.z));
            victim.hurtMarked = true;
        }
        switch (kind) {
            case SPIDER -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            case CORPSE -> victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 140, 0));
            case LADY -> victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
            default -> {
            }
        }
    }

    /** What bursts out at the victim's head, for the players watching. */
    private static void burst(ServerLevel level, ScareKind kind, Vec3 at) {
        switch (kind) {
            case CLOWN -> {
                for (int color : new int[]{0xE8102A, 0xFFD21A, 0x2050E0, 0x20B040}) {
                    level.sendParticles(new DustParticleOptions(new Vector3f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                            (color & 255) / 255.0F), 1.0F), at.x, at.y, at.z, 5, 0.5, 0.5, 0.5, 0.0);
                }
            }
            case LADY -> level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 10, 0.4, 0.4, 0.4, 0.04);
            case SPIDER -> level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)),
                    at.x, at.y, at.z, 14, 0.4, 0.4, 0.4, 0.08);
            case CORPSE -> level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.ROTTEN_FLESH)),
                    at.x, at.y, at.z, 14, 0.4, 0.4, 0.4, 0.08);
            case DEMON -> {
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 16, 0.4, 0.4, 0.4, 0.06);
                level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 4, 0.3, 0.3, 0.3, 0.0);
            }
        }
    }

    /** A scare pearl landed: scare whoever it hit and anyone right next to the splash. */
    public static void pearlHit(ScarePearl pearl, HitResult hit) {
        ServerLevel level = (ServerLevel) pearl.level();
        ScareKind kind = pearl.kind();
        Entity owner = pearl.getOwner();
        Vec3 at = hit.getLocation();
        List<ServerPlayer> victims = new ArrayList<>();
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ServerPlayer player && player != owner) {
            victims.add(player);
        }
        AABB area = new AABB(at, at).inflate(SPLASH);
        for (ServerPlayer player : level.players()) {
            if (player != owner && !player.isSpectator() && !victims.contains(player) && player.getBoundingBox().intersects(area)) {
                victims.add(player);
            }
        }
        // thrown at your own feet: try it on yourself
        if (victims.isEmpty() && owner instanceof ServerPlayer self && self.getBoundingBox().intersects(area)) {
            victims.add(self);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 0.5F, 1.7F);
        for (ServerPlayer victim : victims) {
            scare(victim, kind);
        }
        if (!victims.isEmpty() && owner instanceof ServerPlayer thrower && !victims.contains(thrower)) {
            Component who = victims.size() == 1 ? victims.get(0).getDisplayName() : Component.literal(String.valueOf(victims.size()));
            thrower.displayClientMessage(Component.translatable(victims.size() == 1 ? "jumpscares.scared.one" : "jumpscares.scared.many",
                    who, kind.displayName()).withColor(kind.color), true);
            thrower.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 0.6F);
        }
    }
}
