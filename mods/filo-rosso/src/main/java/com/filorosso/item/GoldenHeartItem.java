package com.filorosso.item;

import com.filorosso.world.Theatre;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * "Cuore d'oro", the trophy of Recisione: used with your partner close by, the red thread pulls the
 * two of you up into the Theatre (and, up there, back home). It is never used up.
 */
public class GoldenHeartItem extends net.minecraft.world.item.Item {
    public GoldenHeartItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        if (!Theatre.useHeart(sp)) {
            return InteractionResult.FAIL;
        }
        player.getCooldowns().addCooldown(player.getItemInHand(hand), 100);
        return InteractionResult.SUCCESS;
    }
}
