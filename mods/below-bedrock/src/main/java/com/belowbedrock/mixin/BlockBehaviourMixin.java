package com.belowbedrock.mixin;

import com.belowbedrock.item.GlitchedPickaxeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bedrock can be mined, slowly, with the glitched pickaxe (same numbers on client and server). */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourMixin {
    @Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
    private void belowbedrock$mineBedrock(BlockState state, Player player, BlockGetter level, BlockPos pos,
                                          CallbackInfoReturnable<Float> cir) {
        if (state.is(Blocks.BEDROCK) && GlitchedPickaxeItem.canBreakBedrock(player)) {
            cir.setReturnValue(GlitchedPickaxeItem.BEDROCK_PROGRESS);
        }
    }
}
