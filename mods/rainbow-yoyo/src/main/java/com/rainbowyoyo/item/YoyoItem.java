package com.rainbowyoyo.item;

import com.rainbowyoyo.client.ClientHooks;
import com.rainbowyoyo.client.YoyoClientExtensions;
import com.rainbowyoyo.config.YoyoConfig;
import com.rainbowyoyo.entity.YoyoEntity;
import com.rainbowyoyo.util.Rainbow;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * The giant rainbow yo-yo.
 *
 * <ul>
 *   <li>Use (hold): throw it; it keeps spinning at the end of the string while the key is held.
 *       Vanilla repeats the use packet every 4 ticks while the key is down, which tells the server
 *       the key is still held; the player is never slowed down like with a bow.</li>
 *   <li>Attack: a quick shot, or a trick while the yo-yo is out (see {@link YoyoEntity}).</li>
 * </ul>
 */
public class YoyoItem extends Item {

    public YoyoItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            YoyoEntity.onUse(serverPlayer);
        }
        // Consume: no arm swing, and the off hand is not used as well.
        return InteractionResultHolder.consume(stack);
    }

    /** The attack key throws the yo-yo instead: holding it never breaks blocks. */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    /** The name glows through the rainbow, letter by letter. */
    @Override
    public Component getName(ItemStack stack) {
        double offset = (Util.getMillis() % 3000L) / 3000.0;
        if (FMLEnvironment.dist != Dist.CLIENT) {
            // A dedicated server cannot translate mod names: keep it translatable, one colour.
            return Component.translatable(getDescriptionId(stack))
                    .withStyle(style -> style.withColor(TextColor.fromRgb(Rainbow.rgb(offset))));
        }
        String text = Component.translatable(getDescriptionId(stack)).getString();
        MutableComponent name = Component.empty();
        int letter = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int color = Rainbow.rgb(offset - letter * 0.07);
            name.append(Component.literal(new String(Character.toChars(codePoint)))
                    .withStyle(style -> style.withColor(TextColor.fromRgb(color))));
            i += Character.charCount(codePoint);
            letter++;
        }
        return name;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        double damage = YoyoConfig.SERVER_SPEC.isLoaded() ? YoyoConfig.SERVER.damage.get() : 80.0;
        String hearts = damage % 2 == 0 ? String.valueOf((int) (damage / 2)) : String.valueOf(damage / 2);
        tooltip.add(Component.translatable("item.rainbowyoyo.rainbow_yoyo.damage", hearts)
                .withStyle(ChatFormatting.RED));
        if (!ClientHooks.hasShiftDown()) {
            tooltip.add(Component.translatable("item.rainbowyoyo.rainbow_yoyo.more").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (String line : new String[]{"use", "attack", "tricks", "trick_forward", "trick_up", "trick_down",
                "trick_sneak", "supernova"}) {
            tooltip.add(Component.translatable("item.rainbowyoyo.rainbow_yoyo." + line).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new YoyoClientExtensions());
    }
}
