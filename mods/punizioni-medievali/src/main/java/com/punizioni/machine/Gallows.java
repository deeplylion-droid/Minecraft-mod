package com.punizioni.machine;

import com.punizioni.ModDamage;
import com.punizioni.ModSounds;
import com.punizioni.net.Net;
import com.punizioni.server.Gore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The gallows: a scaffold with a trapdoor, two posts and a beam with the noose. Whoever stands by the
 * scaffold is brought up onto the trapdoor with the noose round the neck; the drums roll; the
 * executioner pulls the lever, the trapdoor drops and the body falls, jerks, kicks and sways. Cut the
 * rope in time (with a sword, an axe or shears) and the victim falls free. Afterwards the body hangs
 * there for a long while, and the crows come.
 */
public class Gallows extends Machine {
    public static final int NOOSED = 1;
    public static final int HANGING = 2;
    public static final int CORPSE = 3;

    public static final int CORPSE_TICKS = 20 * 45;
    /** The top of the scaffold, the drop, the beam. */
    public static final double FLOOR = 2.0;
    public static final double DROP = 1.05;
    public static final double BEAM = 4.85;

    public Gallows(EntityType<? extends Gallows> type, Level level) {
        super(type, level);
    }

    @Override
    public MachineKind kind() {
        return MachineKind.GALLOWS;
    }

    /** On the trapdoor; after the drop, hanging under it. */
    @Override
    protected Vec3 slot() {
        return new Vec3(0.0, FLOOR - this.drop(0.0F), 0.0);
    }

    @Override
    protected Vec3 catchSize() {
        return new Vec3(1.2, 3.0, 1.2);
    }

    @Override
    protected float headFreedom() {
        return 70.0F;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved() && this.state() != HANGING;
    }

    @Override
    protected double dismountDistance() {
        return 0.0;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return this.position().add(0.0, 0.05, 0.0);
    }

    /** How far the body has dropped: quick, with a jerk at the end of the rope. */
    public float drop(float partial) {
        if (this.state() != HANGING) {
            return 0.0F;
        }
        float t = this.stateAge(partial);
        if (t < 4.0F) {
            float k = t / 4.0F;
            return (float) (DROP * k * k);
        }
        // a little bounce on the rope
        float b = (float) Math.exp(-(t - 4.0F) / 6.0F) * Mth.sin((t - 4.0F) * 1.1F) * 0.08F;
        return (float) DROP + b;
    }

    /** The trapdoor: 0 shut, 1 hanging open. */
    public float trapdoor(float partial) {
        float t = this.stateAge(partial);
        return switch (this.state()) {
            case HANGING -> Math.min(1.0F, t / 3.0F);
            case CORPSE -> t > CORPSE_TICKS - 20 ? Math.max(0.0F, (CORPSE_TICKS - t) / 20.0F) : 1.0F;
            default -> 0.0F;
        };
    }

    private static boolean cuts(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.SHEARS);
    }

    @Override
    protected InteractionResult use(ServerLevel level, ServerPlayer player, ItemStack stack) {
        switch (this.state()) {
            case IDLE -> this.catchSomeone(level, player);
            case NOOSED -> {
                if (player.isShiftKeyDown()) {
                    this.release();
                    this.setState(IDLE);
                } else {
                    this.setOperator(player);
                    this.setState(HANGING);
                    this.sound(SoundEvents.WOODEN_TRAPDOOR_OPEN, 2.4F, 0.6F);
                    this.sound(SoundEvents.LEVER_CLICK, 1.6F, 0.6F);
                }
            }
            case HANGING -> {
                if (cuts(stack)) {
                    // the rope is cut: the victim drops free
                    this.sound(ModSounds.ROPE_CREAK, 1.6F, 1.6F);
                    this.release();
                    this.setState(CORPSE);
                    this.setRemains(null);
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
        this.setState(NOOSED);
        this.sound(ModSounds.ROPE_CREAK, 1.4F, 1.0F);
        this.sound(ModSounds.DRUMS, 2.4F, 0.95F);
        tell(player, "punizioni.msg.gallows_hint");
        return true;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        int t = this.stateTicks();
        LivingEntity v = this.victim();
        Player executioner = this.operator();
        switch (this.state()) {
            case NOOSED -> {
                if (v == null) {
                    this.setState(IDLE);
                } else if (t % 50 == 25) {
                    this.sound(ModSounds.ROPE_CREAK, 0.8F, 1.1F);
                }
            }
            case HANGING -> {
                if (t == 4) {
                    // the jerk at the end of the rope
                    this.sound(ModSounds.BONE_CRACK, 2.0F, 0.6F);
                    this.sound(ModSounds.ROPE_CREAK, 2.0F, 0.6F);
                    if (v != null) {
                        Gore.hurtScreen(v, Net.VictimFx.HANG, 1.0F);
                    }
                }
                if (v == null || !v.isAlive()) {
                    this.setRemains(v);
                    this.setState(CORPSE);
                    break;
                }
                if (t > 4 && t % 10 == 0) {
                    ModDamage.hurt(level, v, ModDamage.HANGING, 1.5F, executioner);
                    Gore.hurtScreen(v, Net.VictimFx.HANG, 0.5F);
                }
                if (t % 16 == 0) {
                    this.sound(ModSounds.ROPE_CREAK, 1.4F, 0.7F + this.random.nextFloat() * 0.2F);
                }
            }
            case CORPSE -> {
                if (this.remains() != null) {
                    if (t > 80 && t % 35 == 0) {
                        this.sound(ModSounds.CROW, 1.6F, 0.8F + this.random.nextFloat() * 0.4F);
                    }
                    if (t > 140 && t % 40 == 0) {
                        // the crows pecking at the body
                        Gore.burst(level, this.local(0.0, FLOOR - DROP + 1.4, 0.15), 4, 0.15, 0.03);
                    }
                    if (t % 70 == 0) {
                        this.sound(ModSounds.ROPE_CREAK, 0.8F, 0.6F);
                    }
                }
                if (t >= CORPSE_TICKS) {
                    this.setRemains(null);
                    this.setState(IDLE);
                    this.sound(SoundEvents.WOODEN_TRAPDOOR_CLOSE, 1.4F, 0.7F);
                }
            }
            default -> {
            }
        }
    }
}
