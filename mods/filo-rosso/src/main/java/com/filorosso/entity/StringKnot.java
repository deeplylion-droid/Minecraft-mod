package com.filorosso.entity;

import com.filorosso.item.ModItems;
import com.filorosso.sound.ModSounds;
import com.filorosso.world.Puppets;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The knot where the Burattinaio's strings meet above a puppet's head. Hit it three times (once with
 * the scissors of the Spaiati) and the strings snap: the puppet is free.
 */
public class StringKnot extends Entity {
    public static final int HITS = 3;
    /** Above the puppet's head (blocks over its feet). */
    public static final double HEIGHT = 2.7;

    private static final EntityDataAccessor<Integer> DATA_PUPPET = SynchedEntityData.defineId(StringKnot.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PUPPETEER = SynchedEntityData.defineId(StringKnot.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HITS = SynchedEntityData.defineId(StringKnot.class, EntityDataSerializers.INT);

    public StringKnot(EntityType<? extends StringKnot> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PUPPET, -1);
        builder.define(DATA_PUPPETEER, -1);
        builder.define(DATA_HITS, 0);
    }

    public void tie(Entity puppet, Entity puppeteer) {
        this.entityData.set(DATA_PUPPET, puppet.getId());
        this.entityData.set(DATA_PUPPETEER, puppeteer.getId());
        this.setPos(puppet.getX(), puppet.getY() + HEIGHT, puppet.getZ());
    }

    public int puppetId() {
        return this.entityData.get(DATA_PUPPET);
    }

    public int puppeteerId() {
        return this.entityData.get(DATA_PUPPETEER);
    }

    public int hits() {
        return this.entityData.get(DATA_HITS);
    }

    @Override
    public void tick() {
        super.tick();
        Entity puppet = this.level().getEntity(this.puppetId());
        if (puppet != null) {
            // it follows the puppet, bobbing on its strings
            double bob = Math.sin(this.tickCount * 0.3) * 0.08;
            this.setPos(puppet.getX(), puppet.getY() + HEIGHT + bob, puppet.getZ());
        }
        if (this.level().isClientSide()) {
            return;
        }
        if (puppet == null || !puppet.isAlive() || this.tickCount > 400) {
            this.discard();
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (!(source.getEntity() instanceof ServerPlayer player) || this.isRemoved()) {
            return false;
        }
        Entity puppet = level.getEntity(this.puppetId());
        if (puppet == player) {
            // a puppet cannot reach its own strings
            return false;
        }
        boolean scissors = player.getMainHandItem().is(ModItems.SCISSORS);
        int hits = scissors ? HITS : this.hits() + 1;
        this.entityData.set(DATA_HITS, hits);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.KNOT_HIT, SoundSource.PLAYERS, 1.2F, 0.8F + hits * 0.15F);
        level.sendParticles(new DustParticleOptions(0xD8202A, 1.4F), this.getX(), this.getY(), this.getZ(), 10, 0.25, 0.25, 0.25, 0.0);
        if (hits >= HITS && puppet instanceof ServerPlayer victim) {
            Puppets.free(level, victim, player);
            this.discard();
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
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
