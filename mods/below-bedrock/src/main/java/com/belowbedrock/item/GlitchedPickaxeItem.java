package com.belowbedrock.item;

import com.belowbedrock.net.ScreenFxPayload;
import com.belowbedrock.registry.ModAdvancements;
import com.belowbedrock.world.Faded;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A diamond pickaxe that came out wrong: crafted upside down, its texture keeps glitching, and it
 * breaks the one block nothing else can break.
 */
public class GlitchedPickaxeItem extends PickaxeItem {
    /** Mining progress per tick on bedrock: about four and a half seconds per block. */
    public static final float BEDROCK_PROGRESS = 1.0F / 90.0F;
    /** Durability lost for each bedrock block (a diamond pickaxe has 1561). */
    public static final int BEDROCK_WEAR = 48;

    public GlitchedPickaxeItem(Properties properties) {
        super(Tiers.DIAMOND, properties);
    }

    /** Whether this player is holding the pickaxe in a dimension where the bedrock can be broken. */
    public static boolean canBreakBedrock(Player player) {
        return player.getMainHandItem().getItem() instanceof GlitchedPickaxeItem && Faded.hasDeepVoid(player.level());
    }

    /** "Piccone di d?amante": one letter of the name is never quite there. */
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack),
                Component.translatable(this.getDescriptionId(stack) + ".glitch").withStyle(ChatFormatting.OBFUSCATED));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(this.getDescriptionId() + ".desc").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!state.is(Blocks.BEDROCK)) {
            return super.mineBlock(stack, level, state, pos, miner);
        }
        if (level instanceof ServerLevel serverLevel) {
            stack.hurtAndBreak(BEDROCK_WEAR, miner, EquipmentSlot.MAINHAND);
            // Something on the other side noticed.
            serverLevel.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.6F, 0.35F);
            serverLevel.playSound(null, pos, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.9F, 0.6F);
            if (miner instanceof ServerPlayer player) {
                ScreenFxPayload.send(player, ScreenFxPayload.Fx.GLITCH, 6, 0.55F);
                ModAdvancements.award(player, ModAdvancements.BREAK_BEDROCK);
            }
        }
        return true;
    }
}
