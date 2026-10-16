package com.filorosso.entity;

import com.filorosso.data.Couple;
import com.filorosso.server.Links;
import com.filorosso.server.Tether;
import com.filorosso.sound.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * "Uno Spaiato": a black, faceless servant of Recisione with dark red eyes. It
 * hunts couples at night and cuts the threads it gets close to; it burns in the sun and drops
 * heart shards (the way to call its master).
 */
public class Servant extends Zombie {
    private int cutCooldown;

    public Servant(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createServantAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
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
        return true;
    }

    @Override
    public void setBaby(boolean baby) {
        super.setBaby(false);
    }

    /** They carry a pair of scissors (never dropped). */
    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(com.filorosso.item.ModItems.SCISSORS));
        this.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MINION_AMBIENT;
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
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || !this.isAlive()) {
            return;
        }
        if (this.cutCooldown > 0) {
            this.cutCooldown--;
            return;
        }
        if (this.tickCount % 5 != 0) {
            return;
        }
        Vec3 here = this.position().add(0.0, 1.2, 0.0);
        for (Links.Pair pair : Links.active(level.getServer())) {
            Couple couple = pair.couple();
            if (couple == null || pair.a().level() != level || pair.a().distanceToSqr(this) > 20 * 20) {
                continue;
            }
            if (Tether.distanceToThread(pair.a(), pair.b(), here) < 1.4) {
                Tether.cut(level.getServer(), couple, 20 * 15);
                level.sendParticles(ParticleTypes.CRIT, here.x, here.y, here.z, 12, 0.3, 0.3, 0.3, 0.2);
                this.playSound(ModSounds.SCISSORS, 1.0F, 1.2F);
                this.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
                this.cutCooldown = 200;
                return;
            }
        }
    }
}
