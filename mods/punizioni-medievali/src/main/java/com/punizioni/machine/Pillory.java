package com.punizioni.machine;

import com.punizioni.ModSounds;
import com.punizioni.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
 * The pillory: a low wooden stage, two posts and a board in two halves with three holes. Whoever
 * stands behind the board gets bent over, the neck and the wrists locked in it, the face out for
 * everyone to see; the bell calls the village and the dung starts flying (see Dung). The
 * executioner's right click opens the board again. A right click with dung in hand throws it
 * instead of opening the board.
 */
public class Pillory extends Machine {
    public static final int LOCKED = 1;
    /** The upper half comes down (or goes up again) in this many ticks. */
    public static final int CLOSE_TICKS = 8;
    /** Who hears the bell and the call (blocks). */
    private static final double ANNOUNCE_RANGE = 96.0;

    public Pillory(EntityType<? extends Pillory> type, Level level) {
        super(type, level);
    }

    @Override
    public MachineKind kind() {
        return MachineKind.PILLORY;
    }

    /** On the stage, behind the board. */
    @Override
    protected Vec3 slot() {
        return new Vec3(0.0, PilloryPose.PLATFORM, -PilloryPose.FEET_BEHIND);
    }

    @Override
    protected float headFreedom() {
        return 25.0F;
    }

    @Override
    protected Vec3 catchSize() {
        return new Vec3(0.7, 2.0, 0.7);
    }

    /** Always there to be hit: the board stops dung, and clicks on it open it. */
    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    protected double dismountDistance() {
        return -1.5;
    }

    /** With dung in hand a click throws it: the board stays shut. */
    @Override
    public boolean ignoresClickWith(ItemStack stack) {
        return stack.is(ModItems.POOP) || super.ignoresClickWith(stack);
    }

    /** The upper half of the board: 0 up (open), 1 down on the neck and the wrists. */
    public float board(float partial) {
        float t = this.stateAge(partial);
        return this.state() == LOCKED ? Mth.clamp(t / CLOSE_TICKS, 0.0F, 1.0F) : Mth.clamp(1.0F - t / CLOSE_TICKS, 0.0F, 1.0F);
    }

    @Override
    protected InteractionResult use(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (this.state() == IDLE) {
            this.catchSomeone(level, player);
        } else {
            this.release();
            this.setState(IDLE);
            this.sound(SoundEvents.WOODEN_TRAPDOOR_OPEN, 1.4F, 0.7F);
            this.sound(ModSounds.CHAINS, 0.8F, 1.1F);
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
        this.sound(SoundEvents.WOODEN_TRAPDOOR_CLOSE, 1.8F, 0.6F);
        this.sound(ModSounds.CHAINS, 1.0F, 0.8F);
        // the bell calls the village
        level.playSound(null, this.getX(), this.getY() + 2.0, this.getZ(), ModSounds.TOLL, net.minecraft.sounds.SoundSource.BLOCKS, 2.5F, 1.2F);
        tell(player, "punizioni.msg.pillory_hint");
        Component call = Component.translatable("punizioni.msg.pillory_call", victim.getDisplayName()).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(this) < ANNOUNCE_RANGE * ANNOUNCE_RANGE) {
                p.sendSystemMessage(call);
            }
        }
        return true;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (this.state() == LOCKED) {
            LivingEntity v = this.victim();
            if (v == null) {
                this.setState(IDLE);
            } else if (this.random.nextInt(160) == 0 && v.isAlive()) {
                // pulling at the board
                this.sound(SoundEvents.WOOD_HIT, 0.7F, 0.6F + this.random.nextFloat() * 0.2F);
            }
        }
    }

    /** The executioner who locked the board (for the messages), or null. */
    public Player locker() {
        return this.operator();
    }
}
