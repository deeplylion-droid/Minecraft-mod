package com.disabilities.item;

import com.disabilities.server.Bones;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Plaster cast ("Gesso"): a broken bone heals in 45 seconds instead of 4 minutes. Right-click a friend to put it on them. */
public class CastItem extends Item {
    public CastItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!Bones.broken(player)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Bones.cast(serverPlayer, serverPlayer);
            stack.consume(1, player);
        }
        player.getCooldowns().addCooldown(stack, 20);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player patient) || !Bones.broken(patient)) {
            return InteractionResult.PASS;
        }
        if (target instanceof ServerPlayer serverPatient && player instanceof ServerPlayer serverPlayer) {
            Bones.cast(serverPatient, serverPlayer);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}
