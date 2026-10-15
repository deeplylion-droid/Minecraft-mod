package com.disabilities.item;

import com.disabilities.server.Ailments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Stops bleeding (hemophilia) and heals a heart. Right-click a friend to bandage them. */
public class BandageItem extends Item {
    public BandageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!Ailments.canBandage(player)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Ailments.bandage(serverPlayer, serverPlayer);
            stack.consume(1, player);
        }
        player.getCooldowns().addCooldown(stack, 20);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player patient) || !Ailments.canBandage(patient)) {
            return InteractionResult.PASS;
        }
        if (target instanceof ServerPlayer serverPatient && player instanceof ServerPlayer serverPlayer) {
            Ailments.bandage(serverPatient, serverPlayer);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}
