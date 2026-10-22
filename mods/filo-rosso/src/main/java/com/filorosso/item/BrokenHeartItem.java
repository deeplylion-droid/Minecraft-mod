package com.filorosso.item;

import com.filorosso.server.Boss;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** "Cuore spezzato": used at night under the open sky, it calls Recisione. */
public class BrokenHeartItem extends Item {
    public BrokenHeartItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (level.dimension() != Level.OVERWORLD || level.isBrightOutside()) {
            player.sendSystemMessage(Component.translatable("filorosso.msg.boss_night").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            player.sendSystemMessage(Component.translatable("filorosso.msg.boss_peaceful").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        if (!Boss.summon(server.getServer(), server, player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(14.0)))) {
            player.sendSystemMessage(Component.translatable("filorosso.msg.boss_already").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
