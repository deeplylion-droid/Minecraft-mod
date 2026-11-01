package com.punizioni.machine;

import com.punizioni.ModDamage;
import com.punizioni.ModSounds;
import com.punizioni.data.Body;
import com.punizioni.data.ModData;
import com.punizioni.entity.DraftHorse;
import com.punizioni.entity.ModEntities;
import com.punizioni.net.Net;
import com.punizioni.server.Gore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Quartering: a low wooden rack with an iron ring at each corner. Whoever lies on it gets wrists and
 * ankles roped to four black horses, which gallop in from far away. When the executioner gives the
 * word the horses pull outwards, step by step, and the body stretches like rubber (RackGeometry);
 * then they strain at full stretch, rearing. The executioner decides: right click again to stop them
 * (the victim walks away stretched for a minute), or let them reach the end, and arms and legs tear
 * off and fly away.
 */
public class QuarteringRack extends Machine {
    public static final int TIED = 1;
    public static final int READY = 2;
    public static final int PULLING = 3;
    public static final int TENSION = 4;
    public static final int TORN = 5;
    public static final int STOPPED = 6;

    public static final int ARRIVE_TICKS = 70;
    public static final int PULL_TICKS = 160;
    public static final int TENSION_TICKS = 70;
    public static final int LEAVE_TICKS = 60;
    /** How far from its limb a horse stands, ropes just taut; how much further it gets at full stretch. */
    public static final double HORSE_DISTANCE = 4.4;
    public static final double HORSE_PULL = 1.8;

    private final List<UUID> horses = new ArrayList<>();

    public QuarteringRack(EntityType<? extends QuarteringRack> type, Level level) {
        super(type, level);
    }

    @Override
    public MachineKind kind() {
        return MachineKind.QUARTERING_RACK;
    }

    /** The hips, on top of the rack: the body lies towards the back from here. */
    @Override
    protected Vec3 slot() {
        return new Vec3(0.0, RackGeometry.TOP, RackGeometry.HIP_Z);
    }

    @Override
    protected float headFreedom() {
        return 40.0F;
    }

    @Override
    protected Vec3 catchSize() {
        return new Vec3(1.1, 2.0, 1.1);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    /** How far the horses have stretched the body (0 to 1), for both sides. */
    public float stretch(float partial) {
        float t = this.stateAge(partial);
        return switch (this.state()) {
            case PULLING -> {
                float k = Mth.clamp(t / PULL_TICKS, 0.0F, 1.0F);
                yield k * k * (3.0F - 2.0F * k);
            }
            case TENSION -> 1.0F;
            case STOPPED -> this.value();
            default -> 0.0F;
        };
    }

    @Override
    protected InteractionResult use(ServerLevel level, ServerPlayer player, ItemStack stack) {
        switch (this.state()) {
            case IDLE -> this.catchSomeone(level, player);
            case TIED -> tell(player, "punizioni.msg.horses_coming");
            case READY -> {
                if (player.isShiftKeyDown()) {
                    this.stop(level);
                } else {
                    this.setState(PULLING);
                    this.sound(ModSounds.LASH, 2.0F, 0.8F);
                    this.sound(ModSounds.DRUMS, 2.2F, 0.9F);
                    this.forHorses(level, h -> h.makeMad());
                    tell(player, "punizioni.msg.horses_stop_hint");
                }
            }
            case PULLING, TENSION -> this.stop(level);
            default -> {
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean startWith(ServerLevel level, LivingEntity victim, ServerPlayer player) {
        if (!this.capture(victim)) {
            return false;
        }
        this.setOperator(player);
        this.setState(TIED);
        this.spawnHorses(level);
        this.sound(SoundEvents.HORSE_GALLOP, 2.0F, 0.8F);
        return true;
    }

    /** The executioner stops the horses: the victim goes free, still stretched. */
    private void stop(ServerLevel level) {
        float s = this.stretch(0.0F);
        LivingEntity v = this.victim();
        this.setValue(s);
        this.setState(STOPPED);
        if (v != null && v.isAlive()) {
            ModData.setBody(v, ModData.body(v).stretched(level.getGameTime() + Body.STRETCH_TICKS, s));
            this.release();
        }
        this.forHorses(level, h -> h.makeMad());
    }

    @Override
    protected void serverTick(ServerLevel level) {
        int state = this.state();
        int t = this.stateTicks();
        LivingEntity v = this.victim();
        Player executioner = this.operator();
        if (state == IDLE) {
            if (!this.horses.isEmpty()) {
                this.removeHorses(level);
            }
            return;
        }
        if ((state == TIED || state == READY) && v == null) {
            this.setState(STOPPED);
            return;
        }
        switch (state) {
            case TIED -> {
                if (t >= ARRIVE_TICKS) {
                    this.setState(READY);
                    if (executioner instanceof ServerPlayer sp) {
                        tell(sp, "punizioni.msg.horses_ready");
                    }
                }
            }
            case READY -> {
                if (t % 50 == 0) {
                    this.forHorses(level, h -> h.playSound(SoundEvents.HORSE_BREATHE, 1.0F, 0.8F));
                }
            }
            case PULLING -> {
                if (v == null || !v.isAlive()) {
                    this.tear(level, v, executioner);
                    return;
                }
                if (t % 20 == 10) {
                    ModDamage.hurt(level, v, ModDamage.RACK, 1.0F, executioner);
                    Gore.hurtScreen(v, Net.VictimFx.STRETCH, 0.4F + 0.6F * this.stretch(0.0F));
                }
                if (t % 28 == 0) {
                    this.scream(v, false, 1.5F);
                }
                if (t % 16 == 0) {
                    this.sound(ModSounds.ROPE_CREAK, 1.4F, 0.7F + this.stretch(0.0F) * 0.3F);
                }
                if (t >= PULL_TICKS) {
                    this.setState(TENSION);
                }
            }
            case TENSION -> {
                if (v == null || !v.isAlive()) {
                    this.tear(level, v, executioner);
                    return;
                }
                if (t % 10 == 5) {
                    ModDamage.hurt(level, v, ModDamage.RACK, 1.0F, executioner);
                    Gore.hurtScreen(v, Net.VictimFx.STRETCH, 1.0F);
                }
                if (t % 14 == 0) {
                    this.sound(ModSounds.BONE_CRACK, 1.6F, 0.8F + this.random.nextFloat() * 0.4F);
                    this.scream(v, false, 1.7F);
                }
                if (t % 9 == 0) {
                    this.sound(ModSounds.ROPE_CREAK, 1.6F, 1.0F);
                }
                if (t % 20 == 0) {
                    this.forHorses(level, h -> {
                        if (this.random.nextInt(2) == 0) {
                            h.makeMad();
                        }
                    });
                }
                if (t >= TENSION_TICKS) {
                    this.tear(level, v, executioner);
                    return;
                }
            }
            case TORN, STOPPED -> {
                if (t >= LEAVE_TICKS) {
                    this.removeHorses(level);
                    this.setState(IDLE);
                    return;
                }
            }
            default -> {
            }
        }
        this.moveHorses(level, state, t);
    }

    /** The end: arms and legs torn off, flying after the horses. */
    private void tear(ServerLevel level, LivingEntity v, Player executioner) {
        this.setState(TORN);
        this.sound(ModSounds.TEAR, 3.0F, 0.9F);
        this.sound(ModSounds.BONE_CRACK, 2.4F, 0.6F);
        this.forHorses(level, h -> h.makeMad());
        for (int limb = 0; limb < 4; limb++) {
            double[] a = RackGeometry.anchor(limb, 1.0F);
            double[] p = RackGeometry.pull(limb);
            Vec3 at = this.local(a[0], RackGeometry.TOP + 0.1, a[1]);
            Vec3 dir = this.local(p[0], 0.0, p[1]).subtract(this.position()).add(0.0, 0.6, 0.0);
            Gore.jet(level, at, dir, 6, 0.5);
            Gore.burst(level, at, 30, 0.2, 0.15);
        }
        if (v != null) {
            Gore.toAll(this, new Net.TearFx(this.getId(), v.getId()));
            if (v.isAlive()) {
                ModData.setBody(v, ModData.body(v).withFlag(Body.TORN));
                this.scream(v, false, 2.0F);
                ModDamage.hurt(level, v, ModDamage.QUARTERING, 1000.0F, executioner);
            }
        }
    }

    // ------------------------------------------------------------------ horses

    private void spawnHorses(ServerLevel level) {
        this.removeHorses(level);
        for (int limb = 0; limb < 4; limb++) {
            DraftHorse h = ModEntities.DRAFT_HORSE.create(level, EntitySpawnReason.EVENT);
            if (h == null) {
                continue;
            }
            Vec3 at = this.horseSpot(level, limb, 14.0);
            h.snapTo(at.x, at.y, at.z, this.horseYaw(limb) + 180.0F, 0.0F);
            level.addFreshEntity(h);
            this.horses.add(h.getUUID());
        }
    }

    private Vec3 horseSpot(ServerLevel level, int limb, double distance) {
        double[] a = RackGeometry.anchor(limb, 0.0F);
        double[] p = RackGeometry.pull(limb);
        Vec3 at = this.local(a[0] + p[0] * distance, 0.0, a[1] + p[1] * distance);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
        double y = Math.abs(top - this.getY()) < 4.0 ? top : this.getY();
        return new Vec3(at.x, y, at.z);
    }

    /** The way a horse faces when it pulls (away from the rack). */
    private float horseYaw(int limb) {
        double[] p = RackGeometry.pull(limb);
        Vec3 d = this.local(p[0], 0.0, p[1]).subtract(this.position());
        return (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private void moveHorses(ServerLevel level, int state, int t) {
        float stretch = this.stretch(0.0F);
        for (int limb = 0; limb < this.horses.size(); limb++) {
            if (!(level.getEntity(this.horses.get(limb)) instanceof DraftHorse h)) {
                continue;
            }
            double distance;
            float yaw = this.horseYaw(limb);
            switch (state) {
                case TIED -> {
                    float k = Mth.clamp(t / (float) ARRIVE_TICKS, 0.0F, 1.0F);
                    k = 1.0F - (1.0F - k) * (1.0F - k);
                    distance = Mth.lerp(k, 14.0, HORSE_DISTANCE);
                    // galloping in towards the rack, turning round at the last moment
                    yaw = t > ARRIVE_TICKS - 12 ? yaw + 180.0F * Mth.clamp((ARRIVE_TICKS - t) / 12.0F, 0.0F, 1.0F) : yaw + 180.0F;
                }
                case READY -> distance = HORSE_DISTANCE;
                case PULLING -> distance = HORSE_DISTANCE + HORSE_PULL * stretch;
                case TENSION -> distance = HORSE_DISTANCE + HORSE_PULL + 0.18 * Mth.sin((t + limb * 5) * 0.9F);
                case TORN -> distance = HORSE_DISTANCE + HORSE_PULL + 12.0 * Mth.clamp(t / (float) LEAVE_TICKS, 0.0F, 1.0F);
                case STOPPED -> {
                    float k = Mth.clamp(t / (float) LEAVE_TICKS, 0.0F, 1.0F);
                    distance = HORSE_DISTANCE + HORSE_PULL * this.value() * (1.0F - k) + 10.0 * k * k;
                }
                default -> distance = HORSE_DISTANCE;
            }
            Vec3 at = this.horseSpot(level, limb, distance);
            h.setPos(at.x, at.y, at.z);
            h.setYRot(yaw);
            h.setYBodyRot(yaw);
            h.setYHeadRot(yaw);
            if ((state == PULLING || state == TENSION) && this.random.nextInt(3) == 0) {
                BlockPos ground = BlockPos.containing(at.x, at.y - 0.5, at.z);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(ground)), at.x, at.y + 0.1, at.z, 4,
                        0.4, 0.05, 0.4, 0.05);
            }
        }
    }

    private void forHorses(ServerLevel level, java.util.function.Consumer<DraftHorse> action) {
        for (UUID id : this.horses) {
            if (level.getEntity(id) instanceof DraftHorse h) {
                action.accept(h);
            }
        }
    }

    private void removeHorses(ServerLevel level) {
        for (UUID id : this.horses) {
            Entity e = level.getEntity(id);
            if (e != null) {
                level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1.0, e.getZ(), 12, 0.5, 0.6, 0.5, 0.02);
                e.discard();
            }
        }
        this.horses.clear();
    }

    @Override
    protected void onRemovedMachine(ServerLevel level) {
        this.removeHorses(level);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.level() instanceof ServerLevel level) {
            this.removeHorses(level);
        }
        super.remove(reason);
    }
}
