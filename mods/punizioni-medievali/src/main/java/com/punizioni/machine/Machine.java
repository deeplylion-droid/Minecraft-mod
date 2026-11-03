package com.punizioni.machine;

import com.punizioni.ModSounds;
import com.punizioni.entity.DraftHorse;
import com.punizioni.entity.Rat;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A punishment machine: placed like an armour stand, facing the one who places it. Whoever stands in
 * its slot when the executioner works it (right click) is caught: they ride the machine, which holds
 * them in place and in pose until the punishment ends or the executioner lets them go. While someone
 * is inside, clicks go through the machine to them (and back to the machine, see Punizioni). Crouch
 * and hit it twice to take it down.
 *
 * <p>The state and when it began are synced: both sides play the same animation from the same game
 * time (the client's game time follows the server's).
 */
public abstract class Machine extends Entity {
    public static final int IDLE = 0;

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(Machine.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_STATE_TIME = SynchedEntityData.defineId(Machine.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_VICTIM = SynchedEntityData.defineId(Machine.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_OPERATOR = SynchedEntityData.defineId(Machine.class, EntityDataSerializers.INT);
    /** What each machine needs on both sides: how far the horses pulled, how high the fire is... */
    private static final EntityDataAccessor<Float> DATA_VALUE = SynchedEntityData.defineId(Machine.class, EntityDataSerializers.FLOAT);
    /** And a number: the limb on the chopping block. */
    private static final EntityDataAccessor<Integer> DATA_CHOICE = SynchedEntityData.defineId(Machine.class, EntityDataSerializers.INT);
    /** Whose remains stay in the machine (the head in the basket, the hanged body): their UUID, for the skin. */
    private static final EntityDataAccessor<String> DATA_REMAINS = SynchedEntityData.defineId(Machine.class, EntityDataSerializers.STRING);

    private @Nullable UUID operator;
    private boolean capturing;
    private int breakHits;
    private long lastBreakHit;

    protected Machine(EntityType<? extends Machine> type, Level level) {
        super(type, level);
    }

    public abstract MachineKind kind();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, IDLE);
        builder.define(DATA_STATE_TIME, 0L);
        builder.define(DATA_VICTIM, -1);
        builder.define(DATA_OPERATOR, -1);
        builder.define(DATA_VALUE, 0.0F);
        builder.define(DATA_CHOICE, 0);
        builder.define(DATA_REMAINS, "");
    }

    // ------------------------------------------------------------------ state

    public int state() {
        return this.entityData.get(DATA_STATE);
    }

    /** Ticks since the current state began (with the partial tick, for drawing). */
    public float stateAge(float partial) {
        return Math.max(0.0F, this.level().getGameTime() - this.entityData.get(DATA_STATE_TIME) + partial);
    }

    public int stateTicks() {
        return (int) Math.max(0L, this.level().getGameTime() - this.entityData.get(DATA_STATE_TIME));
    }

    protected void setState(int state) {
        this.entityData.set(DATA_STATE, state);
        this.entityData.set(DATA_STATE_TIME, this.level().getGameTime());
    }

    public float value() {
        return this.entityData.get(DATA_VALUE);
    }

    protected void setValue(float v) {
        this.entityData.set(DATA_VALUE, v);
    }

    public int choice() {
        return this.entityData.get(DATA_CHOICE);
    }

    protected void setChoice(int c) {
        this.entityData.set(DATA_CHOICE, c);
    }

    /** Whose remains are in the machine now (null: nobody's). */
    public @Nullable UUID remains() {
        String s = this.entityData.get(DATA_REMAINS);
        if (s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected void setRemains(@Nullable Entity owner) {
        this.entityData.set(DATA_REMAINS, owner != null ? owner.getUUID().toString() : "");
    }

    public @Nullable LivingEntity victim() {
        int id = this.entityData.get(DATA_VICTIM);
        return id >= 0 && this.level().getEntity(id) instanceof LivingEntity l ? l : null;
    }

    public int victimId() {
        return this.entityData.get(DATA_VICTIM);
    }

    /** The executioner who set the machine going (server: by UUID; clients: by id). */
    public @Nullable Player operator() {
        if (this.level() instanceof ServerLevel level) {
            return this.operator != null && level.getEntity(this.operator) instanceof Player p ? p : null;
        }
        int id = this.entityData.get(DATA_OPERATOR);
        return id >= 0 && this.level().getEntity(id) instanceof Player p ? p : null;
    }

    protected void setOperator(@Nullable Player player) {
        this.operator = player != null ? player.getUUID() : null;
        this.entityData.set(DATA_OPERATOR, player != null ? player.getId() : -1);
    }

    // ------------------------------------------------------------------ space

    /** Which way the machine looks (the victim's front for most machines). */
    public Vec3 forward() {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    /** Its right hand side, looking the way it faces. */
    public Vec3 right() {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
    }

    /** A point in the machine's own frame: blocks to its right, up, forward. */
    public Vec3 local(double right, double up, double forward) {
        Vec3 f = this.forward();
        Vec3 r = this.right();
        return new Vec3(this.getX() + r.x * right + f.x * forward, this.getY() + up, this.getZ() + r.z * right + f.z * forward);
    }

    /** Where the victim's feet are held, in the machine's frame (right, up, forward). */
    protected abstract Vec3 slot();

    /** The victim's body turns this many degrees from the way the machine looks. */
    protected float victimYaw() {
        return 0.0F;
    }

    /** How far the victim can turn their head from where the body looks. */
    protected float headFreedom() {
        return 70.0F;
    }

    /** Half the size of the space where someone gets caught, around the slot (sideways, up). */
    protected Vec3 catchSize() {
        return new Vec3(0.6, 2.0, 0.6);
    }

    public Vec3 slotPosition() {
        Vec3 s = this.slot();
        return this.local(s.x, s.y, s.z);
    }

    // ------------------------------------------------------------------ catching

    /** Whoever stands in the slot (the nearest one), or null. */
    protected @Nullable LivingEntity findVictim(@Nullable Entity except) {
        Vec3 c = this.slotPosition();
        Vec3 h = this.catchSize();
        AABB box = new AABB(c.x - h.x, c.y - 0.6, c.z - h.x, c.x + h.x, c.y + h.y, c.z + h.x).inflate(0.0, 0.0, 0.0);
        List<LivingEntity> list = this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != except && e.isAlive() && !e.isSpectator()
                && !(e instanceof DraftHorse) && !(e instanceof Rat) && !(e.getVehicle() instanceof Machine));
        return list.stream().min(Comparator.comparingDouble(e -> e.position().distanceToSqr(c))).orElse(null);
    }

    protected boolean capture(LivingEntity victim) {
        if (victim.isPassenger()) {
            victim.stopRiding();
        }
        this.capturing = true;
        boolean ok = victim.startRiding(this, true, true);
        this.capturing = false;
        if (ok) {
            this.entityData.set(DATA_VICTIM, victim.getId());
            this.playSound(ModSounds.CHAINS, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        }
        return ok;
    }

    /** Lets the victim go (alive), a step in front of the machine. */
    public void release() {
        LivingEntity v = this.victim();
        this.entityData.set(DATA_VICTIM, -1);
        if (v != null && v.getVehicle() == this) {
            v.stopRiding();
        }
    }

    public boolean holds(Entity e) {
        return e.getVehicle() == this;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.capturing && this.getPassengers().isEmpty();
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        Vec3 p = this.slotPosition();
        move.accept(passenger, p.x, p.y, p.z);
        if (passenger instanceof LivingEntity l) {
            float yaw = this.getYRot() + this.victimYaw();
            l.setYBodyRot(yaw);
            l.yBodyRotO = yaw;
            this.clampHead(l);
        }
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        this.clampHead(passenger);
    }

    private void clampHead(Entity passenger) {
        float body = this.getYRot() + this.victimYaw();
        float rel = Mth.wrapDegrees(passenger.getYRot() - body);
        float max = this.headFreedom();
        float clamped = Mth.clamp(rel, -max, max);
        if (clamped != rel) {
            passenger.yRotO += clamped - rel;
            passenger.setYRot(body + clamped);
        }
        passenger.setYHeadRot(passenger.getYRot());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 p = this.local(0.0, 0.05, this.dismountDistance());
        return this.level().noCollision(passenger, passenger.getBoundingBox().move(p.subtract(passenger.position()))) ? p : this.position().add(0.0, 1.0, 0.0);
    }

    protected double dismountDistance() {
        return 1.3;
    }

    // ------------------------------------------------------------------ use and break

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND || player.getVehicle() == this || player.isSpectator() || this.ignoresClickWith(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer sp) || !(this.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        return this.use(level, sp, player.getItemInHand(hand));
    }

    /** A right click holding this goes on to the item (the dung is thrown at the pillory, it does not open it). */
    public boolean ignoresClickWith(ItemStack stack) {
        return false;
    }

    /** The executioner's right click (on the machine, or on whoever is held in it). */
    protected abstract InteractionResult use(ServerLevel level, ServerPlayer player, ItemStack stack);

    /** Catches this victim and starts (the first right click), the executioner may be nobody. */
    protected abstract boolean startWith(ServerLevel level, LivingEntity victim, @Nullable ServerPlayer player);

    /** The idle right click: whoever is in the slot gets caught. */
    protected void catchSomeone(ServerLevel level, ServerPlayer player) {
        LivingEntity victim = this.findVictim(player);
        if (victim == null) {
            tell(player, "punizioni.msg.nobody", Component.translatable(this.kind().item().getDescriptionId()));
        } else {
            this.startWith(level, victim, player);
        }
    }

    /** /punizione: brings the victim into the slot and starts. */
    public boolean commandStart(ServerLevel level, LivingEntity victim, @Nullable ServerPlayer player) {
        if (this.state() != IDLE || this.victimId() >= 0) {
            return false;
        }
        Vec3 at = this.slotPosition();
        victim.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), this.getYRot() + this.victimYaw(), 0.0F, true);
        return this.startWith(level, victim, player);
    }

    /** /punizione avanza: the next step, as if the executioner had clicked (with the right tool). */
    public void commandAdvance(ServerLevel level, ServerPlayer player) {
        this.use(level, player, ItemStack.EMPTY);
    }

    /** /punizione libera. */
    public void commandRelease() {
        this.release();
        this.setState(IDLE);
    }

    protected static void tell(@Nullable ServerPlayer player, String key) {
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(key));
        }
    }

    /** While someone is inside, clicks reach them, not the machine. */
    @Override
    public boolean isPickable() {
        return !this.isRemoved() && this.victimId() < 0;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.isRemoved() || this.isInvulnerableToBase(source)) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            this.breakMachine(level, false);
            return true;
        }
        if (!(source.getEntity() instanceof Player p) || source.getDirectEntity() != p) {
            return false;
        }
        if (!p.isShiftKeyDown()) {
            p.sendOverlayMessage(Component.translatable("punizioni.msg.break_hint"));
            return false;
        }
        if (p.getAbilities().instabuild) {
            this.breakMachine(level, true);
            return true;
        }
        long now = level.getGameTime();
        if (now - this.lastBreakHit > 10) {
            this.breakHits = 0;
        }
        this.lastBreakHit = now;
        if (++this.breakHits >= 2) {
            this.breakMachine(level, false);
        } else {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_HIT, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
        return true;
    }

    protected void breakMachine(ServerLevel level, boolean creative) {
        this.release();
        this.onRemovedMachine(level);
        if (!creative) {
            this.spawnAtLocation(level, this.getPickResult());
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DARK_OAK_PLANKS.defaultBlockState()),
                this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(), 40, this.getBbWidth() * 0.3, this.getBbHeight() * 0.3,
                this.getBbWidth() * 0.3, 0.05);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
        this.discard();
    }

    /** Clean up what the machine brought (horses, rats, light). */
    protected void onRemovedMachine(ServerLevel level) {
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(this.kind().item());
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (!this.onGround() || this.getDeltaMovement().lengthSqr() > 1.0E-6) {
            this.applyGravity();
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 0.98, 0.5));
        }
        if (this.level() instanceof ServerLevel level) {
            // the victim got away somehow (teleport, logout, respawn): the machine lets go
            int id = this.victimId();
            if (id >= 0) {
                LivingEntity v = this.victim();
                if (v == null || (v.getVehicle() != this && !v.isDeadOrDying())) {
                    this.entityData.set(DATA_VICTIM, -1);
                }
            }
            this.serverTick(level);
        } else {
            this.clientTick();
        }
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    protected abstract void serverTick(ServerLevel level);

    protected void clientTick() {
    }

    // ------------------------------------------------------------------ helpers

    protected void sound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** A scream from the victim, muffled or not. */
    protected void scream(LivingEntity victim, boolean muffled, float volume) {
        this.level().playSound(null, victim.getX(), victim.getEyeY(), victim.getZ(), muffled ? ModSounds.SCREAM_MUFFLED : ModSounds.SCREAM,
                SoundSource.PLAYERS, volume, 0.9F + this.random.nextFloat() * 0.25F);
    }

    protected static void tell(@Nullable ServerPlayer player, String key, Object... args) {
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(key, args));
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0 * 96.0;
    }
}
