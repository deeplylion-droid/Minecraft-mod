package com.filorosso.entity;

import com.filorosso.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A red thread that falls from the dark like a spear (the Burattinaio's RAIN): first a red circle
 * on the boards for {@link #WARN} ticks, then it drops in a few ticks and hurts whoever is still
 * standing there.
 */
public class ThreadSpear extends Entity {
    public static final int WARN = 18;
    public static final double FALL = 26.0;
    public static final int FALL_TICKS = 5;
    public static final double RADIUS = 1.2;
    public static final float DAMAGE = 5.0F;

    private static final EntityDataAccessor<Float> DATA_GROUND = SynchedEntityData.defineId(ThreadSpear.class, EntityDataSerializers.FLOAT);
    private Entity owner;

    public ThreadSpear(EntityType<? extends ThreadSpear> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** A spear over this point (it finds the boards under it). */
    public static void drop(ServerLevel level, Entity owner, Vec3 at) {
        ThreadSpear spear = ModEntities.THREAD_SPEAR.create(level, EntitySpawnReason.EVENT);
        if (spear == null) {
            return;
        }
        BlockPos column = BlockPos.containing(at.x, owner.getY() + 4.0, at.z);
        int y = column.getY();
        for (int i = 0; i < 12 && level.getBlockState(column.below()).isAir(); i++) {
            column = column.below();
            y--;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
        double ground = Math.min(y, top);
        spear.owner = owner;
        spear.entityData.set(DATA_GROUND, (float) ground);
        spear.setPos(at.x, ground, at.z);
        level.addFreshEntity(spear);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_GROUND, 0.0F);
    }

    public double ground() {
        return this.entityData.get(DATA_GROUND);
    }

    /** How far up the spear's tip is (blocks over the ground): FALL while waiting, 0 when it lands. */
    public double height(float partial) {
        float t = this.tickCount + partial - WARN;
        if (t <= 0.0F) {
            return FALL;
        }
        double k = Math.min(1.0, t / FALL_TICKS);
        return FALL * (1.0 - k * k);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (this.tickCount < WARN && this.tickCount % 2 == 0) {
                double a = this.tickCount * 0.7;
                double r = RADIUS * (1.0 - this.tickCount / (double) WARN * 0.4);
                this.level().addParticle(new DustParticleOptions(0xE01818, 1.0F), this.getX() + Math.cos(a) * r, this.ground() + 0.05,
                        this.getZ() + Math.sin(a) * r, 0.0, 0.0, 0.0);
            }
            return;
        }
        if (this.tickCount == 1) {
            this.level().playSound(null, this.getX(), this.ground() + 8.0, this.getZ(), ModSounds.SPEAR_FALL, SoundSource.HOSTILE, 1.4F,
                    0.9F + this.random.nextFloat() * 0.2F);
        }
        if (this.tickCount == WARN + FALL_TICKS && this.level() instanceof ServerLevel level) {
            this.land(level);
        }
        if (this.tickCount > WARN + FALL_TICKS + 30) {
            this.discard();
        }
    }

    private void land(ServerLevel level) {
        double g = this.ground();
        level.playSound(null, this.getX(), g, this.getZ(), ModSounds.SPEAR_HIT, SoundSource.HOSTILE, 1.6F, 0.85F + this.random.nextFloat() * 0.3F);
        BlockPos below = BlockPos.containing(this.getX(), g - 0.5, this.getZ());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), this.getX(), g + 0.1, this.getZ(), 16, 0.4, 0.1, 0.4,
                0.12);
        level.sendParticles(new DustParticleOptions(0xD8202A, 1.5F), this.getX(), g + 0.5, this.getZ(), 12, 0.3, 0.6, 0.3, 0.0);
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) {
                continue;
            }
            double h = Math.sqrt(p.distanceToSqr(this.getX(), p.getY(), this.getZ()));
            if (h < RADIUS && Math.abs(p.getY() - g) < 2.5) {
                DamageSource source = this.owner != null ? this.damageSources().mobAttack((net.minecraft.world.entity.LivingEntity) this.owner)
                        : this.damageSources().magic();
                p.hurtServer(level, source, DAMAGE);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
