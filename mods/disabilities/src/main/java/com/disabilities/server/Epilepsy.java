package com.disabilities.server;

import com.disabilities.condition.ModData;
import com.disabilities.condition.Status;
import com.disabilities.net.EpisodeKind;
import com.disabilities.net.Net;
import com.disabilities.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

/**
 * Epilepsy: every 8-14 minutes (and after lightning close by, explosions, hard hits) a seizure. First
 * a few seconds of warning (the world wobbles: time to stop somewhere safe), then 10-14 seconds on
 * the ground convulsing, dropping what you hold, foam at the mouth; then half a minute confused.
 * A friend can put you in the recovery position (right-click) and it ends sooner. Nothing flashes on
 * anyone's screen (people watching a live can be photosensitive).
 */
final class Epilepsy {
    private Epilepsy() {
    }

    static final int AURA_TICKS = 100;
    private static final DustParticleOptions FOAM = new DustParticleOptions(0xF4F4EE, 0.7F);

    static void tick(ServerPlayer player, Ailments.Tracker t, long now, Status status, boolean episodes) {
        ServerLevel level = player.level();
        RandomSource random = player.getRandom();
        boolean seizing = status.seizing(now);
        if (t.wasSeizing && !seizing) {
            // after the seizure: confused and slow for half a minute
            t.wasSeizing = false;
            Ailments.setStatus(player, ModData.status(player).withConfused(now + 600));
            Ailments.sound(player, ModSounds.GASP, 1.0F, 0.9F);
            player.sendOverlayMessage(Component.translatable("disabilities.msg.seizure_over").withStyle(ChatFormatting.GOLD));
            Body.refresh(player);
            return;
        }
        if (seizing) {
            t.wasSeizing = true;
            player.setSprinting(false);
            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
            if (now % 3 == 0) {
                Vec3 mouth = player.position().add(0.0, 0.25, 0.0).add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(0.75));
                level.sendParticles(FOAM, mouth.x, mouth.y, mouth.z, 2, 0.08, 0.04, 0.08, 0.0);
            }
            if (now % 30 == 0) {
                Ailments.sound(player, ModSounds.SEIZURE, 1.0F, 0.9F + random.nextFloat() * 0.2F);
            }
            if (player.isInWater()) {
                player.setAirSupply(Math.max(-20, player.getAirSupply() - 4));
            }
            return;
        }
        // lightning close by: 60% of the time it sets off a seizure
        if (now % 10 == 0) {
            for (LightningBolt bolt : level.getEntitiesOfClass(LightningBolt.class, player.getBoundingBox().inflate(48.0))) {
                if (bolt.getId() != t.lastBolt) {
                    t.lastBolt = bolt.getId();
                    if (random.nextFloat() < 0.6F && t.auraAt == 0) {
                        aura(player, t, now, 20);
                    }
                }
            }
        }
        if (t.auraAt > 0 && now >= t.auraAt) {
            t.auraAt = 0;
            seize(player, Ailments.between(random, 200, 280));
            return;
        }
        if (!episodes) {
            return;
        }
        if (t.nextSeizure == 0) {
            t.nextSeizure = now + Ailments.between(random, 9600, 16800);
        }
        if (t.auraAt == 0 && now >= t.nextSeizure) {
            t.nextSeizure = now + Ailments.between(random, 9600, 16800);
            aura(player, t, now, AURA_TICKS);
        }
    }

    /** The warning: in {@code ticks} the seizure starts. */
    static void aura(ServerPlayer player, Ailments.Tracker t, long now, int ticks) {
        t.auraAt = now + ticks;
        Ailments.send(player, new Net.Episode(EpisodeKind.AURA, ticks));
        player.sendOverlayMessage(Component.translatable("disabilities.msg.aura").withStyle(ChatFormatting.GOLD));
    }

    /** Hits and explosions can set it off. */
    static void afterDamage(ServerPlayer player, Ailments.Tracker t, DamageSource source, float damage) {
        long now = player.level().getGameTime();
        Status status = ModData.status(player);
        if (status.seizing(now) || status.arrested(now) || t.auraAt > 0) {
            return;
        }
        float chance = source.is(DamageTypeTags.IS_EXPLOSION) ? 0.5F : damage >= 6.0F ? 0.15F : 0.0F;
        if (chance > 0.0F && player.getRandom().nextFloat() < chance) {
            aura(player, t, now, 10);
        }
    }

    public static boolean seize(ServerPlayer player, int ticks) {
        long now = player.level().getGameTime();
        Status status = ModData.status(player);
        if (status.seizing(now) || status.arrested(now)) {
            return false;
        }
        Ailments.setStatus(player, status.withSeizure(now + ticks).withSleep(0));
        Ailments.tracker(player).wasSeizing = true;
        player.setSprinting(false);
        player.stopUsingItem();
        if (!player.getMainHandItem().isEmpty()) {
            player.drop(false);
        }
        Body.refresh(player);
        ServerLevel level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BIG_FALL, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.9F);
        Ailments.bubble(player, Component.translatable("disabilities.bubble.seizure").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), ticks);
        player.sendOverlayMessage(Component.translatable("disabilities.msg.seizure").withStyle(ChatFormatting.GOLD));
        Ailments.tellNearby(player, Component.translatable("disabilities.chat.seizure", player.getDisplayName()).withStyle(ChatFormatting.GOLD), 48.0);
        return true;
    }

    /** A friend puts them in the recovery position: the seizure ends in two seconds. */
    static boolean secure(ServerPlayer patient, ServerPlayer helper) {
        long now = patient.level().getGameTime();
        Status status = ModData.status(patient);
        if (!status.seizing(now) || status.seizureUntil() <= now + 40) {
            return false;
        }
        Ailments.setStatus(patient, status.withSeizure(now + 40));
        helper.sendOverlayMessage(Component.translatable("disabilities.msg.secured", patient.getDisplayName()).withStyle(ChatFormatting.GREEN));
        patient.sendOverlayMessage(Component.translatable("disabilities.msg.secured_by", helper.getDisplayName()).withStyle(ChatFormatting.GREEN));
        Ailments.motion(helper, com.disabilities.net.MotionKind.CPR, 0);
        return true;
    }
}
