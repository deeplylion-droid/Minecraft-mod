package com.filorosso.entity;

import com.filorosso.data.Couple;
import com.filorosso.server.Boss;
import com.filorosso.server.Bonds;
import com.filorosso.server.Servants;
import com.filorosso.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo Spezzacuori: a giant, monstrous humanoid (seven blocks tall, long arms ending in blades, a
 * broken heart glowing in his hollow chest). He hates love: he slams, pounds the ground, throws
 * scissors that cut the threads, grabs lovers and throws them away from each other, screams to call
 * his servants. Only couple hits (both partners within a second) really hurt him.
 */
public class Spezzacuori extends Monster {
    public static final int NONE = 0;
    public static final int SLAM = 1;
    public static final int POUND = 2;
    public static final int THROW = 3;
    public static final int GRAB = 4;
    public static final int SCREAM = 5;
    public static final int RISE = 6;

    private static final int[] DURATION = {0, 26, 40, 30, 36, 50, 70};

    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(Spezzacuori.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_START = SynchedEntityData.defineId(Spezzacuori.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RAGE = SynchedEntityData.defineId(Spezzacuori.class, EntityDataSerializers.BOOLEAN);

    private final ServerBossEvent bossEvent = new ServerBossEvent(java.util.UUID.randomUUID(), Component.translatable("entity.filorosso.spezzacuori"),
            BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.NOTCHED_10);
    private int slamCooldown = 40;
    private int poundCooldown = 160;
    private int throwCooldown = 100;
    private int grabCooldown = 220;
    private boolean screamed66;
    private boolean screamed33;
    private int farTicks;
    private int hintCooldown;

    public Spezzacuori(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 300;
        this.setPersistenceRequired();
        this.bossEvent.setDarkenScreen(true);
        this.bossEvent.setCreateWorldFog(true);
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.STEP_HEIGHT, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, NONE);
        builder.define(DATA_ACTION_START, 0);
        builder.define(DATA_RAGE, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32.0F));
    }

    // ------------------------------------------------------------------ state shared with the renderer

    public int action() {
        return this.entityData.get(DATA_ACTION);
    }

    /** Ticks (with partial) since the current action started. */
    public float actionTime(float partial) {
        return this.tickCount - this.entityData.get(DATA_ACTION_START) + partial;
    }

    public boolean raging() {
        return this.entityData.get(DATA_RAGE);
    }

    private void start(int action) {
        this.entityData.set(DATA_ACTION, action);
        this.entityData.set(DATA_ACTION_START, this.tickCount);
    }

    /** Called right after summoning: he rises from the ground. */
    public void rise() {
        this.start(RISE);
    }

    // ------------------------------------------------------------------ damage

    /** Only couple hits count fully; while rising he cannot be hurt. */
    public float adjustDamage(float amount, boolean combo, Couple couple) {
        if (this.action() == RISE || this.isDeadOrDying()) {
            return 0.0F;
        }
        if (combo) {
            if (couple != null) {
                couple.bossHits++;
            }
            return amount;
        }
        return amount * 0.12F;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && source.getEntity() instanceof ServerPlayer player && this.hintCooldown <= 0 && amount < 3.0F) {
            this.hintCooldown = 100;
            player.sendSystemMessage(Component.translatable("filorosso.msg.boss_hint").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return hurt;
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowVehicles) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.BOSS_ROAR;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BOSS_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BOSS_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 3.0F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    // ------------------------------------------------------------------ boss bar

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level) {
            this.bossEvent.setProgress(0.0F);
            Boss.onDeath(level.getServer(), this);
        }
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.level() instanceof ServerLevel level) {
            if (this.deathTime % 3 == 0) {
                level.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 3.0 + this.deathTime * 0.03, this.getZ(), 6, 1.2, 2.0, 1.2, 0.15);
                level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 4.5, this.getZ(), 4, 0.6, 0.8, 0.6, 0.05);
            }
            if (this.deathTime == 70) {
                level.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 4.0, this.getZ(), 120, 3.0, 3.0, 3.0, 0.6);
                level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), this.getX(), this.getY() + 4.0, this.getZ(), 1, 0, 0, 0, 0);
            }
            if (this.deathTime >= 80) {
                this.bossEvent.removeAllPlayers();
                this.remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        this.bossEvent.removeAllPlayers();
    }

    // ------------------------------------------------------------------ brain

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        this.hintCooldown--;
        if (!this.raging() && this.getHealth() < this.getMaxHealth() * 0.5F) {
            this.entityData.set(DATA_RAGE, true);
            this.bossEvent.setColor(BossEvent.BossBarColor.RED);
            this.playSound(ModSounds.BOSS_SCREAM, 4.0F, 0.8F);
            var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(0.38);
            }
        }
        int action = this.action();
        float t = this.actionTime(0.0F);
        if (action != NONE) {
            this.getNavigation().stop();
            this.act(level, action, (int) t);
            if (t >= DURATION[action]) {
                this.start(NONE);
            }
            return;
        }
        LivingEntity target = this.pickTarget(level);
        this.setTarget(target);
        if (target == null) {
            return;
        }
        double d = this.distanceTo(target);
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        float rate = this.raging() ? 1.5F : 1.0F;
        this.slamCooldown -= rate;
        this.poundCooldown -= rate;
        this.throwCooldown -= rate;
        this.grabCooldown -= rate;
        if (!this.screamed66 && this.getHealth() < this.getMaxHealth() * 0.66F) {
            this.screamed66 = true;
            this.start(SCREAM);
            return;
        }
        if (!this.screamed33 && this.getHealth() < this.getMaxHealth() * 0.33F) {
            this.screamed33 = true;
            this.start(SCREAM);
            return;
        }
        if (d < 4.6 && this.slamCooldown <= 0) {
            this.slamCooldown = 30;
            this.start(SLAM);
        } else if (d < 5.5 && this.grabCooldown <= 0) {
            this.grabCooldown = 260;
            this.start(GRAB);
        } else if (d < 10.0 && this.poundCooldown <= 0) {
            this.poundCooldown = 220;
            this.start(POUND);
        } else if (d > 6.0 && d < 32.0 && this.throwCooldown <= 0 && this.hasLineOfSight(target)) {
            this.throwCooldown = 150;
            this.start(THROW);
        } else {
            this.getNavigation().moveTo(target, 1.0);
        }
        // stuck far away: he sinks into the ground and comes up next to them
        this.farTicks = d > 26.0 ? this.farTicks + 1 : 0;
        if (this.farTicks > 200) {
            this.farTicks = 0;
            Vec3 dir = this.position().subtract(target.position()).normalize().scale(7.0);
            BlockPos at = BlockPos.containing(target.getX() + dir.x, target.getY(), target.getZ() + dir.z);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1, this.getZ(), 40, 1.5, 1.5, 1.5, 0.05);
            this.teleportTo(at.getX() + 0.5, y, at.getZ() + 0.5);
            this.start(RISE);
        }
    }

    private LivingEntity pickTarget(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof ServerPlayer p && p.isAlive() && !p.isSpectator() && !p.isCreative() && this.distanceTo(p) < 48.0
                && this.tickCount % 60 != 0) {
            return current;
        }
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) {
                continue;
            }
            double d = this.distanceTo(p) - (Bonds.coupleOf(p) != null ? 6.0 : 0.0);
            if (d < bestD && this.distanceTo(p) < 48.0) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private List<ServerPlayer> playersNear(ServerLevel level, double radius) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceTo(this) < radius) {
                list.add(p);
            }
        }
        return list;
    }

    private Vec3 forward() {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    private void act(ServerLevel level, int action, int t) {
        switch (action) {
            case RISE -> {
                if (t % 4 == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(this.blockPosition().below())),
                            this.getX(), this.getY() + 0.2, this.getZ(), 30, 1.6, 0.3, 1.6, 0.15);
                }
                if (t == 2) {
                    this.playSound(ModSounds.BOSS_ROAR, 5.0F, 0.7F);
                }
            }
            case SLAM -> {
                if (t == 12) {
                    Vec3 front = this.position().add(this.forward().scale(2.4));
                    this.playSound(ModSounds.BOSS_SLAM, 3.0F, 1.0F);
                    level.sendParticles(ParticleTypes.EXPLOSION, front.x, front.y + 0.5, front.z, 2, 0.5, 0.2, 0.5, 0.0);
                    for (ServerPlayer p : this.playersNear(level, 6.0)) {
                        if (p.position().distanceTo(front) < 3.6) {
                            p.hurtServer(level, this.damageSources().mobAttack(this), this.raging() ? 13.0F : 10.0F);
                            Vec3 push = p.position().subtract(this.position()).normalize().scale(1.3).add(0, 0.5, 0);
                            fling(p, push);
                        }
                    }
                }
            }
            case POUND -> {
                if (t == 20) {
                    this.playSound(ModSounds.BOSS_SLAM, 5.0F, 0.6F);
                    for (int i = 0; i < 24; i++) {
                        double a = i * Math.PI * 2.0 / 24.0;
                        for (double r = 2.0; r <= 9.0; r += 3.5) {
                            level.sendParticles(ParticleTypes.CLOUD, this.getX() + Math.cos(a) * r, this.getY() + 0.3, this.getZ() + Math.sin(a) * r,
                                    1, 0.1, 0.05, 0.1, 0.02);
                        }
                    }
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(this.blockPosition().below())),
                            this.getX(), this.getY() + 0.2, this.getZ(), 80, 4.0, 0.3, 4.0, 0.3);
                    for (ServerPlayer p : this.playersNear(level, 9.5)) {
                        if (p.onGround() || p.getY() - this.getY() < 1.5) {
                            p.hurtServer(level, this.damageSources().mobAttack(this), 6.0F);
                            Vec3 push = p.position().subtract(this.position()).multiply(1, 0, 1).normalize().scale(1.1).add(0, 0.7, 0);
                            fling(p, push);
                        }
                    }
                }
            }
            case THROW -> {
                if (t == 14) {
                    LivingEntity target = this.getTarget();
                    if (target == null) {
                        return;
                    }
                    Vec3 aim = target.position().add(0, 1.0, 0);
                    if (target instanceof ServerPlayer p) {
                        ServerPlayer partner = Bonds.partner(p);
                        if (partner != null && partner.level() == level && partner.distanceTo(p) < 12) {
                            aim = p.position().add(partner.position()).scale(0.5).add(0, 1.0, 0);
                        }
                    }
                    Vec3 hand = this.position().add(this.forward().scale(1.2)).add(0, 5.2, 0);
                    int count = this.raging() ? 3 : 1;
                    for (int i = 0; i < count; i++) {
                        Scissors s = new Scissors(level, this);
                        s.setPos(hand.x, hand.y, hand.z);
                        Vec3 dir = aim.subtract(hand).normalize();
                        double spread = (i - (count - 1) / 2.0) * 0.22;
                        dir = dir.yRot((float) spread);
                        s.shoot(dir.x, dir.y + 0.04, dir.z, 1.25F, 0.0F);
                        level.addFreshEntity(s);
                    }
                    this.playSound(ModSounds.SCISSORS, 3.0F, 0.7F);
                }
            }
            case GRAB -> {
                if (t == 12) {
                    LivingEntity target = this.getTarget();
                    if (target instanceof ServerPlayer p && p.distanceTo(this) < 6.5) {
                        ServerPlayer partner = Bonds.partner(p);
                        Vec3 away = partner != null && partner.level() == level ? p.position().subtract(partner.position())
                                : p.position().subtract(this.position());
                        if (away.lengthSqr() < 0.01) {
                            away = this.forward();
                        }
                        away = away.multiply(1, 0, 1).normalize();
                        p.hurtServer(level, this.damageSources().mobAttack(this), 4.0F);
                        fling(p, away.scale(2.0).add(0, 0.9, 0));
                        this.playSound(ModSounds.BOSS_ROAR, 3.0F, 1.3F);
                        p.sendSystemMessage(Component.translatable("filorosso.msg.boss_grab").withStyle(ChatFormatting.RED), true);
                    }
                }
            }
            case SCREAM -> {
                if (t == 10) {
                    this.playSound(ModSounds.BOSS_SCREAM, 6.0F, 1.0F);
                    for (ServerPlayer p : this.playersNear(level, 40.0)) {
                        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0));
                        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1));
                    }
                    int couples = Math.max(1, Boss.couplesOnline(level.getServer()));
                    List<ServerPlayer> near = this.playersNear(level, 40.0);
                    for (int i = 0; i < Math.min(8, couples * 2) && !near.isEmpty(); i++) {
                        Servants.spawnNear(level, near.get(i % near.size()), true);
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void fling(ServerPlayer p, Vec3 v) {
        p.setDeltaMovement(v);
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        p.syncVelocity = true;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        return false;
    }
}
