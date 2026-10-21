package com.infezione.item;

import com.infezione.client.AntidoteClientExtensions;
import com.infezione.infection.Infection;
import com.infezione.infection.Infections;
import com.infezione.infection.Stages;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * The antidote: a syringe. Hold use to inject yourself, or use it on another player to inject them at
 * once. It cures the infection up to stage 4; a zombie is too far gone.
 */
public class AntidoteItem extends Item {
    /** How long injecting yourself takes. */
    public static final int USE_TICKS = 32;

    public AntidoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int stage = Infection.stageOf(player);
        if (stage == Stages.HEALTHY || stage > Stages.LAST_CURABLE) {
            if (player instanceof ServerPlayer server) {
                // says why it does nothing
                Infections.inject(server, server);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player && Infections.inject(player, player) && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer doctor && target instanceof ServerPlayer patient
                && Infections.inject(patient, doctor) && !doctor.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.CUSTOM;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.infezione.antidoto.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.infezione.antidoto.tooltip2").withStyle(ChatFormatting.DARK_RED));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new AntidoteClientExtensions());
    }
}
