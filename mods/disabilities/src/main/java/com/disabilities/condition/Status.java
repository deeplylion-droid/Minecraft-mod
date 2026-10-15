package com.disabilities.condition;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Episodes in progress, as the game time they end at (0 = none), so the value only changes (and is
 * only synced) when an episode starts or stops. Everybody receives it: the others see you asleep,
 * frozen, out of breath or confused.
 */
public record Status(long sleepUntil, long bleedUntil, long freezeUntil, long windedUntil, long confusedUntil) {
    public static final Status NONE = new Status(0, 0, 0, 0, 0);

    public static final StreamCodec<ByteBuf, Status> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Status::sleepUntil,
            ByteBufCodecs.VAR_LONG, Status::bleedUntil,
            ByteBufCodecs.VAR_LONG, Status::freezeUntil,
            ByteBufCodecs.VAR_LONG, Status::windedUntil,
            ByteBufCodecs.VAR_LONG, Status::confusedUntil,
            Status::new);

    public boolean asleep(long now) {
        return now < this.sleepUntil;
    }

    public boolean bleeding(long now) {
        return now < this.bleedUntil;
    }

    public boolean frozen(long now) {
        return now < this.freezeUntil;
    }

    public boolean winded(long now) {
        return now < this.windedUntil;
    }

    public boolean confused(long now) {
        return now < this.confusedUntil;
    }

    public Status withSleep(long until) {
        return new Status(until, this.bleedUntil, this.freezeUntil, this.windedUntil, this.confusedUntil);
    }

    public Status withBleed(long until) {
        return new Status(this.sleepUntil, until, this.freezeUntil, this.windedUntil, this.confusedUntil);
    }

    public Status withFreeze(long until) {
        return new Status(this.sleepUntil, this.bleedUntil, until, this.windedUntil, this.confusedUntil);
    }

    public Status withWinded(long until) {
        return new Status(this.sleepUntil, this.bleedUntil, this.freezeUntil, until, this.confusedUntil);
    }

    public Status withConfused(long until) {
        return new Status(this.sleepUntil, this.bleedUntil, this.freezeUntil, this.windedUntil, until);
    }
}
