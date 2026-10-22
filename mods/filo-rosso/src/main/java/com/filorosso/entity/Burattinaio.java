package com.filorosso.entity;

import com.filorosso.data.Couple;
import com.filorosso.server.Bonds;
import com.filorosso.sound.ModSounds;
import com.filorosso.world.Puppets;
import com.filorosso.world.Stage;
import com.filorosso.world.Theatre;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Il Burattinaio: who held Recisione's strings. A tall figure in a black coat that hangs from the
 * dark above the stage and never touches the boards: a porcelain mask with three empty red eyes,
 * very long arms, a marionette control in each hand. Strings rise from its head into the dark: it is
 * a puppet too. Its own attack is the strings that command: it ties someone's limbs and walks them
 * like a marionette, against their own partner, until the partner cuts the knot. Only couples really
 * hurt it.
 */
public class Burattinaio extends Monster implements BondBoss {
    public static final int NONE = 0;
    /** "I fili che comandano": the right control goes up, the strings whip down at someone, three tugs. */
    public static final int CONTROL = 1;
    /** It drops onto the boards and its hands crash down; then it stays low for a moment. */
    public static final int DESCENT = 2;
    /** Arms up: red threads fall from the dark like spears, where the red circles are. */
    public static final int RAIN = 3;
    /** The lights go out; when they come back it is somewhere else, and the stalls are not empty. */
    public static final int DARK = 4;
    /** Coming down from the dark at the start of the show. */
    public static final int ARRIVE = 5;
    /** It glitches away and back next to its target. */
    public static final int BLINK = 6;

    private static final int[] DURATION = {0, 48, 44, 56, 70, 100, 16};
    /** When the strings reach the puppet (attack1's flick, 1.1 s). */
    public static final int CONTROL_HIT = 22;
    /** When the hands crash on the boards. */
    public static final int SLAM = 20;
    public static final String[] ACTION_NAMES = {"", "fili", "calata", "pioggia", "buio", "", "salto"};
    /** Height the descent drops it (blocks), for the renderer and the reach of the slam. */
    public static final float DROP = 5.0F;

    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(Burattinaio.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_START = SynchedEntityData.defineId(Burattinaio.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(Burattinaio.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(UUID.randomUUID(), glitchName(-1), BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarOverlay.NOTCHED_10);
    private int controlCooldown = 60;
    private int descentCooldown = 100;
    private int rainCooldown = 140;
    private int darkCooldown = 500;
    private int farTicks;
    private int hintCooldown;
    private boolean lastCombo;
    private long lassoedUntil;
    private Couple kissedBy;
    private final Set<UUID> hitThisAction = new HashSet<>();
    /** Where the descent crashes down. */
    private Vec3 slamAt;
    private int localActionStart;
    private int localPhaseStart;

    public Burattinaio(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        this.setPersistenceRequired();
        this.setNoGravity(true);
        this.noPhysics = true;
        this.bossEvent.setDarkenScreen(true);
        this.bossEvent.setCreateWorldFog(true);
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 900.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    /** "IL BURATTINAIO" with one letter that will not stay still. */
    public static Component glitchName(int glitch) {
        String name = "IL BURATTINAIO";
        MutableComponent out = Component.empty();
        for (int i = 0; i < name.length(); i++) {
            MutableComponent c = Component.literal(String.valueOf(name.charAt(i)));
            if (i == glitch && name.charAt(i) != ' ') {
                c = c.withStyle(ChatFormatting.OBFUSCATED);
            }
            out.append(c);
        }
        return out.withStyle(ChatFormatting.WHITE);
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
    }

    // ------------------------------------------------------------------ state shared with the renderer

    public static int duration(int action) {
        return action >= 0 && action < DURATION.length ? DURATION[action] : 0;
    }

    public int action() {
        return this.entityData.get(DATA_ACTION);
    }

    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ACTION.equals(key) || DATA_ACTION_START.equals(key)) {
            this.localActionStart = this.tickCount;
        }
        if (DATA_PHASE.equals(key)) {
            this.localPhaseStart = this.tickCount;
        }
    }

    public float actionTime(float partial) {
        return this.tickCount - this.localActionStart + partial;
    }

    public float phaseTime(float partial) {
        return this.tickCount - this.localPhaseStart + partial;
    }

    private void start(int action) {
        this.entityData.set(DATA_ACTION, action);
        this.entityData.set(DATA_ACTION_START, this.tickCount);
        this.localActionStart = this.tickCount;
        this.hitThisAction.clear();
    }

    /** /filo burattinaio attacco: this attack now (not while it arrives or dies). */
    public void force(int action) {
        if (this.action() != ARRIVE && !this.isDeadOrDying()) {
            if (this.getTarget() == null && this.level() instanceof ServerLevel level) {
                this.setTarget(this.pickTarget(level));
            }
            this.start(action);
        }
    }

    /** Called when it is summoned: it comes down from the dark. */
    public void arrive() {
        this.start(ARRIVE);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Phase", this.phase());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_PHASE, input.getIntOr("Phase", 1));
    }

    // ------------------------------------------------------------------ damage

    @Override
    public void lassoed(long until, Couple couple) {
        this.lassoedUntil = until;
    }

    @Override
    public void kissed(Couple couple) {
        this.kissedBy = couple;
    }

    private boolean stunned() {
        float t = this.actionTime(0.0F);
        return this.action() == DESCENT && t >= SLAM && t < SLAM + 16;
    }

    @Override
    public float adjustDamage(float amount, boolean combo, Couple couple) {
        if (this.kissedBy != null) {
            combo = true;
            couple = couple != null ? couple : this.kissedBy;
            this.kissedBy = null;
        }
        this.lastCombo = combo;
        if (this.action() == ARRIVE || this.isDeadOrDying()) {
            return 0.0F;
        }
        // after crashing down it is open for a moment
        float open = this.stunned() ? 1.5F : 1.0F;
        if (combo) {
            if (couple != null) {
                couple.bossHits++;
            }
            return amount * open;
        }
        return amount * 0.12F * open;
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
        return ModSounds.PUPPETEER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.PUPPETEER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.PUPPETEER_DEATH;
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
            this.start(NONE);
            Puppets.freeAll(level.getServer());
            Stage.onDeath(level, this);
        }
    }

    /**
     * Its own strings snap: it drops onto the boards like a puppet, folds over, the mask comes off
     * (the model's death animation, 72 ticks); then it falls to black dust.
     */
    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.level() instanceof ServerLevel level) {
            if (this.deathTime == 7) {
                this.playSound(ModSounds.SNAP, 5.0F, 0.5F);
                this.playSound(ModSounds.SNAP, 5.0F, 0.7F);
                level.sendParticles(new DustParticleOptions(0xD8202A, 2.0F), this.getX(), this.getY() + 15.0, this.getZ(), 40, 0.6, 2.0, 0.6, 0.0);
            }
            if (this.deathTime == 17) {
                this.playSound(ModSounds.BOSS_SLAM, 5.0F, 0.5F);
                BlockPos below = BlockPos.containing(this.getX(), this.getY() - 0.5, this.getZ());
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), this.getX(), this.getY() + 0.2, this.getZ(), 60,
                        2.5, 0.2, 2.5, 0.15);
            }
            if (this.deathTime == 34) {
                this.playSound(ModSounds.MASK_FALL, 4.0F, 1.0F);
            }
            if (this.deathTime > 72 && this.deathTime % 2 == 0) {
                level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 1.0, this.getZ(), 10, 1.8, 0.6, 1.8, 0.02);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 3, 1.6, 0.5, 1.6, 0.01);
            }
            if (this.deathTime >= 96) {
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
        Stage.adopt(this);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.tickCount % 19 == 0) {
            this.bossEvent.setName(glitchName(this.random.nextInt(3) == 0 ? -1 : this.random.nextInt(14)));
        }
        this.hintCooldown--;
        int phase = this.getHealth() <= this.getMaxHealth() * 0.33F ? 3 : this.getHealth() <= this.getMaxHealth() * 0.66F ? 2 : 1;
        if (phase > this.phase() && this.action() != ARRIVE) {
            this.entityData.set(DATA_PHASE, phase);
            Stage.onPhase(level, this, phase);
            this.start(DARK);
        }
        int action = this.action();
        float t = this.actionTime(0.0F);
        if (action != NONE) {
            this.act(level, action, (int) t);
            if (t >= DURATION[action]) {
                this.start(NONE);
            }
            return;
        }
        LivingEntity target = this.pickTarget(level);
        this.setTarget(target);
        if (target == null) {
            this.glide(Theatre.BOSS_HOME, 0.08);
            return;
        }
        double d = Math.sqrt(this.distanceToSqr(target.getX(), this.getY(), target.getZ()));
        this.getLookControl().setLookAt(target, 20.0F, 20.0F);
        this.turnTowards(target, 6.0F);
        float rate = phase == 3 ? 1.7F : phase == 2 ? 1.3F : 1.0F;
        this.controlCooldown -= rate;
        this.descentCooldown -= rate;
        this.rainCooldown -= rate;
        if (phase >= 2) {
            this.darkCooldown -= rate;
        }
        if (this.controlCooldown <= 0 && d < 30.0 && Puppets.count(level.getServer()) < (phase == 3 ? 2 : 1)) {
            this.controlCooldown = 170;
            this.start(CONTROL);
        } else if (this.descentCooldown <= 0 && d < 12.0) {
            this.descentCooldown = 110;
            this.start(DESCENT);
        } else if (this.rainCooldown <= 0) {
            this.rainCooldown = 220;
            this.start(RAIN);
        } else if (this.darkCooldown <= 0) {
            this.darkCooldown = 700;
            this.start(DARK);
        } else {
            // it keeps a few blocks away: its strings reach far
            Vec3 to = this.position().subtract(target.position()).multiply(1, 0, 1);
            to = to.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : to.normalize();
            Vec3 want = target.position().add(to.scale(7.0));
            this.glide(want, this.speed(phase));
        }
        this.farTicks = d > 26.0 ? this.farTicks + 1 : 0;
        if (this.farTicks > 100) {
            this.farTicks = 0;
            this.start(BLINK);
        }
    }

    private double speed(int phase) {
        double s = phase == 3 ? 0.2 : phase == 2 ? 0.16 : 0.12;
        boolean tied = this.level().getServer() != null && this.level().getServer().overworld().getGameTime() < this.lassoedUntil;
        return tied ? s * 0.4 : s;
    }

    /** Floats towards a point on the stage (or over the stalls), never touching anything. */
    private void glide(Vec3 to, double speed) {
        double x = Mth.clamp(to.x, -19.0, 19.0);
        double z = Mth.clamp(to.z, -46.0, 9.0);
        double y = Theatre.STAGE;
        Vec3 want = new Vec3(x, y, z).subtract(this.position());
        double d = want.length();
        Vec3 v = d < 0.05 ? Vec3.ZERO : want.scale(Math.min(speed, d) / d);
        this.setDeltaMovement(v);
        this.move(net.minecraft.world.entity.MoverType.SELF, v);
    }

    private void turnTowards(Entity e, float step) {
        float want = (float) (Mth.atan2(e.getZ() - this.getZ(), e.getX() - this.getX()) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(this.yBodyRot, want, step);
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    private LivingEntity pickTarget(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof ServerPlayer p && valid(p) && this.distanceTo(p) < 48.0 && this.tickCount % 80 != 0) {
            return current;
        }
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (!valid(p)) {
                continue;
            }
            double d = this.distanceTo(p) - (Bonds.coupleOf(p) != null ? 8.0 : 0.0);
            if (d < bestD && this.distanceTo(p) < 64.0) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static boolean valid(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative();
    }

    public List<ServerPlayer> playersNear(ServerLevel level, double radius) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (valid(p) && p.distanceTo(this) < radius) {
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
            case ARRIVE -> {
                // down from the dark, slowly, to its place above the boards
                double y = Theatre.STAGE + 28.0 * (1.0 - smooth(t / 80.0));
                this.setPos(this.getX(), y, this.getZ());
                this.setDeltaMovement(Vec3.ZERO);
                if (t == 1) {
                    this.playSound(ModSounds.PUPPETEER_AMBIENT, 6.0F, 0.6F);
                }
                if (t == 76) {
                    this.playSound(ModSounds.SNAP, 4.0F, 0.5F);
                }
            }
            case CONTROL -> {
                LivingEntity target = this.getTarget();
                if (target != null && t < CONTROL_HIT) {
                    this.turnTowards(target, 14.0F);
                }
                if (t == 4) {
                    this.playSound(ModSounds.PUPPET_STRINGS, 3.0F, 0.7F);
                }
                if (t == CONTROL_HIT) {
                    int victims = this.phase() == 3 ? 2 : 1;
                    for (ServerPlayer victim : Puppets.chooseVictims(level, this, victims)) {
                        Puppets.start(level, this, victim, this.phase() == 1 ? 140 : 170);
                    }
                }
            }
            case DESCENT -> {
                LivingEntity target = this.getTarget();
                if (t == 0) {
                    this.slamAt = null;
                    this.playSound(ModSounds.PUPPETEER_AMBIENT, 4.0F, 1.3F);
                }
                if (target != null && t < 14) {
                    // over them, fast
                    this.turnTowards(target, 20.0F);
                    Vec3 behind = target.position().subtract(this.forward().scale(2.0));
                    this.glide(behind, 0.45);
                }
                if (t == SLAM) {
                    this.slam(level);
                }
            }
            case RAIN -> {
                if (t == 4) {
                    this.playSound(ModSounds.PUPPET_STRINGS, 4.0F, 0.5F);
                }
                int every = this.phase() == 3 ? 4 : 6;
                if (t >= 10 && t <= 40 && (t - 10) % every == 0) {
                    for (ServerPlayer p : this.playersNear(level, 48.0)) {
                        // where they will be: a little ahead of where they are going
                        Vec3 aim = p.position().add(p.getDeltaMovement().multiply(10, 0, 10))
                                .add(this.random.nextGaussian() * 1.4, 0.0, this.random.nextGaussian() * 1.4);
                        ThreadSpear.drop(level, this, aim);
                        if (this.phase() >= 2 && this.random.nextInt(2) == 0) {
                            ThreadSpear.drop(level, this, aim.add(this.random.nextGaussian() * 4.0, 0.0, this.random.nextGaussian() * 4.0));
                        }
                    }
                }
            }
            case DARK -> {
                if (t == 1) {
                    this.playSound(ModSounds.GHOST_OFF, 6.0F, 0.7F);
                    for (ServerPlayer p : this.playersNear(level, 96.0)) {
                        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 90, 0, false, false));
                        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
                    }
                    Stage.darkness(level, true);
                }
                if (t == 24) {
                    // somewhere else when the light comes back, behind someone
                    List<ServerPlayer> near = this.playersNear(level, 96.0);
                    if (!near.isEmpty()) {
                        ServerPlayer p = near.get(this.random.nextInt(near.size()));
                        Vec3 back = p.position().subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(7.0));
                        double x = Mth.clamp(back.x, -19.0, 19.0);
                        double z = Mth.clamp(back.z, -46.0, 9.0);
                        this.teleportTo(x, Theatre.STAGE, z);
                        this.turnTowards(p, 360.0F);
                    }
                    Stage.wakeSpectators(level, this.phase() == 3 ? 3 : 2);
                }
                if (t == 60) {
                    Stage.darkness(level, false);
                    this.playSound(ModSounds.PUPPETEER_AMBIENT, 5.0F, 0.8F);
                }
            }
            case BLINK -> {
                if (t == 1) {
                    level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 8.0, this.getZ(), 80, 1.0, 6.0, 1.0, 0.05);
                    this.playSound(ModSounds.BOSS_GLITCH, 4.0F, 0.6F);
                }
                LivingEntity target = this.getTarget();
                if (t == 8 && target != null) {
                    Vec3 dir = this.position().subtract(target.position()).multiply(1, 0, 1);
                    dir = dir.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : dir.normalize();
                    double x = Mth.clamp(target.getX() + dir.x * 8.0, -19.0, 19.0);
                    double z = Mth.clamp(target.getZ() + dir.z * 8.0, -46.0, 9.0);
                    this.teleportTo(x, Theatre.STAGE, z);
                    level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 8.0, this.getZ(), 80, 1.0, 6.0, 1.0, 0.05);
                    this.playSound(ModSounds.BOSS_GLITCH, 4.0F, 0.45F);
                }
            }
            default -> {
            }
        }
    }

    private static double smooth(double k) {
        k = Mth.clamp(k, 0.0, 1.0);
        return k * k * (3.0 - 2.0 * k);
    }

    /** The descent: both hands crash on the boards in front of it, a ring of splinters. */
    private void slam(ServerLevel level) {
        Vec3 at = this.position().add(this.forward().scale(2.5));
        this.slamAt = at;
        this.playSound(ModSounds.BOSS_SLAM, 5.0F, 0.6F);
        this.playSound(ModSounds.SNAP, 3.0F, 0.6F);
        BlockPos below = BlockPos.containing(at.x, this.getY() - 0.5, at.z);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), at.x, this.getY() + 0.2, at.z, 80, 2.5, 0.2, 2.5, 0.2);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, this.getY() + 0.5, at.z, 2, 1.0, 0.2, 1.0, 0.0);
        float damage = this.phase() == 3 ? 10.0F : 8.0F;
        for (ServerPlayer p : this.playersNear(level, 16.0)) {
            double h = Math.sqrt(p.distanceToSqr(at.x, p.getY(), at.z));
            if (h > 6.0 || p.getY() - this.getY() > 3.0) {
                continue;
            }
            p.hurtServer(level, this.damageSources().mobAttack(this), damage * (h < 3.0 ? 1.0F : 0.6F));
            Vec3 away = p.position().subtract(at).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? this.forward() : away.normalize();
            Recisione.fling(p, away.scale(1.1).add(0, 0.55, 0));
        }
    }

    /** Where its right control is, about, while it pulls someone's strings (for the puppets). */
    public Vec3 controlPosition() {
        Vec3 fwd = this.forward();
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
        return this.position().add(fwd.scale(2.6)).add(right.scale(-1.6)).add(0.0, 8.8, 0.0);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        return false;
    }

    /** Sound for everyone in the Theatre. */
    public void announce(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, this.getX(), this.getY() + 8.0, this.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }
}
