package com.disabilities.server;

import com.disabilities.condition.Condition;
import com.disabilities.condition.ModData;
import com.disabilities.net.EpisodeKind;
import com.disabilities.net.Net;
import com.disabilities.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gigantism: one and a half times as tall (doors are too low), longer arms and stronger fists, more
 * health, but hungrier, heavier falls, no boats, minecarts or horses, and every ceiling hits your
 * head. Every step shakes the ground for everyone around. Also the bumps of blind players without
 * their cane, sent by their client.
 */
final class Giant {
    private Giant() {
    }

    static void tick(ServerPlayer player, Ailments.Tracker t, long now) {
        if (player.isPassenger()) {
            player.stopRiding();
            player.sendOverlayMessage(Component.translatable("disabilities.msg.too_big").withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (t.moving && player.onGround() && !player.isShiftKeyDown() && !player.isInWater()) {
            if (++t.giantStep >= (player.isSprinting() ? 8 : 12)) {
                t.giantStep = 0;
                step(player);
            }
        } else {
            t.giantStep = 6;
        }
    }

    private static void step(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
        BlockState state = level.getBlockState(below);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GIANT_STEP, SoundSource.PLAYERS, 1.1F,
                0.85F + player.getRandom().nextFloat() * 0.2F);
        if (!state.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), player.getX(), player.getY() + 0.05, player.getZ(), 14,
                    0.45, 0.05, 0.45, 0.12);
        }
    }

    /** From the client: a wall (blind) or a ceiling (giant). */
    static void bump(ServerPlayer player, int kind) {
        long now = player.level().getGameTime();
        Ailments.Tracker t = Ailments.tracker(player);
        boolean head = kind == 1;
        if (head ? !ModData.has(player, Condition.GIGANTISM) : !ModData.has(player, Condition.BLIND)) {
            return;
        }
        if (now - t.lastBonk < (head ? 20 : 40) || ModData.frozen(player)) {
            return;
        }
        t.lastBonk = now;
        ServerLevel level = player.level();
        Ailments.sound(player, ModSounds.BONK, 1.0F, head ? 0.8F : 1.1F);
        player.hurtServer(level, level.damageSources().source(ModDamage.BONK), head ? 1.0F : 0.5F);
        Ailments.send(player, new Net.Episode(EpisodeKind.BONK, head ? 1 : 0));
        Ailments.bubble(player, Component.translatable("disabilities.bubble.ouch").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), 30);
        level.sendParticles(ParticleTypes.CRIT, player.getX(), head ? player.getY() + player.getBbHeight() : player.getEyeY(), player.getZ(), 6,
                0.3, 0.1, 0.3, 0.1);
        player.sendOverlayMessage(Component.translatable(head ? "disabilities.msg.bonk_head" : "disabilities.msg.bonk_wall")
                .withStyle(ChatFormatting.YELLOW));
    }
}
