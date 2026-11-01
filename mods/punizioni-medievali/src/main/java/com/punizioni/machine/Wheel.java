package com.punizioni.machine;

import com.punizioni.ModDamage;
import com.punizioni.ModSounds;
import com.punizioni.data.Body;
import com.punizioni.data.ModData;
import com.punizioni.net.Net;
import com.punizioni.server.Gore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The breaking wheel: a cartwheel on a tall post. Whoever stands in front of it is tied spread out on
 * the spokes; the executioner breaks arms and legs with the mallet, one bone at a time (they stay
 * bent the wrong way until death). Then the wheel is hoisted to the top of the post and turns, slowly,
 * with the victim on it, and the crows come.
 */
public class Wheel extends Machine {
    public static final int TIED = 1;
    public static final int RAISING = 2;
    public static final int RAISED = 3;
    public static final int EMPTY = 4;
    public static final int LOWERING = 5;

    public static final int HOIST_TICKS = 80;
    public static final int EMPTY_TICKS = 20 * 20;
    /** Height of the wheel's hub, down and up. */
    public static final double HUB_LOW = 1.5;
    public static final double HUB_HIGH = 4.7;
    /** The crows come this long after the wheel is up. */
    public static final int CROWS_AFTER = 50;
    /** The order the mallet breaks the bones in. */
    private static final int[] BONES = {Body.RIGHT_ARM, Body.LEFT_ARM, Body.RIGHT_LEG, Body.LEFT_LEG};

    public Wheel(EntityType<? extends Wheel> type, Level level) {
        super(type, level);
    }

    @Override
    public MachineKind kind() {
        return MachineKind.WHEEL;
    }

    /** Feet on the bottom spokes, the hips on the hub. */
    @Override
    protected Vec3 slot() {
        return new Vec3(0.0, this.hub(0.0F) - 0.72, 0.42);
    }

    @Override
    protected float headFreedom() {
        return 45.0F;
    }

    /** Where the hub is now (blocks over the ground). */
    public double hub(float partial) {
        float t = this.stateAge(partial);
        return switch (this.state()) {
            case RAISING -> Mth.lerp(ease(t / HOIST_TICKS), HUB_LOW, HUB_HIGH);
            case RAISED, EMPTY -> HUB_HIGH;
            case LOWERING -> Mth.lerp(ease(t / HOIST_TICKS), HUB_HIGH, HUB_LOW);
            default -> HUB_LOW;
        };
    }

    /** How far the wheel has turned (degrees): it turns only up on the post. */
    public float spin(float partial) {
        float t = this.stateAge(partial);
        return switch (this.state()) {
            case RAISED -> t * 1.2F;
            case EMPTY -> (this.value() + t) * 0.8F;
            default -> 0.0F;
        };
    }

    private static float ease(float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
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
                    this.setState(RAISING);
                    this.sound(ModSounds.ROPE_CREAK, 1.8F, 0.6F);
                    this.sound(ModSounds.DRUMS, 2.0F, 0.85F);
                }
            }
            case RAISED -> {
                if (player.isShiftKeyDown()) {
                    this.setState(LOWERING);
                    this.sound(ModSounds.ROPE_CREAK, 1.8F, 0.7F);
                }
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
        tell(player, "punizioni.msg.wheel_hint");
        return true;
    }

    /** /punizione avanza: a blow of the mallet while bones are left, then up it goes. */
    @Override
    public void commandAdvance(ServerLevel level, ServerPlayer player) {
        LivingEntity v = this.victim();
        if (this.state() == TIED && v != null && ModData.body(v).broken() != Body.ALL_BONES) {
            this.strike(level, player);
        } else {
            super.commandAdvance(level, player);
        }
    }

    /** A blow of the mallet on the one tied to the wheel: the next bone breaks. */
    public boolean strike(ServerLevel level, Player executioner) {
        LivingEntity v = this.victim();
        if (this.state() != TIED || v == null || !v.isAlive()) {
            return false;
        }
        Body body = ModData.body(v);
        int bone = 0;
        for (int b : BONES) {
            if (!body.brokenBone(b)) {
                bone = b;
                break;
            }
        }
        this.sound(ModSounds.MALLET, 2.2F, 0.8F + this.random.nextFloat() * 0.2F);
        if (bone == 0) {
            // nothing left to break: just the blow
            ModDamage.hurt(level, v, ModDamage.WHEEL, 2.0F, executioner);
            return true;
        }
        ModData.setBody(v, body.withBroken(bone));
        this.sound(ModSounds.BONE_CRACK, 2.4F, 0.7F + this.random.nextFloat() * 0.3F);
        this.scream(v, false, 2.0F);
        boolean arm = bone == Body.RIGHT_ARM || bone == Body.LEFT_ARM;
        boolean right = bone == Body.RIGHT_ARM || bone == Body.RIGHT_LEG;
        Vec3 at = this.local(right ? 0.55 : -0.55, this.hub(0.0F) + (arm ? 0.45 : -0.5), 0.5);
        Gore.burst(level, at, 25, 0.15, 0.12);
        Gore.jet(level, at, this.forward().add(0.0, 0.3, 0.0), 4, 0.3);
        Gore.hurtScreen(v, Net.VictimFx.BONE, 1.0F);
        ModDamage.hurt(level, v, ModDamage.WHEEL, 3.0F, executioner);
        if (executioner instanceof ServerPlayer sp && (body.broken() | bone) == Body.ALL_BONES) {
            tell(sp, "punizioni.msg.wheel_raise");
        }
        return true;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        int t = this.stateTicks();
        LivingEntity v = this.victim();
        Player executioner = this.operator();
        switch (this.state()) {
            case TIED -> {
                if (v == null) {
                    this.setState(IDLE);
                }
            }
            case RAISING -> {
                if (t % 12 == 0) {
                    this.sound(ModSounds.ROPE_CREAK, 1.2F, 0.7F + t / (float) HOIST_TICKS * 0.3F);
                }
                if (t >= HOIST_TICKS) {
                    this.setState(v != null && v.isAlive() ? RAISED : EMPTY);
                    this.sound(ModSounds.TOLL, 3.0F, 0.9F);
                }
            }
            case RAISED -> {
                if (v == null || !v.isAlive()) {
                    this.setValue(t);
                    this.setRemains(v);
                    this.setState(EMPTY);
                    break;
                }
                if (t > CROWS_AFTER) {
                    if (t % 25 == 0) {
                        ModDamage.hurt(level, v, ModDamage.CROWS, 1.0F, executioner);
                        Gore.burst(level, v.position().add(0.0, 1.0 + this.random.nextDouble() * 0.8, 0.0), 8, 0.3, 0.08);
                        Gore.hurtScreen(v, Net.VictimFx.PECK, 0.6F);
                    }
                    if (t % 18 == 0) {
                        this.sound(ModSounds.CROW, 1.6F, 0.8F + this.random.nextFloat() * 0.4F);
                    }
                }
                if (t % 45 == 20) {
                    this.scream(v, false, 1.4F);
                }
            }
            case EMPTY -> {
                if (t % 30 == 0) {
                    this.sound(ModSounds.CROW, 1.4F, 0.8F + this.random.nextFloat() * 0.4F);
                }
                if (t >= EMPTY_TICKS) {
                    this.setRemains(null);
                    this.setState(LOWERING);
                }
            }
            case LOWERING -> {
                if (t >= HOIST_TICKS) {
                    this.release();
                    this.setState(IDLE);
                }
            }
            default -> {
            }
        }
    }
}
