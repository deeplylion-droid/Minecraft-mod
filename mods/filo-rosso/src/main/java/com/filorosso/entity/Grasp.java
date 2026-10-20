package com.filorosso.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The hand Recisione pushes up through the ground under someone (its GRAB): it comes up, closes at
 * {@link #CLOSE} and sinks back. Only something to look at: the boss decides who it caught.
 */
public class Grasp extends Entity {
    public static final int LIFE = 34;
    public static final int CLOSE = 9;

    public Grasp(EntityType<? extends Grasp> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (this.tickCount < CLOSE + 2) {
                // the ground breaking open
                BlockState below = this.level().getBlockState(this.blockPosition().below());
                if (!below.isAir()) {
                    for (int i = 0; i < 4; i++) {
                        this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, below), this.getRandomX(1.2), this.getY() + 0.1,
                                this.getRandomZ(1.2), 0.0, 0.25, 0.0);
                    }
                }
                this.level().addParticle(ParticleTypes.SQUID_INK, this.getRandomX(0.8), this.getY() + 0.1, this.getRandomZ(0.8), 0.0, 0.05, 0.0);
            }
            return;
        }
        if (this.tickCount >= LIFE) {
            this.discard();
        }
    }

    /** 0 = still underground, 1 = all the way up. */
    public float rise(float partial) {
        float t = this.tickCount + partial;
        float up = Math.min(1.0F, t / 6.0F);
        float down = Math.max(0.0F, (t - (LIFE - 10)) / 10.0F);
        return Math.max(0.0F, up * up * (3.0F - 2.0F * up) - down * down);
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
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
