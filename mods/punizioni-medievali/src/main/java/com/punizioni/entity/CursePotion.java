package com.punizioni.entity;

import com.punizioni.item.ModItems;
import com.punizioni.server.Curses;
import com.punizioni.witch.CurseKind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * A bottle of the witch's curse in flight. It shatters on whatever it hits, in a cloud of its
 * colour, and curses the players around it (Curses.splash).
 */
public class CursePotion extends ThrowableItemProjectile {
    public CursePotion(EntityType<? extends CursePotion> type, Level level) {
        super(type, level);
    }

    public CursePotion(Level level, LivingEntity owner, ItemStack stack) {
        super(ModEntities.CURSE_POTION, owner, level, stack);
    }

    public CursePotion(Level level, double x, double y, double z, ItemStack stack) {
        super(ModEntities.CURSE_POTION, x, y, z, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.TOAD_POTION;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            CurseKind kind = CurseKind.ofPotion(this.getItem().getItem());
            if (kind == null) {
                kind = CurseKind.TOAD;
            }
            @Nullable Entity direct = hit instanceof EntityHitResult e ? e.getEntity() : null;
            Curses.splash(level, this, kind, hit.getLocation(), direct, this.getOwner());
            level.levelEvent(2002, this.blockPosition(), kind.color());
            level.levelEvent(1053, this.blockPosition(), 0);
            this.discard();
        }
    }
}
