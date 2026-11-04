package com.disabilities.server;

import com.disabilities.condition.ModData;
import com.disabilities.net.EpisodeKind;
import com.disabilities.net.MotionKind;
import com.disabilities.net.Net;
import com.disabilities.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Alien hand: every 30-90 seconds the off hand does something on its own. It slaps your face, throws
 * away what you hold, punches whoever is in front of you, knocks things out of a friend's hands,
 * turns your head, or waves at nobody. Everybody sees the arm do it.
 */
final class AlienHand {
    private AlienHand() {
    }

    static final String[] ACTIONS = {"schiaffo", "lancio", "pugno", "presa", "testa", "saluto"};

    static void tick(ServerPlayer player, Ailments.Tracker t, long now, boolean episodes) {
        if (!episodes || ModData.frozen(player)) {
            return;
        }
        RandomSource random = player.getRandom();
        if (t.nextAlien == 0) {
            t.nextAlien = now + Ailments.between(random, 600, 1800);
        }
        if (now >= t.nextAlien) {
            t.nextAlien = now + Ailments.between(random, 600, 1800);
            act(player, -1);
        }
    }

    /** {@code kind} -1 for a random one, or an index of {@link #ACTIONS}. */
    static void act(ServerPlayer player, int kind) {
        RandomSource random = player.getRandom();
        if (kind < 0) {
            int r = random.nextInt(100);
            kind = r < 28 ? 0 : r < 42 ? 1 : r < 64 ? 2 : r < 76 ? 3 : r < 88 ? 4 : 5;
        }
        switch (kind) {
            case 1 -> {
                if (!throwHeld(player)) {
                    punch(player);
                }
            }
            case 2 -> punch(player);
            case 3 -> {
                if (!grab(player)) {
                    slap(player);
                }
            }
            case 4 -> {
                Ailments.motion(player, MotionKind.ALIEN_SLAP, 1);
                Ailments.send(player, new Net.Episode(EpisodeKind.PULLED, random.nextBoolean() ? 1 : -1));
                player.sendOverlayMessage(Component.translatable("disabilities.msg.alien_pull").withStyle(ChatFormatting.GREEN));
            }
            case 5 -> {
                Ailments.motion(player, MotionKind.ALIEN_WAVE, 0);
                Ailments.bubble(player, Component.translatable("disabilities.bubble.alien_wave").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC), 40);
            }
            default -> slap(player);
        }
    }

    private static void slap(ServerPlayer player) {
        ServerLevel level = player.level();
        Ailments.motion(player, MotionKind.ALIEN_SLAP, 0);
        Ailments.sound(player, ModSounds.SLAP, 1.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        player.hurtServer(level, level.damageSources().source(ModDamage.ALIEN_HAND), 1.0F);
        Ailments.send(player, new Net.Episode(EpisodeKind.SLAPPED, player.getRandom().nextBoolean() ? 1 : -1));
        Ailments.bubble(player, Component.translatable("disabilities.bubble.ouch").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), 30);
        player.sendOverlayMessage(Component.translatable("disabilities.msg.alien_slap").withStyle(ChatFormatting.GREEN));
    }

    private static boolean throwHeld(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return false;
        }
        ItemStack stack = held.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ItemEntity item = player.drop(stack, true, net.minecraft.util.Prediction.SERVER_ONLY);
        if (item != null) {
            Vec3 look = player.getLookAngle();
            item.setDeltaMovement(look.scale(0.85).add(0.0, 0.3, 0.0));
            item.setPickUpDelay(30);
        }
        Ailments.motion(player, MotionKind.ALIEN_THROW, 0);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.4F);
        player.sendOverlayMessage(Component.translatable("disabilities.msg.alien_throw", stack.getHoverName()).withStyle(ChatFormatting.GREEN));
        return true;
    }

    private static Entity inFront(ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(reach));
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : player.level().getEntities(player, new AABB(eye, end).inflate(0.8), e -> e instanceof LivingEntity && e.isAlive())) {
            if (e.getBoundingBox().inflate(0.4).clip(eye, end).isPresent()) {
                double d = e.distanceToSqr(player);
                if (d < bestDist) {
                    bestDist = d;
                    best = e;
                }
            }
        }
        return best;
    }

    private static void punch(ServerPlayer player) {
        ServerLevel level = player.level();
        Ailments.motion(player, MotionKind.ALIEN_PUNCH, 0);
        Entity target = inFront(player, 3.0);
        if (target instanceof LivingEntity living) {
            net.minecraft.world.damagesource.DamageSource hit = level.damageSources().playerAttack(player);
            if (living.hurtServer(level, hit, 2.0F)) {
                Vec3 look = player.getLookAngle();
                living.knockback(0.5, -look.x, -look.z, hit, 2.0F);
            }
            level.playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
            if (target instanceof ServerPlayer victim) {
                victim.sendOverlayMessage(Component.translatable("disabilities.msg.alien_hit_you", player.getDisplayName()).withStyle(ChatFormatting.GREEN));
            }
            Ailments.bubble(player, Component.translatable("disabilities.bubble.sorry").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), 40);
            player.sendOverlayMessage(Component.translatable("disabilities.msg.alien_punch").withStyle(ChatFormatting.GREEN));
        } else {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
            player.sendOverlayMessage(Component.translatable("disabilities.msg.alien_air").withStyle(ChatFormatting.GREEN));
        }
    }

    /** Knocks the held item out of the hand of the nearest player within 3 blocks. */
    private static boolean grab(ServerPlayer player) {
        ServerPlayer victim = null;
        double best = 9.0;
        for (Player p : player.level().players()) {
            if (p != player && p instanceof ServerPlayer sp && !p.isSpectator() && !p.getMainHandItem().isEmpty()) {
                double d = p.distanceToSqr(player);
                if (d < best) {
                    best = d;
                    victim = sp;
                }
            }
        }
        if (victim == null) {
            return false;
        }
        ItemStack stack = victim.getMainHandItem().copy();
        victim.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ItemEntity item = victim.drop(stack, true, net.minecraft.util.Prediction.SERVER_ONLY);
        if (item != null) {
            item.setPickUpDelay(20);
        }
        Ailments.motion(player, MotionKind.ALIEN_GRAB, victim.getId());
        Ailments.sound(player, ModSounds.SLAP, 0.7F, 1.3F);
        Ailments.bubble(player, Component.translatable("disabilities.bubble.sorry").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), 40);
        victim.sendOverlayMessage(Component.translatable("disabilities.msg.alien_grabbed_you", player.getDisplayName(), stack.getHoverName())
                .withStyle(ChatFormatting.GREEN));
        player.sendOverlayMessage(Component.translatable("disabilities.msg.alien_grab", victim.getDisplayName()).withStyle(ChatFormatting.GREEN));
        return true;
    }
}
