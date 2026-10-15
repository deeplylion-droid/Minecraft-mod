package com.disabilities.item;

import com.disabilities.server.Ailments;
import com.disabilities.sound.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Asthma inhaler: gives you all your breath back. Sixteen puffs, then craft another. */
public class InhalerItem extends Item {
    public static final int COOLDOWN = 200;

    public InhalerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            Ailments.catchBreath(serverPlayer);
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.INHALER, SoundSource.PLAYERS, 1.0F, 1.0F);
            Vec3 mouth = player.getEyePosition().add(player.getLookAngle().scale(0.4));
            serverLevel.sendParticles(ParticleTypes.CLOUD, mouth.x, mouth.y - 0.1, mouth.z, 6, 0.06, 0.06, 0.06, 0.01);
            stack.hurtAndBreak(1, player, hand);
        }
        player.getCooldowns().addCooldown(stack, COOLDOWN);
        return InteractionResult.SUCCESS;
    }
}
