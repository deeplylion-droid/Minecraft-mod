package com.punizioni.machine;

import com.punizioni.ModDamage;
import com.punizioni.ModSounds;
import com.punizioni.data.Body;
import com.punizioni.data.ModData;
import com.punizioni.net.Net;
import com.punizioni.server.Gore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The guillotine: two tall posts, a heavy slanted blade high up, the plank and the lunette, a wicker
 * basket in front. Whoever lies on the plank gets their neck locked in the lunette, looking down into
 * the basket; the drums roll; when the executioner pulls the rope the blade comes down, the head
 * drops into the basket (the victim's own view falls with it) and the neck spurts. The head stays in
 * the basket for half a minute, with the victim's face (their skin).
 */
public class Guillotine extends Machine {
    public static final int LOCKED = 1;
    public static final int DROP = 2;
    public static final int DONE = 3;
    public static final int RESET = 4;

    /** The blade falls in this many ticks; the cut is on the last one. */
    public static final int DROP_TICKS = 5;
    public static final int DONE_TICKS = 20 * 30;
    public static final int RESET_TICKS = 50;
    /** Ticks between the blade and death: the head has fallen and settled in the basket. */
    public static final int DEATH_DELAY = 30;
    /** Blade heights (bottom edge, blocks over the ground): up, and at the neck. */
    public static final float BLADE_UP = 3.55F;
    public static final float BLADE_DOWN = 1.02F;
    /** The lunette (the neck) and the basket, in the machine frame. */
    public static final Vec3 NECK = new Vec3(0.0, 1.08, 0.42);
    public static final Vec3 BASKET = new Vec3(0.0, 0.28, 0.98);

    public Guillotine(EntityType<? extends Guillotine> type, Level level) {
        super(type, level);
    }

    @Override
    public MachineKind kind() {
        return MachineKind.GUILLOTINE;
    }

    /** The hips, on the plank: lying face down, the head forward in the lunette. */
    @Override
    protected Vec3 slot() {
        // lying face down, the hips 0.13 under the body's middle line and 12 pixels (15/16) behind the neck
        return new Vec3(0.0, NECK.y - 0.13, NECK.z - 0.703);
    }

    @Override
    protected float headFreedom() {
        return 25.0F;
    }

    @Override
    protected Vec3 catchSize() {
        return new Vec3(0.8, 2.0, 0.8);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    protected double dismountDistance() {
        return -1.8;
    }

    /** The bottom edge of the blade now. */
    public float blade(float partial) {
        float t = this.stateAge(partial);
        return switch (this.state()) {
            case DROP -> {
                float k = Mth.clamp(t / DROP_TICKS, 0.0F, 1.0F);
                yield Mth.lerp(k * k, BLADE_UP, BLADE_DOWN);
            }
            case DONE -> BLADE_DOWN;
            case RESET -> Mth.lerp(Mth.clamp(t / RESET_TICKS, 0.0F, 1.0F), BLADE_DOWN, BLADE_UP);
            default -> BLADE_UP;
        };
    }

    /** The upper half of the lunette: 0 up (open), 1 down on the neck. */
    public float lunette(float partial) {
        float t = this.stateAge(partial);
        return switch (this.state()) {
            case LOCKED -> Math.min(1.0F, t / 10.0F);
            case DROP, DONE -> 1.0F;
            case RESET -> Math.max(0.0F, 1.0F - t / 20.0F);
            default -> 0.0F;
        };
    }

    @Override
    protected InteractionResult use(ServerLevel level, ServerPlayer player, ItemStack stack) {
        switch (this.state()) {
            case IDLE -> this.catchSomeone(level, player);
            case LOCKED -> {
                if (player.isShiftKeyDown()) {
                    this.release();
                    this.setState(IDLE);
                } else {
                    this.setOperator(player);
                    this.setState(DROP);
                    this.sound(ModSounds.WHOOSH, 2.4F, 0.6F);
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
        this.setState(LOCKED);
        this.sound(SoundEvents.WOODEN_TRAPDOOR_CLOSE, 1.6F, 0.7F);
        this.sound(ModSounds.DRUMS, 2.4F, 1.0F);
        tell(player, "punizioni.msg.guillotine_hint");
        return true;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        int t = this.stateTicks();
        LivingEntity v = this.victim();
        Player executioner = this.operator();
        switch (this.state()) {
            case LOCKED -> {
                if (v == null) {
                    this.setState(IDLE);
                } else if (t % 60 == 30) {
                    this.scream(v, false, 1.2F);
                }
            }
            case DROP -> {
                if (t >= DROP_TICKS) {
                    this.setState(DONE);
                    this.sound(ModSounds.CHOP, 3.0F, 0.6F);
                    this.sound(ModSounds.MAIDEN_SLAM, 1.6F, 1.4F);
                    Vec3 neck = this.local(NECK.x, NECK.y, NECK.z - 0.05);
                    Gore.burst(level, neck, 60, 0.15, 0.25);
                    if (v != null) {
                        this.setRemains(v);
                        Gore.toAll(this, new Net.HeadFx(this.getId(), v.getId()));
                        if (v.isAlive()) {
                            // the body is headless at once; death comes a moment later, so the victim
                            // sees the head's fall into the basket (VictimCamera) before the death screen
                            ModData.setBody(v, ModData.body(v).withFlag(Body.HEADLESS));
                        }
                    }
                }
            }
            case DONE -> {
                if (t == DEATH_DELAY && v != null && v.isAlive()) {
                    ModDamage.hurt(level, v, ModDamage.GUILLOTINE, 1000.0F, executioner);
                }
                Vec3 neck = this.local(NECK.x, NECK.y - 0.05, NECK.z - 0.12);
                if (t < 70) {
                    // the neck spurts in beats, weaker and weaker
                    float beat = Math.max(0.0F, Mth.sin(t * 0.55F));
                    if (beat > 0.6F) {
                        Gore.jet(level, neck, this.forward().scale(-0.4).add(0.0, 0.5, 0.0), (int) (3 + 4 * (1.0F - t / 70.0F)),
                                0.35 * (1.0F - t / 90.0F));
                    }
                }
                if (t % 6 == 0 && t < 400) {
                    // drops from the blade and the lunette into the basket
                    Gore.burst(level, this.local(0.0, BLADE_DOWN, NECK.z + 0.1), 1, 0.2, 0.0);
                }
                if (t >= DONE_TICKS) {
                    this.setRemains(null);
                    this.setState(RESET);
                    this.sound(ModSounds.ROPE_CREAK, 1.2F, 0.8F);
                }
            }
            case RESET -> {
                if (t >= RESET_TICKS) {
                    this.setState(IDLE);
                }
            }
            default -> {
            }
        }
    }
}
