package com.belowbedrock.world;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.BedBlock;

/** The rules of the faded world that are not about worldgen. */
public final class FadedWorld {
    private FadedWorld() {
    }

    public static void register() {
        // You cannot sleep here. The bed says there are monsters nearby. There are none. Only Him.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (Faded.is(level) && !player.isSpectator() && level.getBlockState(hit.getBlockPos()).getBlock() instanceof BedBlock) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("block.minecraft.bed.not_safe"), true);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        // Item frames generated with a placeholder get their real map once the chunk is loaded.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemFrame frame) {
                String picture = FadedMaps.placeholderPicture(frame.getItem());
                if (picture != null) {
                    frame.setSilent(true);
                    frame.setItem(FadedMaps.create(level, picture), false);
                    frame.setSilent(false);
                }
            }
        });
    }
}
