package com.punizioni.item;

import com.punizioni.ModSounds;
import com.punizioni.entity.CursePotion;
import com.punizioni.item.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;

/**
 * A bottle of the witch's curse: thrown like a splash potion (from a dispenser too); it shatters
 * on whatever it hits and curses the players around (see CursePotion). A witch in her hat cackles
 * as she throws it.
 */
public class CursePotionItem extends Item implements ProjectileItem {
    public CursePotionItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.6F,
                1.0F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (level instanceof ServerLevel serverLevel) {
            Projectile.spawnProjectileFromRotation(CursePotion::new, serverLevel, stack, player, -20.0F, 0.6F, 1.0F);
            if (player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.WITCH_HAT) && level.getRandom().nextInt(3) != 0) {
                level.playSound(null, player.getX(), player.getY() + 1.6, player.getZ(), ModSounds.WITCH_CACKLE, SoundSource.PLAYERS, 1.2F,
                        0.95F + level.getRandom().nextFloat() * 0.15F);
            }
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        player.getCooldowns().addCooldown(stack, 20);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
        return new CursePotion(level, position.x(), position.y(), position.z(), stack);
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder().uncertainty(ProjectileItem.DispenseConfig.DEFAULT.uncertainty() * 0.5F)
                .power(ProjectileItem.DispenseConfig.DEFAULT.power() * 1.25F).build();
    }
}
