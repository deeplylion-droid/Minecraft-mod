package com.filorosso.entity;

import com.filorosso.sound.ModSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A spectator of the Theatre: a black figure with no face, sitting in the stalls. It does not move;
 * it only turns its head to follow you. In the later acts some of them stand up, their eyes light up
 * white, and they come down to the stage. When the Burattinaio falls, they applaud.
 */
public class Spectator extends Zombie {
    private static final EntityDataAccessor<Boolean> DATA_AWAKE = SynchedEntityData.defineId(Spectator.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CLAP = SynchedEntityData.defineId(Spectator.class, EntityDataSerializers.BOOLEAN);

    public Spectator(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setNoGravity(true);
        this.setSilent(true);
    }

    public static AttributeSupplier.Builder createSpectatorAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.29)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_AWAKE, false);
        builder.define(DATA_CLAP, false);
    }

    public boolean awake() {
        return this.entityData.get(DATA_AWAKE);
    }

    public boolean clapping() {
        return this.entityData.get(DATA_CLAP);
    }

    /** It stands up and comes for the players. */
    public void wake() {
        if (this.awake()) {
            return;
        }
        this.entityData.set(DATA_AWAKE, true);
        this.setNoAi(false);
        this.setNoGravity(false);
        this.setSilent(false);
        this.setPos(this.getX(), this.getY() + 0.7, this.getZ());
        this.playSound(ModSounds.SPECTATOR_RISE, 1.2F, 0.8F + this.random.nextFloat() * 0.4F);
    }

    public void clap(boolean on) {
        this.entityData.set(DATA_CLAP, on);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || this.awake()) {
            return;
        }
        // sitting: only the head moves, towards the nearest player
        Player p = this.level().getNearestPlayer(this, 40.0);
        if (p != null && !p.isSpectator()) {
            double dx = p.getX() - this.getX();
            double dz = p.getZ() - this.getZ();
            double dy = p.getEyeY() - this.getEyeY();
            float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            float rel = Mth.clamp(Mth.wrapDegrees(want - this.getYRot()), -75.0F, 75.0F);
            this.yHeadRot = Mth.approachDegrees(this.yHeadRot, this.getYRot() + rel, 4.0F);
            float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            this.setXRot(Mth.approachDegrees(this.getXRot(), Mth.clamp(pitch, -40.0F, 40.0F), 3.0F));
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // nothing happens to the ones still sitting in the dark
        if (!this.awake()) {
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean isPickable() {
        return this.awake() && super.isPickable();
    }

    @Override
    public boolean isPushable() {
        return this.awake();
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean canBreakDoors() {
        return false;
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public void setBaby(boolean baby) {
        super.setBaby(false);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.awake() ? ModSounds.SPECTATOR_AMBIENT : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MINION_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MINION_DEATH;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        return this.awake() && target instanceof ServerPlayer && super.doHurtTarget(level, target);
    }
}
