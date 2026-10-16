package com.filorosso.entity;

import com.filorosso.data.Couple;
import com.filorosso.item.ModItems;
import com.filorosso.server.Bonds;
import com.filorosso.server.Links;
import com.filorosso.server.ModDamage;
import com.filorosso.server.Tether;
import com.filorosso.sound.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Lo Spezzacuori's spinning scissors: they cut every thread they fly through. */
public class Scissors extends ThrowableItemProjectile {
    public Scissors(EntityType<? extends Scissors> type, Level level) {
        super(type, level);
    }

    public Scissors(Level level, LivingEntity owner) {
        super(ModEntities.SCISSORS, owner, level, new net.minecraft.world.item.ItemStack(ModItems.SCISSORS));
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.SCISSORS;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.004;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.tickCount > 100) {
            this.discard();
            return;
        }
        Vec3 here = this.position();
        for (Links.Pair pair : Links.active(level.getServer())) {
            Couple couple = pair.couple();
            if (couple == null || pair.a().level() != level || pair.a().distanceToSqr(this) > 24 * 24) {
                continue;
            }
            if (Tether.distanceToThread(pair.a(), pair.b(), here) < 1.3) {
                Tether.cut(level.getServer(), couple, 20 * 20);
                level.sendParticles(ParticleTypes.CRIT, here.x, here.y, here.z, 16, 0.3, 0.3, 0.3, 0.3);
                this.playSound(ModSounds.SCISSORS, 1.2F, 1.0F);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (this.level() instanceof ServerLevel level && hit.getEntity() instanceof LivingEntity target && !(target instanceof Spezzacuori)) {
            target.hurtServer(level, level.damageSources().source(ModDamage.SCISSORS, this, this.getOwner()), 6.0F);
            if (target instanceof ServerPlayer player) {
                Couple couple = Bonds.coupleOf(player);
                if (couple != null) {
                    Tether.cut(level.getServer(), couple, 20 * 20);
                }
            }
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 10, 0.2, 0.2, 0.2, 0.2);
            this.discard();
        }
    }
}
