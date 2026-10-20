package com.frusta.item;

import com.frusta.client.ClientHooks;
import com.frusta.client.WhipClientExtensions;
import com.frusta.config.FrustaConfig;
import com.frusta.enchantment.SottomissioneEnchantment;
import com.frusta.motion.Whip;
import com.frusta.registry.ModEnchantments;
import com.frusta.server.WhipMoves;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.DistExecutor;

/**
 * A whip (black leather, diamond or netherite). It never wears out.
 *
 * <ul>
 *   <li>Attack: a lash. The click is sent by the client ({@code ClientEvents}); vanilla attacks and
 *       mining never happen with a whip in hand.</li>
 *   <li>Use (hold): the whip spins over the head; letting go fires the Imperial Crack.</li>
 *   <li>Sneak + use: the grab.</li>
 * </ul>
 * The server decides what is hit ({@link WhipMoves}); every client draws the moves.
 */
public class WhipItem extends Item {
    private static final int USE_DURATION = 72000;
    private final WhipMaterial material;

    public WhipItem(WhipMaterial material, Properties properties) {
        super(properties);
        this.material = material;
    }

    public WhipMaterial getMaterial() {
        return material;
    }

    /** The whip in the entity's main hand, or an empty stack. */
    public static ItemStack held(LivingEntity entity) {
        ItemStack stack = entity.getMainHandItem();
        return stack.getItem() instanceof WhipItem ? stack : ItemStack.EMPTY;
    }

    public static int sottomissione(ItemStack stack) {
        return stack.getEnchantmentLevel(ModEnchantments.SOTTOMISSIONE.get());
    }

    /** Damage of one lash with this whip (server config and Sottomissione). */
    public static float damage(ItemStack stack) {
        WhipMaterial material = ((WhipItem) stack.getItem()).getMaterial();
        boolean loaded = FrustaConfig.SERVER_SPEC.isLoaded();
        float base = loaded ? material.damage() : material.defaultDamage();
        return base * SottomissioneEnchantment.damageMultiplier(sottomissione(stack));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown()) {
            if (FrustaConfig.SERVER_SPEC.isLoaded() && !FrustaConfig.SERVER.grab.get()) {
                return InteractionResultHolder.pass(stack);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                WhipMoves.grab(serverPlayer);
            } else if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::predictGrab);
            }
            return InteractionResultHolder.consume(stack);
        }
        // Spin it over the head until the key is let go (see releaseUsing).
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int held = getUseDuration(stack) - timeLeft;
        if (entity instanceof ServerPlayer serverPlayer) {
            WhipMoves.release(serverPlayer, held);
        } else if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.predictRelease(held));
        }
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    /** The attack key lashes instead: holding it never breaks blocks. */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    /** No vanilla melee hit with a whip: the lash does the hitting. */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        return true;
    }

    /** No arm swing: the whip has its own moves. */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        return true;
    }

    /** The whip never wears out, but it can still be enchanted at the enchanting table. */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return material.enchantmentValue();
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        float damage = damage(stack);
        tooltip.add(Component.translatable("item.frusta.whip.damage", format(damage), format(damage / 2))
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.frusta.whip.reach", format(material.length()))
                .withStyle(ChatFormatting.GOLD));
        int kneel = Whip.kneelTicks(sottomissione(stack));
        if (kneel > 0) {
            tooltip.add(Component.translatable("item.frusta.whip.kneel", format(kneel / 20.0))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        // Tooltips only exist on the client.
        if (!ClientHooks.hasShiftDown()) {
            tooltip.add(Component.translatable("item.frusta.whip.more").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (String line : new String[]{"lash", "imperial", "grab"}) {
            tooltip.add(Component.translatable("item.frusta.whip." + line).withStyle(ChatFormatting.GRAY));
        }
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new WhipClientExtensions());
    }
}
