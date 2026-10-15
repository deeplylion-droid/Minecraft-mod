package com.disabilities.item;

import com.disabilities.Sided;
import com.disabilities.sound.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The blind player's white cane: right-click taps it hard, and for a few seconds the blind player
 * "sees" around them (the black fog opens up). Everyone nearby hears the taps.
 */
public class WhiteCaneItem extends Item {
    public static final int COOLDOWN = 40;

    public WhiteCaneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CANE_TAP, SoundSource.PLAYERS, 1.0F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.SONAR, SoundSource.PLAYERS, 0.5F, 1.0F);
        player.getCooldowns().addCooldown(stack, COOLDOWN);
        if (level.isClientSide()) {
            Sided.sonar.accept(player);
        }
        return InteractionResult.SUCCESS;
    }
}
