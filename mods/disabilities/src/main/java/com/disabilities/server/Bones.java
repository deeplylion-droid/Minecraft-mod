package com.disabilities.server;

import com.disabilities.condition.Condition;
import com.disabilities.condition.ModData;
import com.disabilities.condition.Status;
import com.disabilities.net.EpisodeKind;
import com.disabilities.net.Net;
import com.disabilities.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * Brittle bones: falls from 5 blocks break a leg, hard hits and explosions break an arm or a leg.
 * A broken leg limps (slow, no sprint, almost no jump), a broken arm (the main one, in a sling) digs
 * and hits weakly and cannot draw a bow. Both heal in 4 minutes, or in 45 seconds once a cast
 * ("Gesso") is put on. Everybody sees the white casts and hears the crack.
 */
public final class Bones {
    private Bones() {
    }

    static final int HEAL_TICKS = 4800;
    static final int CAST_TICKS = 900;

    static void tick(ServerPlayer player, Ailments.Tracker t, long now, Status status) {
        if (t.brokenLegShown != 0 && !status.brokenLeg(now)) {
            t.brokenLegShown = 0;
            player.sendOverlayMessage(Component.translatable("disabilities.msg.healed_leg").withStyle(ChatFormatting.GREEN));
            Body.refresh(player);
        }
        if (t.brokenArmShown != 0 && !status.brokenArm(now)) {
            t.brokenArmShown = 0;
            player.sendOverlayMessage(Component.translatable("disabilities.msg.healed_arm").withStyle(ChatFormatting.GREEN));
            Body.refresh(player);
        }
        if (status.brokenLeg(now)) {
            player.setSprinting(false);
        }
    }

    static void afterDamage(ServerPlayer player, DamageSource source, float damage) {
        RandomSource random = player.getRandom();
        boolean wheelchair = ModData.has(player, Condition.WHEELCHAIR);
        if (source.is(DamageTypeTags.IS_FALL)) {
            if (damage >= 2.0F) {
                breakBone(player, !wheelchair);
                if (damage >= 6.0F && random.nextFloat() < 0.5F) {
                    breakBone(player, false);
                }
            }
        } else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            if (damage >= 3.0F && random.nextFloat() < 0.7F) {
                breakBone(player, !wheelchair && random.nextBoolean());
            }
        } else if (damage >= 4.0F && source.getEntity() != null && random.nextFloat() < 0.3F) {
            breakBone(player, !wheelchair && random.nextFloat() < 0.4F);
        }
    }

    public static void breakBone(ServerPlayer player, boolean leg) {
        long now = player.level().getGameTime();
        Status status = ModData.status(player);
        Ailments.setStatus(player, leg ? status.withBrokenLeg(now + HEAL_TICKS) : status.withBrokenArm(now + HEAL_TICKS));
        Ailments.Tracker t = Ailments.tracker(player);
        if (leg) {
            t.brokenLegShown = now;
        } else {
            t.brokenArmShown = now;
        }
        Body.refresh(player);
        Ailments.sound(player, ModSounds.BONE_CRACK, 1.4F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        Ailments.send(player, new Net.Episode(EpisodeKind.FRACTURE, leg ? 1 : 0));
        Ailments.bubble(player, Component.translatable("disabilities.bubble.crack").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), 40);
        player.sendOverlayMessage(Component.translatable(leg ? "disabilities.msg.broke_leg" : "disabilities.msg.broke_arm").withStyle(ChatFormatting.RED));
        Ailments.tellNearby(player, Component.translatable(leg ? "disabilities.chat.broke_leg" : "disabilities.chat.broke_arm", player.getDisplayName())
                .withStyle(ChatFormatting.GRAY), 32.0);
    }

    public static boolean broken(Player player) {
        long now = player.level().getGameTime();
        Status status = ModData.status(player);
        return status.brokenArm(now) || status.brokenLeg(now);
    }

    /** The cast: what is broken heals in 45 seconds. */
    public static void cast(ServerPlayer patient, ServerPlayer nurse) {
        long now = patient.level().getGameTime();
        Status status = ModData.status(patient);
        if (status.brokenArm(now)) {
            status = status.withBrokenArm(Math.min(status.brokenArmUntil(), now + CAST_TICKS));
        }
        if (status.brokenLeg(now)) {
            status = status.withBrokenLeg(Math.min(status.brokenLegUntil(), now + CAST_TICKS));
        }
        Ailments.setStatus(patient, status);
        patient.level().playSound(null, patient.getX(), patient.getY() + 1, patient.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
        patient.sendOverlayMessage(Component.translatable("disabilities.msg.cast").withStyle(ChatFormatting.GREEN));
        if (nurse != patient) {
            nurse.sendOverlayMessage(Component.translatable("disabilities.msg.cast_other", patient.getDisplayName()).withStyle(ChatFormatting.GREEN));
        }
    }
}
