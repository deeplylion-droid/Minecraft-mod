package com.jumpscares.item;

import com.jumpscares.entity.ScarePearl;
import com.jumpscares.scare.ScareKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Thrown like an ender pearl; whoever it hits gets this pearl's monster in the face. */
public class ScarePearlItem extends Item implements ProjectileItem {
    private final ScareKind kind;

    public ScarePearlItem(ScareKind kind, Item.Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public ScareKind kind() {
        return this.kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.NEUTRAL,
                0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        player.getCooldowns().addCooldown(this, 20);
        if (!level.isClientSide) {
            ScarePearl pearl = new ScarePearl(level, player);
            pearl.setItem(stack);
            pearl.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(pearl);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
        ScarePearl pearl = new ScarePearl(level, position.x(), position.y(), position.z());
        pearl.setItem(stack);
        return pearl;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(this.kind.displayName().withColor(this.kind.color));
        tooltip.add(Component.translatable("item.jumpscares.scare_pearl.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
