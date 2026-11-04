package com.disabilities.condition;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Episodes in progress, as the game time they end at (0 = none), so the value only changes (and is
 * only synced) when an episode starts or stops. Everybody receives it: the others see you asleep,
 * frozen, out of breath, confused, convulsing, in cardiac arrest, panicking, with a cast on.
 * {@code flags} holds a few small states that change now and then (heart stage, anxious, swollen).
 */
public record Status(long sleepUntil, long bleedUntil, long freezeUntil, long windedUntil, long confusedUntil, long seizureUntil, long arrestUntil, long panicUntil, long brokenArmUntil, long brokenLegUntil, long glassesOffUntil, int flags) {
    public static final Status NONE = new Status(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public static final int HEART_MASK = 3;
    public static final int ANXIOUS = 4;
    public static final int SWOLLEN = 8;

    public static final StreamCodec<ByteBuf, Status> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Status::sleepUntil,
            ByteBufCodecs.VAR_LONG, Status::bleedUntil,
            ByteBufCodecs.VAR_LONG, Status::freezeUntil,
            ByteBufCodecs.VAR_LONG, Status::windedUntil,
            ByteBufCodecs.VAR_LONG, Status::confusedUntil,
            ByteBufCodecs.VAR_LONG, Status::seizureUntil,
            ByteBufCodecs.VAR_LONG, Status::arrestUntil,
            ByteBufCodecs.VAR_LONG, Status::panicUntil,
            ByteBufCodecs.VAR_LONG, Status::brokenArmUntil,
            ByteBufCodecs.VAR_LONG, Status::brokenLegUntil,
            ByteBufCodecs.VAR_LONG, Status::glassesOffUntil,
            ByteBufCodecs.VAR_INT, Status::flags,
            Status::new);

    /** Narcolepsy: asleep on the ground. */
    public boolean asleep(long now) {
        return now < this.sleepUntil;
    }

    /** Hemophilia: bleeding. */
    public boolean bleeding(long now) {
        return now < this.bleedUntil;
    }

    /** Parkinson (and amnesia, the pause to remember): the legs do not move. */
    public boolean frozen(long now) {
        return now < this.freezeUntil;
    }

    /** Obesity: out of breath. */
    public boolean winded(long now) {
        return now < this.windedUntil;
    }

    /** Amnesia and after a seizure: confused. */
    public boolean confused(long now) {
        return now < this.confusedUntil;
    }

    /** Epilepsy: a seizure, on the ground. */
    public boolean seizing(long now) {
        return now < this.seizureUntil;
    }

    /** Weak heart: cardiac arrest; dead at this time unless someone does CPR. */
    public boolean arrested(long now) {
        return now < this.arrestUntil;
    }

    /** Claustrophobia: a panic attack. */
    public boolean panicking(long now) {
        return now < this.panicUntil;
    }

    /** Brittle bones: the main arm is broken. */
    public boolean brokenArm(long now) {
        return now < this.brokenArmUntil;
    }

    /** Brittle bones: a leg is broken. */
    public boolean brokenLeg(long now) {
        return now < this.brokenLegUntil;
    }

    /** Myopia: the glasses fell off. */
    public boolean glassesOff(long now) {
        return now < this.glassesOffUntil;
    }

    /** Weak heart: 0 normal, 1 racing, 2 critical. */
    public int heartStage() {
        return this.flags & HEART_MASK;
    }

    public boolean anxious() {
        return (this.flags & ANXIOUS) != 0;
    }

    public boolean swollen() {
        return (this.flags & SWOLLEN) != 0;
    }

    public Status withFlag(int flag, boolean on) {
        return this.withFlags(on ? this.flags | flag : this.flags & ~flag);
    }

    public Status withHeartStage(int stage) {
        return this.withFlags((this.flags & ~HEART_MASK) | (stage & HEART_MASK));
    }

    public Status withSleep(long value) {
        return new Status(value, this.bleedUntil, this.freezeUntil, this.windedUntil, this.confusedUntil, this.seizureUntil, this.arrestUntil, this.panicUntil, this.brokenArmUntil, this.brokenLegUntil, this.glassesOffUntil, this.flags);
    }

    public Status withBleed(long value) {
        return new Status(this.sleepUntil, value, this.freezeUntil, this.windedUntil, this.confusedUntil, this.seizureUntil, this.arrestUntil, this.panicUntil, this.brokenArmUntil, this.brokenLegUntil, this.glassesOffUntil, this.flags);
    }

    public Status withFreeze(long value) {
        return new Status(this.sleepUntil, this.bleedUntil, value, this.windedUntil, this.confusedUntil, this.seizureUntil, this.arrestUntil, this.panicUntil, this.brokenArmUntil, this.brokenLegUntil, this.glassesOffUntil, this.flags);
    }

    public Status withWinded(long value) {
        return new Status(this.sleepUntil, this.bleedUntil, this.freezeUntil, value, this.confusedUntil, this.seizureUntil, this.arrestUntil, this.panicUntil, this.brokenArmUntil, this.brokenLegUntil, this.glassesOffUntil, this.flags);
    }

    public Status withConfused(long value) {
        return new Status(this.sleepUntil, this.bleedUntil, this.freezeUntil, this.windedUntil, value, this.seizureUntil, this.arrestUntil, this.panicUntil, this.brokenArmUntil, this.brokenLegUntil, this.glassesOffUntil, this.flags);
    }

    public Status withSeizure(long value) {
        return new Status(this.sleepUntil, this.bleedUntil, this.freezeUntil, this.windedUntil, this.confusedUntil, value, this.arrestUntil, this.panicUntil, this.brokenArmUntil, this.brokenLegUntil, this.glassesOffUntil, this.flags);
    }

    public Status withArrest(long value) {
        return new Status(this.sleepUntil, this.bleedUntil, this.freezeUntil, this.windedUntil, this.confusedUntil, this.seizureUntil, value, this.panicUntil, this.brokenArmUntil, this.brokenLegUntil, this.glassesOffUntil, this.flags);
    }

    public Status withPanic(long value) {
        return new Status(this.sleepUntil, this.bleedUntil, this.freezeUntil, this.windedUntil, this.confusedUntil, this.seizureUntil, this.arrestUntil, value, this.brokenArmUntil, this.brokenLegUntil, this.glassesOffUntil, this.flags);
    }

    public Status withBrokenArm(long value) {
        return new Status(this.sleepUntil, this.bleedUntil, this.freezeUntil, this.windedUntil, this.confusedUntil, this.seizureUntil, this.arrestUntil, this.panicUntil, value, this.brokenLegUntil, this.glassesOffUntil, this.flags);
    }

    public Status withBrokenLeg(long value) {
        return new Status(this.sleepUntil, this.bleedUntil, this.freezeUntil, this.windedUntil, this.confusedUntil, this.seizureUntil, this.arrestUntil, this.panicUntil, this.brokenArmUntil, value, this.glassesOffUntil, this.flags);
    }

    public Status withGlassesOff(long value) {
        return new Status(this.sleepUntil, this.bleedUntil, this.freezeUntil, this.windedUntil, this.confusedUntil, this.seizureUntil, this.arrestUntil, this.panicUntil, this.brokenArmUntil, this.brokenLegUntil, value, this.flags);
    }

    public Status withFlags(int value) {
        return new Status(this.sleepUntil, this.bleedUntil, this.freezeUntil, this.windedUntil, this.confusedUntil, this.seizureUntil, this.arrestUntil, this.panicUntil, this.brokenArmUntil, this.brokenLegUntil, this.glassesOffUntil, value);
    }
}
