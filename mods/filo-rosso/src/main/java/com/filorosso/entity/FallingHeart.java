package com.filorosso.entity;

import com.filorosso.data.Couple;
import com.filorosso.server.Affinity;
import com.filorosso.server.Bonds;
import com.filorosso.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A heart of the "Pioggia di cuori" event: it floats down, sways, then waits on the ground for a
 * while. Only a couple can take it, the two of them close together: affinity and health for both.
 */
public class FallingHeart extends Entity {
    /** How long it stays once it has landed (ticks). */
    public static final int LIFE_ON_GROUND = 20 * 20;
    public static final int AFFINITY = 5;
    public static final float HEAL = 4.0F;
    /** Partners closer than this take it together. */
    public static final double TOGETHER = 4.0;

    private static final Map<UUID, Long> HINTED = new HashMap<>();
    private int landedAt = -1;

    public FallingHeart(EntityType<? extends FallingHeart> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.onGround()) {
            // down slowly, swaying like a leaf
            double t = this.tickCount + this.getId() * 7.0;
            this.setDeltaMovement(Math.sin(t * 0.11) * 0.035, -0.09, Math.cos(t * 0.09) * 0.035);
            this.move(MoverType.SELF, this.getDeltaMovement());
        } else if (this.landedAt < 0) {
            this.landedAt = this.tickCount;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            // a few pink sparks trailing behind
            if (this.tickCount % 3 == 0) {
                this.level().addParticle(new net.minecraft.core.particles.DustParticleOptions(0xFF5A8C, 0.8F),
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.4, this.getY() + 0.2 + this.random.nextDouble() * 0.3,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.4, 0.0, 0.0, 0.0);
            }
            return;
        }
        if (this.tickCount > 20 * 60 || this.landedAt >= 0 && this.tickCount - this.landedAt > LIFE_ON_GROUND || this.getY() < level.getMinY()) {
            level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.3, this.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
            this.discard();
            return;
        }
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(0.7))) {
            if (!p.isAlive() || p.isSpectator()) {
                continue;
            }
            Couple couple = Bonds.coupleOf(p);
            ServerPlayer partner = Bonds.partner(p);
            if (couple != null && partner != null && partner.isAlive() && partner.level() == level && partner.distanceTo(p) <= TOGETHER) {
                this.take(level, couple, p, partner);
                return;
            }
            long now = level.getGameTime();
            Long last = HINTED.get(p.getUUID());
            if (last == null || now - last > 60) {
                HINTED.put(p.getUUID(), now);
                p.sendSystemMessage(Component.translatable(couple == null ? "filorosso.msg.heart_single" : "filorosso.msg.heart_together")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
    }

    private void take(ServerLevel level, Couple couple, ServerPlayer p, ServerPlayer partner) {
        Affinity.add(level.getServer(), couple, AFFINITY, true);
        for (ServerPlayer q : new ServerPlayer[]{p, partner}) {
            q.heal(HEAL);
            level.sendParticles(ParticleTypes.HEART, q.getX(), q.getY() + 1.4, q.getZ(), 5, 0.4, 0.3, 0.4, 0.05);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.GIFT, SoundSource.PLAYERS, 1.0F, 1.5F);
        level.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.4, this.getZ(), 10, 0.4, 0.4, 0.4, 0.1);
        this.discard();
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
