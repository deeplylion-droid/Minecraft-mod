package com.rainbowyoyo.entity;

import com.rainbowyoyo.RainbowYoyoMod;
import com.rainbowyoyo.client.ClientHooks;
import com.rainbowyoyo.config.YoyoConfig;
import com.rainbowyoyo.item.YoyoItem;
import com.rainbowyoyo.motion.V3;
import com.rainbowyoyo.motion.YoyoMotion;
import com.rainbowyoyo.motion.YoyoPhase;
import com.rainbowyoyo.network.YoyoActionPacket;
import com.rainbowyoyo.registry.ModDamageTypes;
import com.rainbowyoyo.registry.ModEntities;
import com.rainbowyoyo.registry.ModSounds;
import com.rainbowyoyo.util.Rainbow;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

/**
 * The thrown yo-yo.
 *
 * <p>The server owns the state machine (see {@link YoyoPhase}) and sends only the phase, its
 * start and a few anchors through synced entity data. Server and clients then compute the very
 * same motion every tick with {@link YoyoMotion}, relative to the owner's hand, and clients draw
 * it at any partial tick: smooth tricks for every player in multiplayer, with very little traffic.
 * Only the server hits things.</p>
 *
 * <p>Controls (holding the yo-yo in the main hand): hold use to throw it and keep it spinning at the
 * end of the string, release to call it back; attack throws a quick shot, or does a trick while
 * the yo-yo is out: looking ahead Loop the Loop, looking up Shoot the Moon, looking down Walk the
 * Dog, sneaking Around the World. The four tricks in one combo unlock the Supernova.</p>
 */
public class YoyoEntity extends Entity {
    public static final byte EVENT_HIT = 100;
    public static final byte EVENT_SLAM = 101;
    public static final byte EVENT_NOVA = 102;
    public static final byte EVENT_READY = 103;

    /** The use key counts as held for this long after the last use packet (vanilla repeats it every 4 ticks). */
    private static final int HOLD_TICKS = 8;
    /** A trick started within this many ticks of the previous one continues the combo. */
    private static final int COMBO_WINDOW = 60;
    /** Ticks an entity is safe from the yo-yo after a hit (vanilla invulnerability lasts 10). */
    private static final int HIT_COOLDOWN = 10;

    private static final EntityDataAccessor<Integer> DATA_OWNER =
            SynchedEntityData.defineId(YoyoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_PHASE =
            SynchedEntityData.defineId(YoyoEntity.class, EntityDataSerializers.BYTE);
    /** Bumped at every phase change, so clients restart the motion even for the same phase twice. */
    private static final EntityDataAccessor<Integer> DATA_SEQ =
            SynchedEntityData.defineId(YoyoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_YAW =
            SynchedEntityData.defineId(YoyoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH =
            SynchedEntityData.defineId(YoyoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DIST =
            SynchedEntityData.defineId(YoyoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LENGTH =
            SynchedEntityData.defineId(YoyoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> DATA_TARGET =
            SynchedEntityData.defineId(YoyoEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Byte> DATA_COMBO =
            SynchedEntityData.defineId(YoyoEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_NOVA_READY =
            SynchedEntityData.defineId(YoyoEntity.class, EntityDataSerializers.BOOLEAN);

    /** Yo-yos out, by owner. The server map is only touched by the server thread, the client map by the client. */
    private static final Map<UUID, YoyoEntity> SERVER_ACTIVE = new HashMap<>();
    private static final Map<Integer, YoyoEntity> CLIENT_ACTIVE = new HashMap<>();

    // ---- both sides
    private YoyoPhase phase = YoyoPhase.THROW;
    private int phaseTicks;
    private int appliedSeq = -1;
    private V3 startRel = V3.ZERO;
    @Nullable
    private V3 pos;
    private V3 prevPos = V3.ZERO;
    /** Velocity of the spring that pulls the sleeping yo-yo after the crosshair. */
    private V3 vel = V3.ZERO;
    private boolean clipped;
    private boolean prevClipped;
    private boolean failed;

    // ---- server
    @Nullable
    private UUID ownerUuid;
    private int seq;
    private long holdUntil;
    @Nullable
    private YoyoPhase queued;
    private int combo;
    private int comboMask;
    private long lastTrickEnd = Long.MIN_VALUE / 2;
    private final Map<Integer, Integer> hitUntil = new HashMap<>();

    // ---- client (visuals only)
    public float spin;
    public float prevSpin;
    /** Smoothed axle direction used by the renderer. */
    @Nullable
    public Vector3f renderAxle;
    public long renderAxleNanos;
    /** Recent render positions for the rainbow trail: {x, y, z, millis}. */
    public final ArrayDeque<double[]> trail = new ArrayDeque<>();
    private boolean soundStarted;
    private boolean announcedNova;

    public YoyoEntity(EntityType<? extends YoyoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_OWNER, -1);
        this.entityData.define(DATA_PHASE, (byte) YoyoPhase.THROW.ordinal());
        this.entityData.define(DATA_SEQ, 0);
        this.entityData.define(DATA_YAW, 0.0F);
        this.entityData.define(DATA_PITCH, 0.0F);
        this.entityData.define(DATA_DIST, 8.0F);
        this.entityData.define(DATA_LENGTH, 8.0F);
        this.entityData.define(DATA_TARGET, new Vector3f());
        this.entityData.define(DATA_COMBO, (byte) 0);
        this.entityData.define(DATA_NOVA_READY, false);
    }

    // =====================================================================================
    // Owner lookup and entry points
    // =====================================================================================

    @Nullable
    public Player getOwner() {
        Entity entity = level().getEntity(this.entityData.get(DATA_OWNER));
        return entity instanceof Player player && player.isAlive() ? player : null;
    }

    /** The yo-yo the entity has out, as seen by this client, or null. */
    @Nullable
    public static YoyoEntity clientYoyoOf(Entity owner) {
        YoyoEntity yoyo = CLIENT_ACTIVE.get(owner.getId());
        if (yoyo != null && (yoyo.isRemoved() || yoyo.level() != owner.level())) {
            CLIENT_ACTIVE.remove(owner.getId());
            return null;
        }
        return yoyo;
    }

    public static void clearClient() {
        CLIENT_ACTIVE.clear();
    }

    @Nullable
    private static YoyoEntity serverYoyoOf(Player owner) {
        YoyoEntity yoyo = SERVER_ACTIVE.get(owner.getUUID());
        if (yoyo != null && (yoyo.isRemoved() || yoyo.level() != owner.level())) {
            SERVER_ACTIVE.remove(owner.getUUID());
            if (!yoyo.isRemoved()) {
                yoyo.discard();
            }
            return null;
        }
        return yoyo;
    }

    /** Use key (server): throw the yo-yo, or keep it out. Called again every 4 ticks while held. */
    public static void onUse(ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }
        YoyoEntity yoyo = serverYoyoOf(player);
        if (yoyo == null) {
            yoyo = spawn(player, YoyoPhase.THROW);
        } else if (yoyo.phase == YoyoPhase.SHOT || yoyo.phase == YoyoPhase.RETURN) {
            yoyo.beginPhase(YoyoPhase.SLEEP, player); // grab it back onto the string
        }
        yoyo.holdUntil = player.level().getGameTime() + HOLD_TICKS;
    }

    /** Key presses sent by the client (server). */
    public static void onAction(ServerPlayer player, byte action) {
        if (player.isSpectator() || !(player.getMainHandItem().getItem() instanceof YoyoItem)) {
            return;
        }
        YoyoEntity yoyo = serverYoyoOf(player);
        if (action == YoyoActionPacket.ATTACK) {
            if (yoyo == null) {
                spawn(player, YoyoPhase.SHOT);
            } else {
                yoyo.requestTrick(player);
            }
        } else if (action == YoyoActionPacket.RELEASE && yoyo != null) {
            yoyo.holdUntil = 0;
        }
    }

    private static YoyoEntity spawn(ServerPlayer owner, YoyoPhase phase) {
        YoyoEntity yoyo = new YoyoEntity(ModEntities.YOYO.get(), owner.level());
        yoyo.ownerUuid = owner.getUUID();
        yoyo.entityData.set(DATA_OWNER, owner.getId());
        yoyo.entityData.set(DATA_LENGTH, YoyoConfig.SERVER.stringLength.get().floatValue());
        V3 hand = handAnchor(owner, 1.0F);
        yoyo.setPos(hand.x(), hand.y(), hand.z());
        yoyo.pos = hand;
        yoyo.prevPos = hand;
        yoyo.beginPhase(phase, owner);
        owner.level().addFreshEntity(yoyo);
        SERVER_ACTIVE.put(owner.getUUID(), yoyo);
        return yoyo;
    }

    /** Where the yo-yo hangs from: just below the eyes, at the owner's right. */
    public static V3 handAnchor(Player owner, float partialTick) {
        Vec3 eye = owner.getEyePosition(partialTick);
        V3 right = V3.right(owner.getViewYRot(partialTick));
        return new V3(eye.x, eye.y - 0.45, eye.z).add(right.scale(0.32));
    }

    // =====================================================================================
    // Ticking
    // =====================================================================================

    @Override
    public void tick() {
        super.tick();
        Player owner = getOwner();
        if (owner == null) {
            if (!level().isClientSide) {
                discard();
            }
            return;
        }
        if (failed) {
            return;
        }
        if (pos == null) {
            pos = new V3(getX(), getY(), getZ());
            prevPos = pos;
        }
        try {
            if (level().isClientSide) {
                clientTick(owner);
            } else {
                serverTick(owner);
            }
        } catch (RuntimeException e) {
            // Never take the game down with the yo-yo: log it once and drop it.
            failed = true;
            RainbowYoyoMod.LOGGER.error("Rainbow yo-yo failed, removing it", e);
            if (!level().isClientSide) {
                discard();
            }
        }
    }

    /** Moves the yo-yo one tick along its phase (same code on both sides). */
    private void move(Player owner) {
        phaseTicks++;
        prevPos = pos;
        prevClipped = clipped;
        V3 hand = handAnchor(owner, 1.0F);
        YoyoMotion.Context c = context(owner, 1.0F, phaseTicks);
        V3 next;
        if (phase == YoyoPhase.SLEEP) {
            V3 target = YoyoMotion.sleepTarget(c);
            vel = vel.scale(0.55).add(target.sub(pos).scale(0.35));
            next = pos.add(vel);
        } else {
            next = YoyoMotion.position(c);
        }
        if (!next.isFinite()) {
            next = hand;
        }
        next = clipToWorld(hand, next);
        if (clipped && phase == YoyoPhase.SLEEP) {
            vel = next.sub(pos);
        }
        pos = next;
        setPos(pos.x(), pos.y(), pos.z());
    }

    private void serverTick(Player owner) {
        long now = level().getGameTime();
        if (owner.level() != level() || owner.isSpectator()
                || pos.sub(handAnchor(owner, 1.0F)).lengthSqr() > 48 * 48) {
            discard();
            return;
        }
        boolean holdsYoyo = owner.getMainHandItem().getItem() instanceof YoyoItem;
        if (!holdsYoyo && phase != YoyoPhase.RETURN && phase != YoyoPhase.SHOT) {
            beginPhase(YoyoPhase.RETURN, owner);
        }
        boolean holding = holdsYoyo && now < holdUntil;

        move(owner);
        double radius = YoyoMotion.RADIUS * YoyoMotion.scale(context(owner, 1.0F, phaseTicks)) + 0.1;
        hitAlong(prevPos, pos, radius, owner);
        if (tickCount % 40 == 0) {
            hitUntil.values().removeIf(until -> until < tickCount);
        }

        switch (phase) {
            case SHOT -> {
                if (phaseTicks >= YoyoPhase.SHOT.duration) {
                    catchYoyo(owner);
                }
            }
            case THROW -> {
                if (phaseTicks >= YoyoPhase.THROW.duration) {
                    if (queued != null && holding) {
                        startTrick(queued, owner);
                    } else {
                        beginPhase(YoyoPhase.SLEEP, owner);
                    }
                }
            }
            case SLEEP -> {
                if (!holding) {
                    queued = null;
                    beginPhase(YoyoPhase.RETURN, owner);
                } else if (queued != null) {
                    startTrick(queued, owner);
                }
            }
            case RETURN -> {
                if (phaseTicks >= YoyoMotion.returnDuration(startRel)) {
                    catchYoyo(owner);
                }
            }
            default -> trickTick(owner, holding, now);
        }
    }

    private void trickTick(Player owner, boolean holding, long now) {
        if (phase == YoyoPhase.SHOOT_THE_MOON && phaseTicks == YoyoMotion.MOON_IMPACT_TICK) {
            blast(owner, 3.5, 0.55);
            level().broadcastEntityEvent(this, EVENT_SLAM);
            playSound(ModSounds.SLAM.get(), 2.0F, 0.9F + random.nextFloat() * 0.2F);
        } else if (phase == YoyoPhase.SUPERNOVA && phaseTicks == 10) {
            playSound(ModSounds.CHARGE.get(), 1.5F, 1.0F);
        } else if (phase == YoyoPhase.SUPERNOVA && phaseTicks == YoyoMotion.NOVA_BOOM_TICK) {
            blast(owner, 7.0, 0.9);
            level().broadcastEntityEvent(this, EVENT_NOVA);
            playSound(ModSounds.BOOM.get(), 3.0F, 1.0F);
        }
        if (phaseTicks >= phase.duration) {
            lastTrickEnd = now;
            YoyoPhase next = queued;
            queued = null;
            if (next != null && holding) {
                startTrick(next, owner);
            } else {
                beginPhase(holding ? YoyoPhase.SLEEP : YoyoPhase.RETURN, owner);
            }
        }
    }

    private void clientTick(Player owner) {
        int seqNow = this.entityData.get(DATA_SEQ);
        if (seqNow != appliedSeq) {
            appliedSeq = seqNow;
            YoyoPhase next = YoyoPhase.byId(this.entityData.get(DATA_PHASE));
            applyPhase(next, handAnchor(owner, 1.0F));
            if (next.trick && owner.isLocalPlayer()) {
                int shownCombo = this.entityData.get(DATA_COMBO);
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.announceTrick(next, shownCombo));
            }
        }
        move(owner);
        CLIENT_ACTIVE.put(owner.getId(), this);

        // Spin: fast while sleeping and in tricks; rolling like a wheel when walking the dog.
        prevSpin = spin;
        double rate = YoyoMotion.spinRate(context(owner, 1.0F, phaseTicks));
        if (phase == YoyoPhase.WALK_THE_DOG) {
            rate = pos.sub(prevPos).dot(V3.forward(this.entityData.get(DATA_YAW))) / YoyoMotion.RADIUS;
        }
        spin += (float) rate;
        if (Math.abs(spin) > 1000.0F) {
            float wrap = (float) (Math.PI * 2 * Math.floor(spin / (Math.PI * 2)));
            spin -= wrap;
            prevSpin -= wrap;
        }

        if (!soundStarted) {
            soundStarted = true;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.startSpinSound(this));
        }
        boolean ready = this.entityData.get(DATA_NOVA_READY);
        if (ready && !announcedNova && owner.isLocalPlayer()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::announceNovaReady);
        }
        announcedNova = ready;
        if (YoyoConfig.CLIENT.particles.get()) {
            spawnParticles();
        }
    }

    // =====================================================================================
    // Phases
    // =====================================================================================

    /** Server: switches phase and tells the clients. */
    private void beginPhase(YoyoPhase next, Player owner) {
        V3 hand = handAnchor(owner, 1.0F);
        this.entityData.set(DATA_PHASE, (byte) next.ordinal());
        this.entityData.set(DATA_YAW, owner.getYRot());
        this.entityData.set(DATA_PITCH, owner.getXRot());
        if (next == YoyoPhase.SHOT) {
            this.entityData.set(DATA_DIST, (float) shotDistance(owner, hand));
        } else if (next == YoyoPhase.SHOOT_THE_MOON) {
            this.entityData.set(DATA_TARGET, toVector3f(slamPoint(owner, hand)));
        } else if (next == YoyoPhase.SUPERNOVA) {
            this.entityData.set(DATA_TARGET, toVector3f(novaPoint(owner, hand)));
        }
        seq++;
        this.entityData.set(DATA_SEQ, seq);
        appliedSeq = seq;
        applyPhase(next, hand);
        if (next == YoyoPhase.SHOT || next == YoyoPhase.THROW) {
            playSound(ModSounds.THROW.get(), 1.0F, next == YoyoPhase.SHOT ? 1.15F : 0.95F);
        }
    }

    /** Both sides: restarts the motion from where the yo-yo is now. */
    private void applyPhase(YoyoPhase next, V3 hand) {
        V3 here = pos != null ? pos : hand;
        vel = next == YoyoPhase.SLEEP && pos != null ? pos.sub(prevPos) : V3.ZERO;
        phase = next;
        phaseTicks = 0;
        startRel = here.sub(hand);
    }

    private void requestTrick(Player owner) {
        YoyoPhase trick = chooseTrick(owner);
        if (phase == YoyoPhase.SLEEP) {
            startTrick(trick, owner);
        } else if (phase == YoyoPhase.THROW || phase.trick) {
            queued = trick; // runs as soon as the current move ends
        }
    }

    private YoyoPhase chooseTrick(Player owner) {
        if (this.entityData.get(DATA_NOVA_READY)) {
            return YoyoPhase.SUPERNOVA;
        }
        if (owner.isShiftKeyDown()) {
            return YoyoPhase.AROUND_THE_WORLD;
        }
        float pitch = owner.getXRot();
        if (pitch < -35.0F) {
            return YoyoPhase.SHOOT_THE_MOON;
        }
        if (pitch > 35.0F) {
            return YoyoPhase.WALK_THE_DOG;
        }
        return YoyoPhase.LOOP_THE_LOOP;
    }

    private void startTrick(YoyoPhase trick, Player owner) {
        queued = null;
        long now = level().getGameTime();
        if (!phase.trick && now - lastTrickEnd > COMBO_WINDOW) {
            combo = 0;
            comboMask = 0;
        }
        combo = Math.min(combo + 1, 99);
        if (trick == YoyoPhase.SUPERNOVA) {
            comboMask = 0;
            this.entityData.set(DATA_NOVA_READY, false);
        } else {
            comboMask |= trick.bit();
            int all = YoyoPhase.basicTricksMask();
            if ((comboMask & all) == all && !this.entityData.get(DATA_NOVA_READY)) {
                this.entityData.set(DATA_NOVA_READY, true);
                level().broadcastEntityEvent(this, EVENT_READY);
                playSound(ModSounds.READY.get(), 1.2F, 1.0F);
            }
        }
        this.entityData.set(DATA_COMBO, (byte) combo);
        beginPhase(trick, owner);
        playSound(ModSounds.TRICK.get(), 1.0F, 0.9F + 0.06F * Math.min(combo, 10));
    }

    private void catchYoyo(Player owner) {
        level().playSound(null, owner.getX(), owner.getY() + 1.0, owner.getZ(), ModSounds.CATCH.get(),
                SoundSource.PLAYERS, 0.8F, 0.95F + random.nextFloat() * 0.2F);
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!level().isClientSide && ownerUuid != null) {
            SERVER_ACTIVE.remove(ownerUuid, this);
        }
    }

    @Override
    public void onClientRemoval() {
        super.onClientRemoval();
        CLIENT_ACTIVE.values().removeIf(yoyo -> yoyo == this);
    }

    // =====================================================================================
    // World queries
    // =====================================================================================

    private YoyoMotion.Context context(Player owner, float partialTick, double t) {
        YoyoMotion.Context c = new YoyoMotion.Context();
        c.phase = phase;
        c.t = t;
        c.hand = handAnchor(owner, partialTick);
        Vec3 look = owner.getViewVector(partialTick);
        c.look = new V3(look.x, look.y, look.z);
        c.yaw0 = this.entityData.get(DATA_YAW);
        c.pitch0 = this.entityData.get(DATA_PITCH);
        c.startRel = startRel;
        Vector3f target = this.entityData.get(DATA_TARGET);
        c.target = new V3(target.x(), target.y(), target.z());
        c.dist = this.entityData.get(DATA_DIST);
        c.length = this.entityData.get(DATA_LENGTH);
        c.ground = this::surfaceBelow;
        return c;
    }

    /** Keeps the string out of walls: the yo-yo stops where the line from the hand meets a block. */
    private V3 clipToWorld(V3 from, V3 to) {
        clipped = false;
        V3 dir = to.sub(from);
        double length = dir.length();
        if (length < 1e-4) {
            return to;
        }
        BlockHitResult hit = clip(from, to);
        if (hit.getType() == HitResult.Type.MISS) {
            return to;
        }
        clipped = true;
        double d = Math.max(0.0, hit.getLocation().distanceTo(vec(from)) - YoyoMotion.RADIUS * 0.8);
        return from.add(dir.scale(d / length));
    }

    private BlockHitResult clip(V3 from, V3 to) {
        return level().clip(new ClipContext(vec(from), vec(to), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
    }

    /** Top of the ground (or water) below a point, NaN if there is none within 8 blocks. */
    private double surfaceBelow(double x, double fromY, double z) {
        BlockHitResult hit = level().clip(new ClipContext(new Vec3(x, fromY, z), new Vec3(x, fromY - 8.0, z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        return hit.getType() == HitResult.Type.MISS ? Double.NaN : hit.getLocation().y;
    }

    private double shotDistance(Player owner, V3 hand) {
        double max = this.entityData.get(DATA_LENGTH) + 2.0;
        Vec3 look = owner.getViewVector(1.0F);
        V3 end = hand.add(new V3(look.x, look.y, look.z).scale(max));
        BlockHitResult hit = clip(hand, end);
        double d = hit.getType() == HitResult.Type.MISS ? max
                : hit.getLocation().distanceTo(vec(hand)) - YoyoMotion.RADIUS * 0.8;
        return Math.max(1.0, d);
    }

    /** Ground point 5.5 blocks ahead where Shoot the Moon lands. */
    private V3 slamPoint(Player owner, V3 hand) {
        V3 p = hand.add(V3.forward(owner.getYRot()).scale(5.5));
        double y = surfaceBelow(p.x(), hand.y() + 1.0, p.z());
        if (Double.isNaN(y)) {
            y = hand.y() - 1.25;
        }
        return new V3(p.x(), y + YoyoMotion.RADIUS, p.z());
    }

    /** Where the Supernova blows up: 5 blocks ahead, 2.5 up, closer if a wall is in the way. */
    private V3 novaPoint(Player owner, V3 hand) {
        V3 want = hand.add(V3.forward(owner.getYRot()).scale(5.0)).add(0, 2.5, 0);
        BlockHitResult hit = clip(hand, want);
        if (hit.getType() == HitResult.Type.MISS) {
            return want;
        }
        double d = Math.max(0.0, hit.getLocation().distanceTo(vec(hand)) - 1.5);
        return hand.add(want.sub(hand).normalize(V3.UP).scale(d));
    }

    // =====================================================================================
    // Hitting
    // =====================================================================================

    /** Hits everything the yo-yo swept through this tick. */
    private void hitAlong(V3 from, V3 to, double radius, Player owner) {
        Vec3 a = vec(from);
        Vec3 b = vec(to);
        AABB area = new AABB(a, b).inflate(radius + 0.5);
        for (Entity target : level().getEntities(this, area, entity -> canHit(entity, owner))) {
            AABB box = target.getBoundingBox().inflate(radius);
            if (box.contains(b) || box.clip(a, b).isPresent()) {
                Vec3 push = b.subtract(a);
                if (push.lengthSqr() < 1.0E-4) {
                    push = target.position().subtract(b);
                }
                hit(target, owner, push, 1.0);
            }
        }
    }

    /** Hits everything around the yo-yo, flinging it away and up. */
    private void blast(Player owner, double radius, double lift) {
        Vec3 center = vec(pos);
        for (Entity target : level().getEntities(this, new AABB(center, center).inflate(radius),
                entity -> canHit(entity, owner))) {
            Vec3 middle = target.position().add(0, target.getBbHeight() * 0.5, 0);
            if (middle.distanceTo(center) > radius + target.getBbWidth() * 0.5) {
                continue;
            }
            hitUntil.remove(target.getId()); // a blast always lands
            if (hit(target, owner, middle.subtract(center), 1.6) && target instanceof LivingEntity) {
                target.setDeltaMovement(target.getDeltaMovement().add(0, lift, 0));
                target.hurtMarked = true;
            }
        }
    }

    private boolean canHit(Entity entity, Player owner) {
        if (entity == owner || entity instanceof YoyoEntity || !entity.isAlive() || entity.isSpectator()
                || !entity.isPickable() || entity == owner.getVehicle() || entity.isPassengerOfSameVehicle(owner)) {
            return false;
        }
        if (entity instanceof PartEntity<?> part) {
            return part.getParent() != owner;
        }
        if (!(entity instanceof LivingEntity) && !(entity instanceof EndCrystal)) {
            return false; // no smashing boats, minecarts, item frames or paintings
        }
        if (entity instanceof TamableAnimal pet && pet.isOwnedBy(owner)) {
            return false;
        }
        return !(entity instanceof Player other) || owner.canHarmPlayer(other);
    }

    private boolean hit(Entity target, Player owner, Vec3 push, double strength) {
        Integer until = hitUntil.get(target.getId());
        if (until != null && until > tickCount) {
            return false;
        }
        hitUntil.put(target.getId(), tickCount + HIT_COOLDOWN);
        float damage = YoyoConfig.SERVER.damage.get().floatValue();
        if (!target.hurt(damageSource(owner), damage)) {
            return false;
        }
        if (target instanceof LivingEntity living && push.lengthSqr() > 1.0E-6) {
            Vec3 dir = push.normalize();
            living.knockback(0.9 * strength, -dir.x, -dir.z);
        }
        level().broadcastEntityEvent(this, EVENT_HIT);
        playSound(ModSounds.HIT.get(), 1.2F, 0.85F + random.nextFloat() * 0.3F);
        return true;
    }

    private DamageSource damageSource(Player owner) {
        Optional<Holder.Reference<DamageType>> type =
                level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(ModDamageTypes.YOYO);
        if (type.isPresent()) {
            return new DamageSource(type.get(), this, owner);
        }
        return damageSources().playerAttack(owner);
    }

    // =====================================================================================
    // Client effects
    // =====================================================================================

    private void spawnParticles() {
        double speed = pos.sub(prevPos).length();
        double hue = (tickCount * 0.05) % 1.0;
        if (speed > 0.25 || phase.trick) {
            for (int i = 0; i < 2; i++) {
                level().addParticle(dust(hue + random.nextDouble() * 0.2, 1.4F),
                        pos.x() + (random.nextDouble() - 0.5) * 1.1, pos.y() + (random.nextDouble() - 0.5) * 1.1,
                        pos.z() + (random.nextDouble() - 0.5) * 1.1, 0.0, 0.0, 0.0);
            }
        }
        if (phase == YoyoPhase.WALK_THE_DOG && phaseTicks > 6 && phaseTicks < 30) {
            for (int i = 0; i < 2; i++) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x(), pos.y() - YoyoMotion.RADIUS + 0.05, pos.z(),
                        (random.nextDouble() - 0.5) * 0.4, 0.12 + random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.4);
            }
        }
        if (phase == YoyoPhase.SHOOT_THE_MOON && phaseTicks >= 8 && phaseTicks < YoyoMotion.MOON_IMPACT_TICK) {
            level().addParticle(ParticleTypes.END_ROD, pos.x(), pos.y(), pos.z(),
                    (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3);
        }
        if (phase == YoyoPhase.SUPERNOVA && phaseTicks >= 10 && phaseTicks < YoyoMotion.NOVA_BOOM_TICK) {
            // A vortex of light falling into the growing yo-yo.
            for (int i = 0; i < 4; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double b = (random.nextDouble() - 0.5) * Math.PI;
                double dx = Math.cos(a) * Math.cos(b);
                double dy = Math.sin(b);
                double dz = Math.sin(a) * Math.cos(b);
                double r = 3.5;
                level().addParticle(ParticleTypes.END_ROD, pos.x() + dx * r, pos.y() + dy * r, pos.z() + dz * r,
                        -dx * 0.25 - dz * 0.1, -dy * 0.25, -dz * 0.25 + dx * 0.1);
            }
        }
        if (this.entityData.get(DATA_NOVA_READY) && tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.FIREWORK, pos.x() + (random.nextDouble() - 0.5),
                    pos.y() + (random.nextDouble() - 0.5), pos.z() + (random.nextDouble() - 0.5), 0.0, 0.02, 0.0);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (pos == null) {
            pos = new V3(getX(), getY(), getZ());
        }
        switch (id) {
            case EVENT_HIT -> {
                for (int i = 0; i < 18; i++) {
                    level().addParticle(dust(random.nextDouble(), 2.0F), pos.x(), pos.y(), pos.z(),
                            (random.nextDouble() - 0.5) * 2, (random.nextDouble() - 0.5) * 2, (random.nextDouble() - 0.5) * 2);
                }
                for (int i = 0; i < 10; i++) {
                    level().addParticle(ParticleTypes.CRIT, pos.x(), pos.y(), pos.z(),
                            (random.nextDouble() - 0.5) * 1.2, random.nextDouble() * 0.8, (random.nextDouble() - 0.5) * 1.2);
                }
            }
            case EVENT_SLAM -> {
                double y = pos.y() - YoyoMotion.RADIUS + 0.1;
                for (int i = 0; i < 48; i++) {
                    double a = Math.PI * 2 * i / 48;
                    double c = Math.cos(a);
                    double s = Math.sin(a);
                    level().addParticle(ParticleTypes.CLOUD, pos.x() + c * 0.6, y, pos.z() + s * 0.6, c * 0.7, 0.02, s * 0.7);
                    level().addParticle(dust(i / 48.0, 2.5F), pos.x() + c * 1.5, y + 0.2, pos.z() + s * 1.5, 0, 0, 0);
                }
                level().addParticle(ParticleTypes.SONIC_BOOM, pos.x(), y + 0.5, pos.z(), 0, 0, 0);
                level().addParticle(ParticleTypes.EXPLOSION, pos.x(), y + 0.5, pos.z(), 0, 0, 0);
            }
            case EVENT_NOVA -> {
                level().createFireworks(pos.x(), pos.y(), pos.z(), 0, 0, 0, fireworks());
                level().addParticle(ParticleTypes.FLASH, pos.x(), pos.y(), pos.z(), 0, 0, 0);
                for (int i = 0; i < 60; i++) {
                    level().addParticle(dust(random.nextDouble(), 3.0F), pos.x(), pos.y(), pos.z(),
                            (random.nextDouble() - 0.5) * 3, (random.nextDouble() - 0.5) * 3, (random.nextDouble() - 0.5) * 3);
                }
            }
            case EVENT_READY -> {
                for (int i = 0; i < 24; i++) {
                    level().addParticle(ParticleTypes.FIREWORK, pos.x(), pos.y(), pos.z(),
                            (random.nextDouble() - 0.5) * 0.5, random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.5);
                }
            }
            default -> super.handleEntityEvent(id);
        }
    }

    private static DustParticleOptions dust(double hue, float size) {
        int rgb = Rainbow.rgb(hue);
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F,
                (rgb & 255) / 255.0F), size);
    }

    /** A large rainbow ball and a star, with trails and twinkles. */
    private static CompoundTag fireworks() {
        CompoundTag tag = new CompoundTag();
        ListTag explosions = new ListTag();
        explosions.add(explosion(1, new int[]{0xFF2020, 0xFF9020, 0xFFF020, 0x30FF40, 0x30A0FF, 0xA040FF},
                new int[]{0xFFFFFF}, true, true));
        explosions.add(explosion(2, new int[]{0xFF60FF, 0x60FFFF, 0xFFFF60}, new int[]{0xFFB0FF}, true, false));
        tag.put("Explosions", explosions);
        return tag;
    }

    private static CompoundTag explosion(int type, int[] colors, int[] fadeColors, boolean trail, boolean flicker) {
        CompoundTag explosion = new CompoundTag();
        explosion.putByte("Type", (byte) type);
        explosion.putIntArray("Colors", colors);
        explosion.putIntArray("FadeColors", fadeColors);
        explosion.putBoolean("Trail", trail);
        explosion.putBoolean("Flicker", flicker);
        return explosion;
    }

    // =====================================================================================
    // Rendering helpers (client)
    // =====================================================================================

    /** Smooth position at a partial tick: the exact motion when possible, else between ticks. */
    public Vec3 renderPosition(float partialTick) {
        Player owner = getOwner();
        if (pos == null || owner == null) {
            return getPosition(partialTick);
        }
        if (phase != YoyoPhase.SLEEP && !clipped && !prevClipped && !failed) {
            V3 p = YoyoMotion.position(context(owner, partialTick, phaseTicks - 1 + partialTick));
            if (p.isFinite()) {
                return vec(p);
            }
        }
        return vec(prevPos.lerp(pos, partialTick));
    }

    public float renderScale(float partialTick) {
        Player owner = getOwner();
        return owner == null ? 1.0F : (float) YoyoMotion.scale(context(owner, partialTick, phaseTicks - 1 + partialTick));
    }

    public float renderGlow(float partialTick) {
        Player owner = getOwner();
        float glow = owner == null ? 0.0F : (float) YoyoMotion.glow(context(owner, partialTick, phaseTicks - 1 + partialTick));
        if (this.entityData.get(DATA_NOVA_READY)) {
            glow = Math.max(glow, 0.6F + 0.4F * (float) Math.sin((tickCount + partialTick) * 0.5));
        }
        return glow;
    }

    public float renderSpin(float partialTick) {
        return prevSpin + (spin - prevSpin) * partialTick;
    }

    public YoyoPhase getPhase() {
        return phase;
    }

    /** Owner's yaw when the current phase started. */
    public float getPhaseYaw() {
        return this.entityData.get(DATA_YAW);
    }

    /** How loose the string looks (0 = taut). */
    public float renderSlack(float partialTick) {
        Player owner = getOwner();
        return owner == null ? 0.0F : (float) YoyoMotion.slack(context(owner, partialTick, phaseTicks - 1 + partialTick));
    }

    /** 0 to 1: drives the pitch and volume of the spinning sound. */
    public float soundIntensity() {
        double speed = pos == null ? 0 : pos.sub(prevPos).length();
        double spinPart = phase == YoyoPhase.RETURN ? 0.3 : phase.trick ? 0.75 : 0.55;
        return (float) YoyoMotion.clamp(spinPart + speed * 0.15, 0, 1);
    }

    // =====================================================================================
    // Entity plumbing
    // =====================================================================================

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.PLAYERS;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    private static Vec3 vec(V3 v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    private static Vector3f toVector3f(V3 v) {
        return new Vector3f((float) v.x(), (float) v.y(), (float) v.z());
    }
}
