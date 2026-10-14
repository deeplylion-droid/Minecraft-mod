package com.jumpscares.entity;

import com.jumpscares.item.ScarePearlItem;
import com.jumpscares.registry.ModEntities;
import com.jumpscares.registry.ModItems;
import com.jumpscares.scare.ScareKind;
import com.jumpscares.scare.Scares;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A thrown scare pearl: a coloured trail, and a jumpscare for whoever is hit (or right next to the splash). */
public class ScarePearl extends ThrowableItemProjectile {
    public ScarePearl(EntityType<? extends ScarePearl> type, Level level) {
        super(type, level);
    }

    public ScarePearl(Level level, LivingEntity owner) {
        super(ModEntities.SCARE_PEARL, owner, level);
    }

    public ScarePearl(Level level, double x, double y, double z) {
        super(ModEntities.SCARE_PEARL, x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.pearl(ScareKind.CLOWN);
    }

    public ScareKind kind() {
        return this.getItem().getItem() instanceof ScarePearlItem pearl ? pearl.kind() : ScareKind.CLOWN;
    }

    private DustParticleOptions dust(float size) {
        return new DustParticleOptions(Vec3.fromRGB24(this.kind().color).toVector3f(), size);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide && !this.isRemoved()) {
            Vec3 v = this.getDeltaMovement();
            for (int i = 0; i < 2; i++) {
                double f = i / 2.0;
                this.level().addParticle(this.dust(1.2F), this.getX() - v.x * f, this.getY() - v.y * f, this.getZ() - v.z * f,
                        0.0, 0.0, 0.0);
            }
            if (this.random.nextInt(3) == 0) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            ItemParticleOption item = new ItemParticleOption(ParticleTypes.ITEM, this.getItem());
            for (int i = 0; i < 12; i++) {
                this.level().addParticle(item, this.getX(), this.getY(), this.getZ(),
                        (this.random.nextDouble() - 0.5) * 0.2, this.random.nextDouble() * 0.2, (this.random.nextDouble() - 0.5) * 0.2);
            }
            for (int i = 0; i < 10; i++) {
                this.level().addParticle(this.dust(1.0F), this.getX() + (this.random.nextDouble() - 0.5) * 1.2,
                        this.getY() + this.random.nextDouble() * 1.0, this.getZ() + (this.random.nextDouble() - 0.5) * 1.2, 0.0, 0.0, 0.0);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            Scares.pearlHit(this, result);
            this.level().broadcastEntityEvent(this, (byte) 3);
            this.discard();
        }
    }
}
