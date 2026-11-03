package com.punizioni.entity;

import com.punizioni.ModParticles;
import com.punizioni.item.ModItems;
import com.punizioni.machine.Pillory;
import com.punizioni.server.BodyHit;
import com.punizioni.server.Dung;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A lump of dung in flight, thrown like a snowball. On a player it splats where it hits (a stain on
 * that spot of the skin, a brown splatter on their screen); on the pillory it hits whatever part of
 * the body in the board it really meets, or the board itself, or flies on through the empty space.
 */
public class PoopProjectile extends ThrowableItemProjectile {
    public PoopProjectile(EntityType<? extends PoopProjectile> type, Level level) {
        super(type, level);
    }

    public PoopProjectile(Level level, LivingEntity owner, ItemStack stack) {
        super(ModEntities.POOP, owner, level, stack);
    }

    public PoopProjectile(Level level, double x, double y, double z, ItemStack stack) {
        super(ModEntities.POOP, x, y, z, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.POOP;
    }

    /** The pillory and whoever is locked in it are hit for real in tick, against their shapes. */
    @Override
    protected boolean canHitEntity(Entity entity) {
        if (entity instanceof Pillory || entity.getVehicle() instanceof Pillory) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    public void tick() {
        if (!this.isRemoved() && this.hitsPillory()) {
            return;
        }
        super.tick();
    }

    /**
     * This tick's flight against every pillory near it: the head, the hands, the back, the board.
     * Both sides work it out (the client stops the lump where it lands, the server splats it).
     */
    private boolean hitsPillory() {
        Vec3 from = this.position();
        // the move this tick will make: gravity, then the air
        Vec3 delta = this.getDeltaMovement().add(0.0, -this.getGravity(), 0.0).scale(0.99);
        double length = delta.length();
        if (length < 1.0E-4) {
            return false;
        }
        Vec3 dir = delta.scale(1.0 / length);
        Pillory pillory = null;
        BodyHit.Hit hit = null;
        for (Pillory p : this.level().getEntitiesOfClass(Pillory.class, this.getBoundingBox().expandTowards(delta).inflate(2.5))) {
            BodyHit.Hit h = BodyHit.pillory(p, from, dir, length);
            if (h != null && (hit == null || h.distance() < hit.distance())) {
                hit = h;
                pillory = p;
            }
        }
        if (hit == null) {
            return false;
        }
        Vec3 at = from.add(dir.scale(hit.distance()));
        if (this.level().clip(new ClipContext(from, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) {
            // a wall in the way: that is hit first, as usual
            return false;
        }
        this.setPos(at);
        if (this.level() instanceof ServerLevel level) {
            Dung.hitPillory(level, this, pillory, hit, at);
            level.broadcastEntityEvent(this, (byte) 3);
            this.discard();
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }
        return true;
    }

    @Override
    public void handleEntityEvent(@EntityEvent.Value byte id) {
        if (id == 3) {
            ItemStack item = this.getItem();
            ItemParticleOption bits = new ItemParticleOption(ParticleTypes.ITEM,
                    ItemStackTemplate.fromNonEmptyStack(item.isEmpty() ? new ItemStack(ModItems.POOP) : item));
            for (int i = 0; i < 10; i++) {
                this.level().addParticle(bits, this.getX(), this.getY(), this.getZ(), (this.random.nextDouble() - 0.5) * 0.2, this.random.nextDouble() * 0.15,
                        (this.random.nextDouble() - 0.5) * 0.2);
            }
            for (int i = 0; i < 6; i++) {
                this.level().addParticle(ModParticles.DUNG, this.getX(), this.getY(), this.getZ(), (this.random.nextDouble() - 0.5) * 0.25,
                        0.05 + this.random.nextDouble() * 0.2, (this.random.nextDouble() - 0.5) * 0.25);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        boolean done = true;
        if (hit instanceof EntityHitResult entityHit) {
            done = Dung.hitEntity(level, this, entityHit.getEntity(), entityHit.getLocation());
        } else if (hit instanceof BlockHitResult blockHit) {
            // bells ring, targets light up: what any thrown thing does to a block
            this.onHitBlock(blockHit);
            Dung.hitBlock(level, this, blockHit);
        }
        if (done) {
            level.broadcastEntityEvent(this, (byte) 3);
            this.discard();
        }
    }
}
