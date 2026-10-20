package com.filorosso.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Things happening to a player right now, known by every client (not saved).
 *
 * @param downedUntil game time at which a downed player dies if nobody revives them (0 = standing)
 * @param revive      revive progress, 0 to {@link #REVIVE_TICKS}
 */
public record Status(long downedUntil, int revive) {
    public static final Status NONE = new Status(0L, 0);
    public static final int REVIVE_TICKS = 50;

    public static final StreamCodec<ByteBuf, Status> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Status::downedUntil,
            ByteBufCodecs.VAR_INT, Status::revive,
            Status::new);

    public boolean downed() {
        return this.downedUntil > 0;
    }
}
