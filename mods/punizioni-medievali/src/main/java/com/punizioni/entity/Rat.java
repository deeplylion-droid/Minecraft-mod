package com.punizioni.entity;

import com.punizioni.ModSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A sewer rat of the rat table: grey-brown, long naked tail, red eyes that shine in the dark. It does
 * not think for itself: the table runs it out of the cage, over the victim and back. It can be killed
 * (one good hit): friends can save the victim by killing the rats.
 */
public class Rat extends Mob {
    private static final EntityDataAccessor<Boolean> DATA_GNAWING = SynchedEntityData.defineId(Rat.class, EntityDataSerializers.BOOLEAN);

    public Rat(EntityType<? extends Rat> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 2.0).add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GNAWING, false);
    }

    public boolean gnawing() {
        return this.entityData.get(DATA_GNAWING);
    }

    public void setGnawing(boolean gnawing) {
        this.entityData.set(DATA_GNAWING, gnawing);
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.RATS;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return ModSounds.RATS;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }
}
