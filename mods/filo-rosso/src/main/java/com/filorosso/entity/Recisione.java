package com.filorosso.entity;

import com.filorosso.data.Couple;
import com.filorosso.server.Bonds;
import com.filorosso.server.Boss;
import com.filorosso.server.Servants;
import com.filorosso.server.Tether;
import com.filorosso.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Recisione, la Marionetta: a giant puppet of black cord, ten blocks tall, made of the threads of every
 * couple that ever broke. An iron cage for a head with three empty red eyes inside, a knot of red
 * thread for a heart, arms down to its knees; the strings that held it were cut, and they still rise
 * from its shoulders: that is what it whips with. Black boxes glitch around it, more in its later
 * forms; in the last one it grows. A bell rings when it comes. Only couple hits really hurt it.
 */
public class Recisione extends Monster {
    public static final int NONE = 0;
    /** The cut strings lash down in front of it, the right ones first, then the left ones. */
    public static final int SLAM = 1;
    /** Both hands into the ground: a ring of roots runs out across the ground. */
    public static final int ROOTS = 2;
    /** It looks up: black missiles fall from the sky on the couples. */
    public static final int MISSILES = 3;
    /** A hand comes out of the ground under someone and throws them away from their partner. */
    public static final int GRAB = 4;
    /** The bell: darkness, and gravity turns upside down for a moment. */
    public static final int BELL = 5;
    /** Climbing out of the ground. */
    public static final int RISE = 6;
    /** It glitches away and appears next to its target. */
    public static final int BLINK = 7;

    /** Ticks each action lasts (SLAM is the model's attack1 animation, 2 s). */
    private static final int[] DURATION = {0, 40, 44, 34, 30, 60, 70, 16};
    /** When the two lashes of SLAM land (the model's attack1 at 0.70 s and 0.83 s). */
    private static final int LASH_RIGHT = 14, LASH_LEFT = 17;
    /** How far in front the strings reach (blocks from its centre). */
    private static final double LASH_REACH = 6.5;
    /** The names /filo boss attacco knows them by (empty: cannot be forced). */
    public static final String[] ACTION_NAMES = {"", "frusta", "radici", "missili", "presa", "campana", "", "salto"};

    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(Recisione.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_START = SynchedEntityData.defineId(Recisione.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(Recisione.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(UUID.randomUUID(), glitchName(-1), BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS);
    private int slamCooldown = 40;
    private int rootsCooldown = 120;
    private int missileCooldown = 90;
    private int grabCooldown = 200;
    private int bellCooldown = 900;
    private int farTicks;
    private int hintCooldown;
    private boolean lastCombo;
    private final Set<UUID> hitThisAction = new HashSet<>();
    private final Set<Couple> couplesHit = new HashSet<>();
    /** Where the hand comes out of the ground (GRAB). */
    private Vec3 graspAt;
    private final List<ServerPlayer> lifted = new ArrayList<>();
    /** When the current action and phase started, in this side's ticks (set when the synced values change). */
    private int localActionStart;
    private int localPhaseStart;

    public Recisione(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 300;
        this.setPersistenceRequired();
        this.bossEvent.setDarkenScreen(true);
        this.bossEvent.setCreateWorldFog(true);
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.STEP_HEIGHT, 3.0);
    }

    /** "RECISIONE" with one letter that will not stay still. */
    public static Component glitchName(int glitch) {
        // a name, the same in every language
        String name = "RECISIONE";
        MutableComponent out = Component.empty();
        for (int i = 0; i < name.length(); i++) {
            MutableComponent c = Component.literal(String.valueOf(name.charAt(i)));
            if (i == glitch) {
                c = c.withStyle(ChatFormatting.OBFUSCATED);
            }
            out.append(c);
        }
        return out.withStyle(ChatFormatting.DARK_RED);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, NONE);
        builder.define(DATA_ACTION_START, 0);
        builder.define(DATA_PHASE, 1);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    // ------------------------------------------------------------------ state shared with the renderer

    /** How many ticks an action lasts. */
    public static int duration(int action) {
        return action >= 0 && action < DURATION.length ? DURATION[action] : 0;
    }

    public int action() {
        return this.entityData.get(DATA_ACTION);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // tick counts differ between server and client: each side times the action from when it learnt of it
        if (DATA_ACTION.equals(key) || DATA_ACTION_START.equals(key)) {
            this.localActionStart = this.tickCount;
        }
        if (DATA_PHASE.equals(key)) {
            this.localPhaseStart = this.tickCount;
        }
    }

    /** Ticks (with partial) since the current action started. */
    public float actionTime(float partial) {
        return this.tickCount - this.localActionStart + partial;
    }

    /** 1, 2 or 3. */
    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    /** Ticks since the current phase started. */
    public float phaseTime(float partial) {
        return this.tickCount - this.localPhaseStart + partial;
    }

    private void start(int action) {
        this.entityData.set(DATA_ACTION, action);
        // a new value every time, so the same attack twice in a row restarts on the clients too
        this.entityData.set(DATA_ACTION_START, this.tickCount);
        this.localActionStart = this.tickCount;
        this.hitThisAction.clear();
        this.couplesHit.clear();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() && !this.isDeadOrDying()) {
            // black dust coming off its body, more in its later forms
            for (int i = 0; i < this.phase(); i++) {
                this.level().addParticle(new DustParticleOptions(0x050505, 0.9F + this.random.nextFloat() * 0.9F), this.getRandomX(1.6),
                        this.getY() + this.random.nextDouble() * 9.5, this.getRandomZ(1.6), 0.0, 0.03, 0.0);
            }
            if (this.random.nextInt(4) == 0) {
                this.level().addParticle(ParticleTypes.ASH, this.getRandomX(3.0), this.getY() + this.random.nextDouble() * 10.0, this.getRandomZ(3.0),
                        0.0, 0.0, 0.0);
            }
        }
    }

    /** /filo boss attacco: this attack, right now (not while it climbs out or falls apart). */
    public void force(int action) {
        if (this.action() != RISE && !this.isDeadOrDying()) {
            if (this.getTarget() == null && this.level() instanceof ServerLevel level) {
                this.setTarget(this.pickTarget(level));
            }
            this.start(action);
        }
    }

    /** Called right after summoning: it climbs out of the ground. */
    public void rise() {
        this.start(RISE);
    }

    // ------------------------------------------------------------------ damage

    /** Only couple hits count fully; while it climbs out it cannot be hurt. */
    public float adjustDamage(float amount, boolean combo, Couple couple) {
        this.lastCombo = combo;
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
        if (hurt && source.getEntity() instanceof ServerPlayer player && this.hintCooldown <= 0 && !this.lastCombo) {
            this.hintCooldown = 100;
            player.sendSystemMessage(Component.translatable("filorosso.msg.boss_hint").withStyle(ChatFormatting.DARK_RED), true);
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
        return 240;
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
            this.start(NONE);
            Boss.onDeath(level.getServer(), this);
        }
    }

    /**
     * The strings snap, it collapses like a puppet (the model's death animation, 64 ticks), then it
     * falls apart into black dust; the threads it was made of turn back into hearts.
     */
    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.level() instanceof ServerLevel level) {
            if (this.deathTime == 8) {
                // its strings snap
                this.playSound(ModSounds.SNAP, 5.0F, 0.55F);
                this.playSound(ModSounds.SNAP, 5.0F, 0.8F);
                level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 8.5, this.getZ(), 30, 1.2, 0.8, 1.2, 0.06);
            }
            if (this.deathTime == 22) {
                // and it hits the ground
                this.playSound(ModSounds.BOSS_SLAM, 5.0F, 0.55F);
                this.ground(level, this.getX(), this.getZ(), 60, 2.0);
            }
            if (this.deathTime > 22 && this.deathTime % 2 == 0) {
                int dust = this.deathTime > 64 ? 12 : 3;
                level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 1.2, this.getZ(), dust, 1.6, 0.6, 1.6, 0.02);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), dust / 3, 1.4, 0.5, 1.4, 0.01);
            }
            if (this.deathTime == 40) {
                this.playSound(ModSounds.BOSS_SCREAM, 4.0F, 0.5F);
            }
            if (this.deathTime == 92) {
                // the bell, one last time
                this.playSound(ModSounds.BOSS_BELL, 6.0F, 1.25F);
            }
            if (this.deathTime >= 100) {
                level.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 2.0, this.getZ(), 40, 4.0, 1.5, 4.0, 0.1);
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
        if (this.tickCount % 23 == 0) {
            this.bossEvent.setName(glitchName(this.random.nextInt(4) == 0 ? -1 : this.random.nextInt(9)));
        }
        this.hintCooldown--;
        int phase = this.getHealth() <= this.getMaxHealth() * 0.33F ? 3 : this.getHealth() <= this.getMaxHealth() * 0.66F ? 2 : 1;
        if (phase > this.phase() && this.action() != RISE) {
            this.entityData.set(DATA_PHASE, phase);
            for (ServerPlayer p : this.playersNear(level, 64.0)) {
                p.sendSystemMessage(Component.translatable("filorosso.msg.boss_phase" + phase).withStyle(ChatFormatting.DARK_RED), true);
            }
            var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(phase == 3 ? 0.3 : 0.26);
            }
            this.start(BELL);
            return;
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
        this.getLookControl().setLookAt(target, 20.0F, 20.0F);
        float rate = phase == 3 ? 1.6F : phase == 2 ? 1.25F : 1.0F;
        this.slamCooldown -= rate;
        this.rootsCooldown -= rate;
        this.missileCooldown -= rate;
        this.grabCooldown -= rate;
        if (phase == 3) {
            this.bellCooldown -= rate;
        }
        if (this.bellCooldown <= 0) {
            this.bellCooldown = 900;
            this.start(BELL);
        } else if (d < 6.0 && this.slamCooldown <= 0) {
            this.slamCooldown = 40;
            this.start(SLAM);
        } else if (d < 9.0 && this.rootsCooldown <= 0) {
            this.rootsCooldown = 200;
            this.start(ROOTS);
        } else if (d < 26.0 && this.grabCooldown <= 0) {
            this.grabCooldown = 240;
            this.start(GRAB);
        } else if (d > 6.0 && d < 40.0 && this.missileCooldown <= 0) {
            this.missileCooldown = 170;
            this.start(MISSILES);
        } else {
            this.getNavigation().moveTo(target, 1.0);
        }
        // far away for too long: it glitches next to them
        this.farTicks = d > 24.0 ? this.farTicks + 1 : 0;
        if (this.farTicks > 120) {
            this.farTicks = 0;
            this.start(BLINK);
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
            // it hunts couples first
            double d = this.distanceTo(p) - (Bonds.coupleOf(p) != null ? 8.0 : 0.0);
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

    private void ground(ServerLevel level, double x, double z, int count, double spread) {
        BlockPos below = BlockPos.containing(x, this.getY() - 0.5, z);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), x, this.getY() + 0.2, z, count, spread, 0.2, spread, 0.15);
    }

    /**
     * One life, one hit: when both partners are caught by the same attack only the first one takes
     * it (their shared life passes it on to the other anyway).
     */
    private void strike(ServerLevel level, ServerPlayer p, float amount) {
        Couple couple = Bonds.coupleOf(p);
        if (couple != null && !this.couplesHit.add(couple)) {
            return;
        }
        p.hurtServer(level, this.damageSources().mobAttack(this), amount);
    }

    private void act(ServerLevel level, int action, int t) {
        switch (action) {
            case RISE -> {
                if (t % 3 == 0 && t < 56) {
                    this.ground(level, this.getX(), this.getZ(), 24, 1.8);
                    level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 0.5, this.getZ(), 16, 1.5, 0.4, 1.5, 0.05);
                }
                if (t == 2) {
                    this.playSound(ModSounds.BOSS_ROAR, 6.0F, 0.7F);
                }
                if (t == 56) {
                    this.playSound(ModSounds.BOSS_GLITCH, 5.0F, 0.8F);
                }
            }
            case SLAM -> {
                if (t == 2) {
                    this.playSound(ModSounds.BOSS_GLITCH, 2.0F, 1.3F);
                }
                // it turns to face its target while the strings curl back (its body does not always face where its head looks)
                LivingEntity target = this.getTarget();
                if (target != null && t < LASH_RIGHT) {
                    this.turnTowards(target, 24.0F);
                }
                if (t == LASH_RIGHT || t == LASH_LEFT) {
                    this.lash(level, t == LASH_RIGHT ? 1.0 : -1.0);
                }
            }
            case ROOTS -> {
                if (t == 14) {
                    this.playSound(ModSounds.BOSS_SLAM, 4.0F, 0.55F);
                    this.playSound(ModSounds.GRASP, 4.0F, 0.7F);
                }
                if (t >= 16 && t <= 30) {
                    double r = 2.0 + (t - 16) * 0.65;
                    int n = (int) (r * 6);
                    for (int i = 0; i < n; i++) {
                        double a = i * Math.PI * 2.0 / n + t * 0.1;
                        double x = this.getX() + Math.cos(a) * r;
                        double z = this.getZ() + Math.sin(a) * r;
                        level.sendParticles(ParticleTypes.SQUID_INK, x, this.getY() + 0.3, z, 1, 0.05, 0.4, 0.05, 0.02);
                        if (i % 3 == 0) {
                            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, this.getY() + 0.6, z, 1, 0.05, 0.3, 0.05, 0.01);
                        }
                    }
                    for (ServerPlayer p : this.playersNear(level, r + 1.0)) {
                        double h = Math.sqrt(p.distanceToSqr(this.getX(), p.getY(), this.getZ()));
                        if (Math.abs(h - r) < 0.9 && p.getY() - this.getY() < 1.5 && this.hitThisAction.add(p.getUUID())) {
                            this.strike(level, p, 6.0F);
                            fling(p, p.position().subtract(this.position()).multiply(1, 0, 1).normalize().scale(0.6).add(0, 0.9, 0));
                        }
                    }
                }
            }
            case MISSILES -> {
                if (t == 6) {
                    this.playSound(ModSounds.BOSS_GLITCH, 4.0F, 0.6F);
                }
                int total = this.phase() == 3 ? 7 : this.phase() == 2 ? 5 : 3;
                if (t >= 12 && t < 12 + total * 3 && (t - 12) % 3 == 0) {
                    List<ServerPlayer> near = this.playersNear(level, 40.0);
                    if (!near.isEmpty()) {
                        ServerPlayer p = near.get(((t - 12) / 3) % near.size());
                        Vec3 aim = p.position().add(this.random.nextGaussian() * 1.5, 0.0, this.random.nextGaussian() * 1.5);
                        Vec3 from = aim.add(this.random.nextGaussian() * 2.5, 18.0, this.random.nextGaussian() * 2.5);
                        Missile m = new Missile(level, this);
                        m.setPos(from.x, from.y, from.z);
                        Vec3 dir = aim.subtract(from).normalize();
                        m.shoot(dir.x, dir.y, dir.z, 0.75F, 0.0F);
                        level.addFreshEntity(m);
                        level.playSound(null, from.x, from.y, from.z, ModSounds.MISSILE, net.minecraft.sounds.SoundSource.HOSTILE, 3.0F,
                                0.9F + this.random.nextFloat() * 0.2F);
                    }
                }
            }
            case GRAB -> {
                LivingEntity target = this.getTarget();
                if (t == 4) {
                    this.graspAt = null;
                    if (target instanceof ServerPlayer p && p.distanceTo(this) < 30.0) {
                        // it comes up where they stand now: running away in time is the way out
                        this.graspAt = p.position();
                        Grasp hand = ModEntities.GRASP.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                        if (hand != null) {
                            hand.snapTo(this.graspAt.x, this.graspAt.y, this.graspAt.z, this.random.nextFloat() * 360.0F, 0.0F);
                            level.addFreshEntity(hand);
                        }
                        level.playSound(null, this.graspAt.x, this.graspAt.y, this.graspAt.z, ModSounds.GRASP, net.minecraft.sounds.SoundSource.HOSTILE,
                                3.0F, 1.0F);
                    }
                }
                if (this.graspAt != null && t > 4 && t < 4 + Grasp.CLOSE && t % 2 == 0) {
                    level.sendParticles(ParticleTypes.SQUID_INK, this.graspAt.x, this.graspAt.y + 0.1, this.graspAt.z, 8, 0.7, 0.05, 0.7, 0.01);
                }
                if (this.graspAt != null && t == 4 + Grasp.CLOSE) {
                    Vec3 at = this.graspAt;
                    level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.8, at.z, 40, 0.5, 1.0, 0.5, 0.08);
                    for (ServerPlayer p : this.playersNear(level, 40.0)) {
                        double h = Math.sqrt(p.distanceToSqr(at.x, p.getY(), at.z));
                        if (h > 1.8 || p.getY() - at.y > 3.0 || p.getY() - at.y < -1.5) {
                            continue;
                        }
                        ServerPlayer partner = Bonds.partner(p);
                        Vec3 away = partner != null && partner.level() == level ? p.position().subtract(partner.position())
                                : p.position().subtract(this.position());
                        away = away.multiply(1, 0, 1);
                        away = away.lengthSqr() < 0.01 ? this.forward() : away.normalize();
                        this.strike(level, p, 4.0F);
                        fling(p, away.scale(1.6).add(0, 1.15, 0));
                        Couple couple = Bonds.coupleOf(p);
                        if (couple != null) {
                            Tether.cut(level.getServer(), couple, 20 * 8);
                        }
                        p.sendSystemMessage(Component.translatable("filorosso.msg.boss_grab").withStyle(ChatFormatting.DARK_RED), true);
                    }
                }
            }
            case BELL -> {
                if (t == 2) {
                    this.playSound(ModSounds.BOSS_BELL, 8.0F, 0.85F);
                }
                if (t == 10) {
                    this.playSound(ModSounds.BOSS_SCREAM, 6.0F, 1.0F);
                    this.lifted.clear();
                    for (ServerPlayer p : this.playersNear(level, 48.0)) {
                        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
                        // gravity turns upside down for a moment
                        p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, this.phase() == 3 ? 40 : 30, 2));
                        this.lifted.add(p);
                    }
                    if (this.phase() >= 2) {
                        int couples = Math.max(1, Boss.couplesOnline(level.getServer()));
                        List<ServerPlayer> near = this.playersNear(level, 40.0);
                        for (int i = 0; i < Math.min(8, couples * 2) && !near.isEmpty(); i++) {
                            Servants.spawnNear(level, near.get(i % near.size()), true);
                        }
                    }
                }
                if (t == (this.phase() == 3 ? 50 : 40)) {
                    // and back down, gently
                    for (ServerPlayer p : this.lifted) {
                        if (p.isAlive()) {
                            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0));
                        }
                    }
                    this.lifted.clear();
                }
                if (t > 10 && t < 44 && t % 4 == 0) {
                    level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 7.0, this.getZ(), 20, 3.0, 2.0, 3.0, 0.05);
                }
            }
            case BLINK -> {
                if (t == 1) {
                    level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 4.0, this.getZ(), 80, 1.0, 3.5, 1.0, 0.05);
                    this.playSound(ModSounds.BOSS_GLITCH, 4.0F, 0.7F);
                }
                LivingEntity target = this.getTarget();
                if (t == 8 && target != null) {
                    Vec3 dir = this.position().subtract(target.position()).multiply(1, 0, 1);
                    dir = dir.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : dir.normalize();
                    BlockPos at = BlockPos.containing(target.getX() + dir.x * 8.0, target.getY(), target.getZ() + dir.z * 8.0);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
                    if (Math.abs(y - target.getY()) > 10) {
                        y = (int) Math.floor(target.getY());
                    }
                    this.teleportTo(at.getX() + 0.5, y, at.getZ() + 0.5);
                    level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 4.0, this.getZ(), 80, 1.0, 3.5, 1.0, 0.05);
                    this.playSound(ModSounds.BOSS_GLITCH, 4.0F, 0.5F);
                }
            }
            default -> {
            }
        }
    }

    /** Turns its body and head towards something, at most {@code step} degrees this tick. */
    private void turnTowards(Entity e, float step) {
        float want = (float) (Mth.atan2(e.getZ() - this.getZ(), e.getX() - this.getX()) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(this.yBodyRot, want, step);
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    /**
     * One lash of the strings (side: 1 = its right, -1 = its left): everyone in front of it within
     * reach, on that side or straight ahead, is hit and thrown back.
     */
    private void lash(ServerLevel level, double side) {
        Vec3 fwd = this.forward();
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
        // the strings come down slanting across the front, so each lash covers the middle too
        Vec3 land = this.position().add(fwd.scale(4.3)).add(right.scale(side * 1.2));
        this.playSound(ModSounds.SNAP, 4.0F, side > 0 ? 0.7F : 0.6F);
        this.playSound(ModSounds.BOSS_SLAM, 2.5F, 1.2F);
        for (int i = 0; i < 6; i++) {
            Vec3 at = this.position().add(fwd.scale(2.0 + i * 0.75)).add(right.scale(side * (0.4 + i * 0.25)));
            level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.6, at.z, 6, 0.3, 0.3, 0.3, 0.05);
        }
        this.ground(level, land.x, land.z, 30, 1.2);
        // a lash is its own hit: both can catch the same couple (one partner each time)
        this.couplesHit.clear();
        for (ServerPlayer p : this.playersNear(level, LASH_REACH + 1.0)) {
            Vec3 to = p.position().subtract(this.position()).multiply(1, 0, 1);
            double ahead = to.dot(fwd);
            double across = to.dot(right) * side;
            if (ahead < 0.3 || ahead > LASH_REACH || across < -1.6 || Math.abs(to.dot(right)) > 3.2 || p.getY() - this.getY() > 4.5
                    || p.getY() - this.getY() < -2.0) {
                continue;
            }
            // the second lash comes three ticks after the first: it must not be eaten by the hurt cooldown
            p.damageCooldownTime = 0;
            this.strike(level, p, this.phase() == 3 ? 6.0F : 4.5F);
            Vec3 push = to.lengthSqr() < 0.01 ? fwd : to.normalize();
            fling(p, push.scale(1.0).add(right.scale(side * 0.35)).add(0, 0.45, 0));
        }
    }

    static void fling(ServerPlayer p, Vec3 v) {
        p.setDeltaMovement(v);
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        p.syncVelocity = true;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        return false;
    }
}
