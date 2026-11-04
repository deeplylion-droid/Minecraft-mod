package com.disabilities.server;

import com.disabilities.condition.ModData;
import com.disabilities.condition.Status;
import com.disabilities.net.EpisodeKind;
import com.disabilities.net.MotionKind;
import com.disabilities.net.Net;
import com.disabilities.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Weak heart: a heart rate that goes up with running, jumping, swimming, climbing, fighting, getting
 * hurt and panicking, and comes down slowly at rest. Over 125 it races (you see it on screen and you
 * hear it), over 165 it is critical (slow, hand on the chest, everyone sees it), at 190 cardiac
 * arrest: you collapse and in 30 seconds you die, unless someone does CPR (hold right-click on you:
 * 30 compressions).
 */
public final class Heart {
    private Heart() {
    }

    public static final float REST = 72.0F;
    public static final float RACING = 125.0F;
    public static final float CRITICAL = 165.0F;
    public static final float ARREST = 190.0F;
    static final int ARREST_TICKS = 600;
    static final int COMPRESSIONS = 30;
    private static final DustParticleOptions RED = new DustParticleOptions(0xD81E5B, 1.0F);

    static void tick(ServerPlayer player, Ailments.Tracker t, long now, Status status) {
        ServerLevel level = player.level();
        if (status.arrested(now)) {
            t.wasArrested = true;
            if (now % 40 == 0) {
                Ailments.sound(player, ModSounds.FLATLINE, 0.75F, 1.0F);
            }
            if (now % 8 == 0) {
                level.sendParticles(RED, player.getX(), player.getY() + 0.4, player.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
            }
            if (t.cpr > 0 && now - t.lastCpr > 60) {
                t.cpr = 0;
            }
            if (now >= status.arrestUntil() - 1) {
                // nobody came in time
                Ailments.setStatus(player, status.withArrest(0));
                t.wasArrested = false;
                t.bpm = REST;
                player.hurtServer(level, level.damageSources().source(ModDamage.HEART_ATTACK), 1000.0F);
            }
            return;
        }
        if (t.wasArrested) {
            t.wasArrested = false;
            Body.refresh(player);
        }
        float target = REST;
        if (player.isSprinting()) {
            target += 72.0F;
        } else if (t.moving) {
            target += 16.0F;
        }
        if (player.isSwimming() || player.isInWater() && t.moving) {
            target += 40.0F;
        }
        if (player.onClimbable() && t.dy > 0.05) {
            target += 50.0F;
        }
        if (status.panicking(now)) {
            target += 70.0F;
        } else if (status.anxious()) {
            target += 22.0F;
        }
        t.bpm += (target - t.bpm) * (target > t.bpm ? 0.012F : 0.006F);
        int stage = t.bpm >= CRITICAL ? 2 : t.bpm >= RACING ? 1 : 0;
        if (stage != status.heartStage()) {
            if (stage == 2 && status.heartStage() < 2) {
                player.sendOverlayMessage(Component.translatable("disabilities.msg.heart_critical").withStyle(ChatFormatting.RED));
            }
            Ailments.setStatus(player, status.withHeartStage(stage));
            Body.refresh(player);
        }
        if (stage == 2 && now % 24 == 0) {
            Ailments.sound(player, ModSounds.PANT, 0.8F, 1.1F);
        }
        int bpm = Math.round(t.bpm);
        if (now % 5 == 0 && Math.abs(bpm - t.sentBpm) >= 1) {
            t.sentBpm = bpm;
            Ailments.send(player, new Net.Heart(bpm));
        }
        if (t.bpm >= ARREST) {
            arrest(player);
        }
    }

    /** Jumping, hitting, getting hurt: a jolt. */
    static void jolt(ServerPlayer player, float amount) {
        Ailments.Tracker t = Ailments.tracker(player);
        t.bpm = Math.min(t.bpm + amount, ARREST + 5.0F);
    }

    public static void arrest(ServerPlayer player) {
        long now = player.level().getGameTime();
        Status status = ModData.status(player);
        if (status.arrested(now)) {
            return;
        }
        Ailments.Tracker t = Ailments.tracker(player);
        t.cpr = 0;
        t.bpm = 0.0F;
        Ailments.send(player, new Net.Heart(0));
        Ailments.setStatus(player, status.withArrest(now + ARREST_TICKS).withSleep(0).withSeizure(0).withHeartStage(0));
        t.wasArrested = true;
        player.setSprinting(false);
        player.stopUsingItem();
        Body.refresh(player);
        ServerLevel level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 1.0F, 0.8F);
        Ailments.sound(player, ModSounds.FLATLINE, 0.75F, 1.0F);
        Ailments.bubble(player, Component.translatable("disabilities.bubble.arrest").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), ARREST_TICKS);
        player.sendOverlayMessage(Component.translatable("disabilities.msg.arrest").withStyle(ChatFormatting.RED));
        Ailments.tellNearby(player, Component.translatable("disabilities.chat.arrest", player.getDisplayName()).withStyle(ChatFormatting.RED), 64.0);
    }

    /** One chest compression from a friend; at 30 the heart starts again. */
    static boolean compress(ServerPlayer patient, ServerPlayer rescuer) {
        long now = patient.level().getGameTime();
        Status status = ModData.status(patient);
        if (!status.arrested(now)) {
            return false;
        }
        Ailments.Tracker t = Ailments.tracker(patient);
        if (now - t.lastCpr < 3) {
            return true;
        }
        t.lastCpr = now;
        t.cpr++;
        Ailments.motion(rescuer, MotionKind.CPR, 0);
        patient.level().playSound(null, patient.getX(), patient.getY() + 0.3, patient.getZ(), ModSounds.CPR_PUSH, SoundSource.PLAYERS, 1.0F,
                0.9F + patient.getRandom().nextFloat() * 0.2F);
        rescuer.sendOverlayMessage(Component.translatable("disabilities.msg.cpr", t.cpr, COMPRESSIONS).withStyle(ChatFormatting.RED));
        if (t.cpr % 5 == 0) {
            patient.level().playSound(null, patient.getX(), patient.getY() + 0.5, patient.getZ(), ModSounds.ECG_BEEP, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        if (t.cpr >= COMPRESSIONS) {
            revive(patient, rescuer);
        }
        return true;
    }

    private static void revive(ServerPlayer patient, ServerPlayer rescuer) {
        long now = patient.level().getGameTime();
        Ailments.Tracker t = Ailments.tracker(patient);
        t.cpr = 0;
        t.bpm = 110.0F;
        t.wasArrested = false;
        Ailments.setStatus(patient, ModData.status(patient).withArrest(0).withConfused(now + 200));
        patient.setHealth(Math.max(patient.getHealth(), 4.0F));
        Body.refresh(patient);
        Ailments.sound(patient, ModSounds.GASP, 1.0F, 1.0F);
        patient.level().playSound(null, patient.getX(), patient.getY() + 0.5, patient.getZ(), ModSounds.ECG_BEEP, SoundSource.PLAYERS, 0.8F, 1.0F);
        Ailments.send(patient, new Net.Episode(EpisodeKind.REVIVED, 0));
        Ailments.bubble(patient, Component.translatable("disabilities.bubble.thanks").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), 60);
        patient.sendOverlayMessage(Component.translatable("disabilities.msg.revived_by", rescuer.getDisplayName()).withStyle(ChatFormatting.GREEN));
        rescuer.sendOverlayMessage(Component.translatable("disabilities.msg.revived", patient.getDisplayName()).withStyle(ChatFormatting.GREEN));
        Ailments.tellNearby(patient, Component.translatable("disabilities.chat.revived", rescuer.getDisplayName(), patient.getDisplayName())
                .withStyle(ChatFormatting.GREEN), 64.0);
    }

    public static float bpm(ServerPlayer player) {
        return Ailments.tracker(player).bpm;
    }
}
