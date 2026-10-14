package com.belowbedrock.entity;

import com.belowbedrock.haunt.Director;
import com.belowbedrock.registry.ModAdvancements;
import com.belowbedrock.registry.ModEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Him. A tall, pale man with black eyes who lives in the faded world. He is never saved with the
 * world and cannot be hurt: he appears, does one thing to one player, and is gone.
 *
 * <ul>
 *   <li>WATCH: stands far away, at the edge of the fog. Look at him for too long and he is gone.</li>
 *   <li>PEEK: at the corner of your eye. Turn and he is gone.</li>
 *   <li>STALK: only moves while you do not look at him. When he reaches you, turn around.</li>
 *   <li>MIMIC: in front of you, copying every step you take, mirrored.</li>
 *   <li>GIFT: holds something out for you. Get close and it is on the ground.</li>
 *   <li>CHASE: runs at you. Looking does not stop him.</li>
 *   <li>STILL: spawned with an egg or a command: he just stands there, staring.</li>
 * </ul>
 */
public class Inhabitant extends PathfinderMob {
    public enum Mode { WATCH, PEEK, STALK, MIMIC, GIFT, CHASE, STILL }

    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(Inhabitant.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_TILT = SynchedEntityData.defineId(Inhabitant.class, EntityDataSerializers.FLOAT);

    @Nullable
    private UUID targetId;
    private int life;
    private int maxLife = Integer.MAX_VALUE;
    private int seenTicks;
    private int closeTicks;
    private int vanishIn = -1;
    private boolean fromDirector;
    @Nullable
    private Vec3 lastTargetPos;
    private Vec3 lastOwnPos = Vec3.ZERO;
    private int stuckTicks;

    public Inhabitant(EntityType<? extends Inhabitant> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.29)
                .add(Attributes.FOLLOW_RANGE, 96.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    /** Puts Him in the world for {@code target}, facing them. */
    public static Inhabitant spawn(ServerLevel level, ServerPlayer target, Mode mode, Vec3 pos, int maxLife) {
        Inhabitant him = ModEntities.INHABITANT.create(level);
        if (him == null) {
            return null;
        }
        float yaw = yawTowards(pos, target.position());
        him.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        him.setYHeadRot(yaw);
        him.setYBodyRot(yaw);
        him.setMode(mode);
        him.targetId = target.getUUID();
        him.maxLife = maxLife;
        him.fromDirector = true;
        if (mode == Mode.WATCH) {
            him.setTilt((level.random.nextFloat() - 0.5F) * 0.7F);
        }
        level.addFreshEntity(him);
        if (mode == Mode.CHASE) {
            level.playSound(null, him, SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 2.5F, 0.45F);
        }
        return him;
    }

    private static float yawTowards(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MODE, (byte) Mode.STILL.ordinal());
        builder.define(DATA_TILT, 0.0F);
    }

    public Mode getMode() {
        return Mode.values()[Mth.clamp(this.entityData.get(DATA_MODE), 0, Mode.values().length - 1)];
    }

    public void setMode(Mode mode) {
        this.entityData.set(DATA_MODE, (byte) mode.ordinal());
    }

    /** Head tilt in radians (drawn by the model). */
    public float getTilt() {
        return this.entityData.get(DATA_TILT);
    }

    public void setTilt(float tilt) {
        this.entityData.set(DATA_TILT, tilt);
    }

    public boolean isTargeting(ServerPlayer player) {
        return player.getUUID().equals(this.targetId);
    }

    public void setGift(ItemStack gift) {
        this.setItemInHand(InteractionHand.MAIN_HAND, gift);
        this.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        this.setMode(Mode.STILL);
        return super.finalizeSpawn(level, difficulty, spawnType, data);
    }

    // ------------------------------------------------------------------ behaviour (server)

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.life++;
        if (this.vanishIn >= 0) {
            this.getNavigation().stop();
            if (this.vanishIn-- == 0) {
                this.vanish();
            }
            return;
        }
        ServerPlayer target = this.findTarget(level);
        if (target == null) {
            if (this.fromDirector || this.life > 200) {
                this.vanish();
            }
            return;
        }
        if (this.life > this.maxLife) {
            this.vanish();
            return;
        }
        this.getLookControl().setLookAt(target, 180.0F, 180.0F);
        this.setYBodyRot(this.getYHeadRot());
        double distance = this.distanceTo(target);
        switch (this.getMode()) {
            case WATCH -> this.watch(target, distance);
            case PEEK -> this.peek(target);
            case STALK -> this.stalk(target);
            case MIMIC -> this.mimic(target, distance);
            case GIFT -> this.gift(target, distance);
            case CHASE -> this.chase(target, distance);
            case STILL -> {
            }
        }
        this.lastTargetPos = target.position();
    }

    @Nullable
    private ServerPlayer findTarget(ServerLevel level) {
        if (this.targetId != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(this.targetId);
            if (player == null || player.level() != level || !player.isAlive() || player.isSpectator() || this.distanceTo(player) > 96.0F) {
                return null;
            }
            return player;
        }
        return level.getNearestPlayer(this, 48.0) instanceof ServerPlayer player && !player.isSpectator() ? player : null;
    }

    /** Whether {@code player} is looking at Him within the cone {@code cos} (1 = dead centre). */
    public boolean isWatchedBy(ServerPlayer player, double cos) {
        Vec3 eye = player.getEyePosition();
        Vec3 me = new Vec3(this.getX(), this.getY() + this.getBbHeight() * 0.7, this.getZ());
        Vec3 direction = me.subtract(eye);
        double length = direction.length();
        if (length < 1.0E-3) {
            return true;
        }
        return player.getViewVector(1.0F).dot(direction.scale(1.0 / length)) > cos && player.hasLineOfSight(this);
    }

    private void watch(ServerPlayer target, double distance) {
        if (this.isWatchedBy(target, 0.985)) {
            if (++this.seenTicks == 20) {
                ModAdvancements.award(target, ModAdvancements.SEEN_HIM);
            }
            if (this.seenTicks > 30) {
                this.vanishIn = 0;
            }
        } else {
            this.seenTicks = Math.max(0, this.seenTicks - 1);
        }
        if (distance < 13.0) {
            this.vanishIn = 0;
        }
    }

    private void peek(ServerPlayer target) {
        if (this.isWatchedBy(target, 0.93)) {
            ModAdvancements.award(target, ModAdvancements.SEEN_HIM);
            this.vanishIn = 2;
        }
    }

    /** Only moves while nobody is looking. When he is right behind you, turn around. */
    private void stalk(ServerPlayer target) {
        Vec3 toTarget = target.position().subtract(this.position());
        Vec3 flat = new Vec3(toTarget.x, 0.0, toTarget.z);
        double horizontal = flat.length();
        if (horizontal < 2.4 && Math.abs(toTarget.y) < 2.0) {
            this.getNavigation().stop();
            this.closeTicks++;
            if (this.isWatchedBy(target, 0.3) || this.closeTicks > 140) {
                Director.jumpscare(target, this, this.closeTicks > 140);
                this.vanishIn = 0;
            }
            return;
        }
        if (this.isWatchedBy(target, 0.5)) {
            this.getNavigation().stop();
            this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
            this.seenTicks++;
            return;
        }
        if (horizontal > 2.2) {
            this.move(MoverType.SELF, flat.scale(Math.min(0.3, horizontal - 2.2) / horizontal));
        }
        if (this.position().distanceToSqr(this.lastOwnPos) < 0.01) {
            this.stuckTicks++;
        } else {
            this.stuckTicks = 0;
        }
        this.lastOwnPos = this.position();
        // Stuck behind a hill, or still far away: when nobody is looking, he is simply closer. Always behind you.
        boolean stuck = this.stuckTicks > 20;
        if ((stuck || (horizontal > 14.0 && this.life % 50 == 0)) && this.level() instanceof ServerLevel level) {
            double distance = stuck ? 2.6 : Math.max(2.6, horizontal - 6.0);
            Vec3 closer = Director.spotBehind(level, target, distance, distance + 3.0);
            if (closer != null) {
                this.teleportTo(closer.x, closer.y, closer.z);
                this.lastOwnPos = closer;
            }
            this.stuckTicks = 0;
        }
    }

    /** In front of you, doing what you do, mirrored. */
    private void mimic(ServerPlayer target, double distance) {
        if (distance < 6.0) {
            this.vanishIn = 0;
            return;
        }
        this.setPose(target.isCrouching() ? Pose.CROUCHING : Pose.STANDING);
        if (this.lastTargetPos != null) {
            Vec3 moved = target.position().subtract(this.lastTargetPos);
            Vec3 normal = new Vec3(this.getX() - target.getX(), 0.0, this.getZ() - target.getZ());
            if (normal.lengthSqr() > 1.0E-4 && moved.lengthSqr() < 4.0) {
                normal = normal.normalize();
                Vec3 flat = new Vec3(moved.x, 0.0, moved.z);
                Vec3 mirrored = flat.subtract(normal.scale(2.0 * flat.dot(normal)));
                this.move(MoverType.SELF, mirrored);
            }
            if (moved.y > 0.2 && this.onGround()) {
                this.getJumpControl().jump();
            }
        }
    }

    private void gift(ServerPlayer target, double distance) {
        if (this.isWatchedBy(target, 0.97)) {
            this.seenTicks++;
        }
        if (distance < 8.0 || this.seenTicks > 50) {
            ItemStack gift = this.getMainHandItem().copy();
            if (!gift.isEmpty()) {
                ItemEntity item = new ItemEntity(this.level(), this.getX(), this.getY() + 0.3, this.getZ(), gift, 0.0, 0.1, 0.0);
                item.setNoPickUpDelay();
                this.level().addFreshEntity(item);
                this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            this.vanishIn = 0;
        }
    }

    /** Looking does not stop him now. */
    private void chase(ServerPlayer target, double distance) {
        if (distance < 1.5) {
            Director.caught(target, this);
            this.vanishIn = 0;
            return;
        }
        if (this.life % 5 == 1) {
            this.getNavigation().moveTo(target, 1.0);
        }
    }

    /** Gone, as if he had never been there. */
    public void vanish() {
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            level.sendParticles(ParticleTypes.ASH, this.getX(), this.getY() + 1.1, this.getZ(), 30, 0.3, 0.8, 0.3, 0.0);
        }
        this.discard();
    }

    // ------------------------------------------------------------------ he cannot be hurt

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (!this.level().isClientSide && source.getEntity() instanceof ServerPlayer player && this.vanishIn < 0) {
            // He stops, tilts his head, and is gone.
            this.setTilt(0.55F);
            this.vanishIn = 12;
            Director.hitHim(player, this);
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // no footsteps
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    /** He glides while stalking: no walking animation. */
    @Override
    public void calculateEntityAnimation(boolean flying) {
        if (this.getMode() == Mode.STALK) {
            this.walkAnimation.setSpeed(0.0F);
            this.walkAnimation.update(0.0F, 0.4F);
            return;
        }
        super.calculateEntityAnimation(flying);
    }
}
