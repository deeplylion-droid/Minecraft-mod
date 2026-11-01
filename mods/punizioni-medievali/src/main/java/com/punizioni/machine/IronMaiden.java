package com.punizioni.machine;

import com.punizioni.ModDamage;
import com.punizioni.ModSounds;
import com.punizioni.server.Gore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The iron maiden: a sarcophagus three blocks tall with a woman's face, its two doors studded with
 * spikes on the inside. Whoever stands inside when the executioner shuts it: the doors swing closed
 * slowly, creaking, the spikes go in, and blood runs out from under the doors into a pool. If the
 * executioner opens it again in time, the victim walks out (bleeding).
 */
public class IronMaiden extends Machine {
    public static final int CLOSING = 1;
    public static final int CLOSED = 2;
    public static final int OPENING = 3;

    public static final int CLOSE_TICKS = 60;
    /** The spikes touch the skin a little before the doors are shut. */
    public static final int FIRST_STAB = 46;
    public static final int OPEN_TICKS = 40;

    public IronMaiden(EntityType<? extends IronMaiden> type, Level level) {
        super(type, level);
    }

    @Override
    public MachineKind kind() {
        return MachineKind.IRON_MAIDEN;
    }

    @Override
    protected Vec3 slot() {
        return new Vec3(0.0, 0.08, -0.05);
    }

    @Override
    protected float headFreedom() {
        return 45.0F;
    }

    @Override
    protected Vec3 catchSize() {
        return new Vec3(0.55, 2.2, 0.55);
    }

    /** 0 = open, 1 = shut. */
    public float doors(float partial) {
        float t = this.stateAge(partial);
        return switch (this.state()) {
            case CLOSING -> {
                float k = Math.min(1.0F, t / CLOSE_TICKS);
                // slow at first, then the weight of the doors takes over
                yield k * k * (3.0F - 2.0F * k) * 0.35F + k * k * k * 0.65F;
            }
            case CLOSED -> 1.0F;
            case OPENING -> 1.0F - Math.min(1.0F, t / OPEN_TICKS) * Math.min(1.0F, t / OPEN_TICKS);
            default -> 0.0F;
        };
    }

    /** The pool of blood in front of it: grows while it is closed, dries up slowly afterwards. */
    public float blood() {
        return this.value();
    }

    @Override
    protected InteractionResult use(ServerLevel level, ServerPlayer player, ItemStack stack) {
        switch (this.state()) {
            case IDLE -> this.catchSomeone(level, player);
            case CLOSED -> {
                // mercy: the doors open, whoever survived walks out
                this.setState(OPENING);
                this.sound(ModSounds.MAIDEN_CREAK, 1.6F, 1.1F);
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
        this.setState(CLOSING);
        this.sound(ModSounds.DRUMS, 2.0F, 1.0F);
        this.sound(ModSounds.MAIDEN_CREAK, 1.6F, 0.9F);
        return true;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        int t = this.stateTicks();
        LivingEntity v = this.victim();
        Player executioner = this.operator();
        switch (this.state()) {
            case CLOSING -> {
                if (v == null) {
                    this.setState(OPENING);
                    return;
                }
                if (t == FIRST_STAB) {
                    this.scream(v, false, 1.6F);
                    Gore.hurtScreen(v, com.punizioni.net.Net.VictimFx.SPIKES, 0.5F);
                }
                if (t >= CLOSE_TICKS) {
                    this.setState(CLOSED);
                    this.sound(ModSounds.MAIDEN_SLAM, 2.4F, 0.8F);
                    this.sound(ModSounds.SPIKES, 2.0F, 1.0F);
                    ModDamage.hurt(level, v, ModDamage.MAIDEN, 8.0F, executioner);
                    Gore.hurtScreen(v, com.punizioni.net.Net.VictimFx.SPIKES, 1.0F);
                    this.bleed(level, 30);
                }
            }
            case CLOSED -> {
                this.setValue(Math.min(1.0F, this.blood() + 0.004F));
                if (t % 4 == 0) {
                    this.bleed(level, 2);
                }
                if (v != null && v.isAlive()) {
                    if (t % 15 == 0) {
                        ModDamage.hurt(level, v, ModDamage.MAIDEN, 2.0F, executioner);
                    }
                    if (t % 36 == 0) {
                        this.scream(v, true, 1.4F);
                    }
                } else if (t > 40 && this.deadFor(v) > 40) {
                    this.setState(OPENING);
                    this.sound(ModSounds.MAIDEN_CREAK, 1.6F, 1.0F);
                }
            }
            case OPENING -> {
                if (t >= OPEN_TICKS) {
                    this.release();
                    this.setState(IDLE);
                }
            }
            default -> this.setValue(Math.max(0.0F, this.blood() - 0.0004F));
        }
    }

    private long deadSince = -1;

    /** How long the victim has been dead (or gone). */
    private int deadFor(LivingEntity v) {
        long now = this.level().getGameTime();
        if (v != null && v.isAlive()) {
            this.deadSince = -1;
            return 0;
        }
        if (this.deadSince < 0) {
            this.deadSince = now;
        }
        return (int) (now - this.deadSince);
    }

    /** Blood running out of the seam between the doors and from under them. */
    private void bleed(ServerLevel level, int count) {
        Vec3 seam = this.local(0.0, 0.2 + this.random.nextDouble() * 2.2, 0.55);
        Gore.jet(level, seam, this.forward().add(0.0, -0.4, 0.0), Math.max(1, count / 6), 0.15);
        Vec3 base = this.local((this.random.nextDouble() - 0.5) * 0.8, 0.05, 0.6);
        Gore.burst(level, base, count, 0.15, 0.02);
    }
}
