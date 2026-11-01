package com.punizioni.machine;

import com.punizioni.ModDamage;
import com.punizioni.ModSounds;
import com.punizioni.data.ModData;
import com.punizioni.entity.ModEntities;
import com.punizioni.entity.Rat;
import com.punizioni.net.Net;
import com.punizioni.server.Gore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The rat table: a plank table with straps and, at its foot, an iron cage full of rats. Whoever lies
 * on it gets strapped down; when the executioner opens the cage the rats pour out, run up the table
 * and cover the body, gnawing (the skin fills with bites). Killing the rats saves the victim; when
 * the victim is dead the rats go back in their cage and leave the bones on the table.
 */
public class RatTable extends Machine {
    public static final int TIED = 1;
    public static final int SWARM = 2;
    public static final int RECALL = 3;
    public static final int REMAINS = 4;

    public static final double TOP = 0.95;
    public static final double HIP_Z = 0.2;
    public static final int RATS = 18;
    public static final int REMAINS_TICKS = 20 * 30;

    private final List<RatRun> rats = new ArrayList<>();

    /** One rat: when it comes out, where on the body it goes. */
    private static final class RatRun {
        final UUID id;
        final int start;
        final double x, z;
        float home;

        RatRun(UUID id, int start, double x, double z) {
            this.id = id;
            this.start = start;
            this.x = x;
            this.z = z;
        }
    }

    public RatTable(EntityType<? extends RatTable> type, Level level) {
        super(type, level);
    }

    @Override
    public MachineKind kind() {
        return MachineKind.RAT_TABLE;
    }

    @Override
    protected Vec3 slot() {
        return new Vec3(0.0, TOP, HIP_Z);
    }

    @Override
    protected float headFreedom() {
        return 50.0F;
    }

    @Override
    protected Vec3 catchSize() {
        return new Vec3(1.0, 2.0, 1.0);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    /** The cage door: 0 shut, 1 open. */
    public float door(float partial) {
        float t = this.stateAge(partial);
        return switch (this.state()) {
            case SWARM -> Math.min(1.0F, t / 8.0F);
            case RECALL, REMAINS -> Math.max(0.0F, 1.0F - Math.max(0.0F, t - 40.0F) / 8.0F);
            default -> 0.0F;
        };
    }

    @Override
    protected InteractionResult use(ServerLevel level, ServerPlayer player, ItemStack stack) {
        switch (this.state()) {
            case IDLE -> this.catchSomeone(level, player);
            case TIED -> {
                if (player.isShiftKeyDown()) {
                    this.release();
                    this.setState(IDLE);
                } else {
                    this.setState(SWARM);
                    this.sound(ModSounds.DRUMS, 2.0F, 1.1F);
                    this.sound(ModSounds.CHAINS, 1.2F, 0.7F);
                    this.spawnRats(level);
                }
            }
            case SWARM -> {
                // mercy: the rats are called back
                this.release();
                this.setState(RECALL);
                this.recallRats();
            }
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
        tell(player, "punizioni.msg.rats_hint");
        return true;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        int state = this.state();
        int t = this.stateTicks();
        LivingEntity v = this.victim();
        Player executioner = this.operator();
        switch (state) {
            case TIED -> {
                if (v == null) {
                    this.setState(IDLE);
                }
            }
            case SWARM -> {
                if (v == null || !v.isAlive()) {
                    this.setState(REMAINS);
                    this.recallRats();
                    break;
                }
                int gnawing = this.countGnawing(level);
                if (t % 10 == 0 && gnawing > 0) {
                    ModDamage.hurt(level, v, ModDamage.RATS, 0.05F * gnawing, executioner);
                    if (gnawing >= 4) {
                        ModData.setBody(v, ModData.body(v).bitten(1 + gnawing / 8));
                    }
                    Gore.hurtScreen(v, Net.VictimFx.BITE, Math.min(1.0F, gnawing / 12.0F));
                    Gore.burst(level, v.position().add(0.0, 0.3, 0.0), 2 + gnawing / 3, 0.35, 0.05);
                }
                if (t % 24 == 0 && gnawing > 2) {
                    this.scream(v, false, 1.6F);
                }
                if (t % 30 == 0) {
                    this.sound(ModSounds.RATS, 1.6F, 0.9F + this.random.nextFloat() * 0.2F);
                }
                if (t % 13 == 0 && gnawing > 0) {
                    this.sound(ModSounds.GNAW, 1.2F, 0.8F + this.random.nextFloat() * 0.4F);
                }
            }
            case RECALL -> {
                if (t > 50) {
                    this.removeRats(level);
                    this.setState(IDLE);
                }
            }
            case REMAINS -> {
                if (t == 52) {
                    this.removeRats(level);
                }
                if (t >= REMAINS_TICKS) {
                    this.setState(IDLE);
                }
            }
            default -> {
                if (!this.rats.isEmpty()) {
                    this.removeRats(level);
                }
            }
        }
        this.moveRats(level, state, t);
    }

    // ------------------------------------------------------------------ rats

    /** Where the cage door is (machine frame). */
    private static final Vec3 DOOR = new Vec3(0.0, TOP - 0.05, 1.08);

    private void spawnRats(ServerLevel level) {
        this.removeRats(level);
        for (int i = 0; i < RATS; i++) {
            Rat r = ModEntities.RAT.create(level, EntitySpawnReason.EVENT);
            if (r == null) {
                continue;
            }
            Vec3 at = this.local(DOOR.x, DOOR.y, DOOR.z);
            r.snapTo(at.x, at.y, at.z, this.getYRot() + 180.0F, 0.0F);
            level.addFreshEntity(r);
            // spread over the body: legs, belly, chest, arms, face
            double z = HIP_Z + 0.55 - this.random.nextDouble() * 1.45;
            double x = (this.random.nextDouble() - 0.5) * (z < HIP_Z - 0.5 ? 0.5 : 0.75);
            this.rats.add(new RatRun(r.getUUID(), i * 2 + this.random.nextInt(3), x, z));
        }
    }

    private void recallRats() {
        for (RatRun r : this.rats) {
            r.home = 1.0F;
        }
    }

    private int countGnawing(ServerLevel level) {
        int n = 0;
        for (RatRun run : this.rats) {
            if (level.getEntity(run.id) instanceof Rat r && r.isAlive() && r.gnawing()) {
                n++;
            }
        }
        return n;
    }

    private void moveRats(ServerLevel level, int state, int t) {
        for (RatRun run : this.rats) {
            if (!(level.getEntity(run.id) instanceof Rat r) || !r.isAlive()) {
                continue;
            }
            Vec3 door = this.local(DOOR.x, DOOR.y, DOOR.z);
            double jx = Mth.sin((t + run.start * 7) * 0.7F) * 0.04;
            double jz = Mth.cos((t + run.start * 5) * 0.9F) * 0.04;
            Vec3 spot = this.local(run.x + jx, TOP + 0.24, run.z + jz);
            Vec3 at;
            boolean gnaw = false;
            if (state == SWARM && run.home == 0.0F) {
                float k = Mth.clamp((t - run.start) / 18.0F, 0.0F, 1.0F);
                if (t < run.start) {
                    at = door;
                } else if (k < 1.0F) {
                    // scurrying out and up, with little hops
                    at = door.lerp(spot, k).add(0.0, Math.abs(Mth.sin(k * 9.0F)) * 0.12, 0.0);
                } else {
                    at = spot;
                    gnaw = true;
                }
            } else {
                // back to the cage, all together
                run.home = Math.min(1.0F, run.home + 1.0F / 30.0F);
                Vec3 from = r.position();
                at = from.lerp(door, 0.18);
            }
            Vec3 d = at.subtract(r.position());
            float yaw = d.horizontalDistanceSqr() > 1.0E-4 ? (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F
                    : r.getYRot() + (this.random.nextFloat() - 0.5F) * 30.0F;
            r.setPos(at.x, at.y, at.z);
            r.setYRot(yaw);
            r.setYBodyRot(yaw);
            r.setYHeadRot(yaw);
            r.setGnawing(gnaw);
        }
        this.rats.removeIf(run -> !(level.getEntity(run.id) instanceof Rat r) || !r.isAlive());
    }

    private void removeRats(ServerLevel level) {
        for (RatRun run : this.rats) {
            Entity e = level.getEntity(run.id);
            if (e != null) {
                e.discard();
            }
        }
        this.rats.clear();
    }

    @Override
    protected void onRemovedMachine(ServerLevel level) {
        this.removeRats(level);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.level() instanceof ServerLevel level) {
            this.removeRats(level);
        }
        super.remove(reason);
    }
}
