package com.punizioni.item;

import com.punizioni.server.Gruel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A bowl of gruel: grey-green slop with lumps, a bone sticking out. Eat it yourself, or right
 * click someone poor with it and they eat it there and then (they cannot refuse). Either way it
 * fills a little, tastes awful (a grimace, a burp, sometimes a few seconds of nausea).
 */
public class GruelItem extends Item {
    public GruelItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player poor) || poor == player || !poor.isAlive()) {
            return InteractionResult.PASS;
        }
        if (player.level() instanceof ServerLevel level) {
            Gruel.feed(level, player, poor, stack, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (level instanceof ServerLevel server && user instanceof Player player) {
            Gruel.disgust(server, player);
        }
        return super.finishUsingItem(stack, level, user);
    }
}
