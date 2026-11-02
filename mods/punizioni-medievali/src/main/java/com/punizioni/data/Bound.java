package com.punizioni.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

/**
 * A prisoner's bonds: hands tied with the prisoner's rope, and who holds the other end (the captor,
 * or a fence post the rope was knotted to, with the captor remembered). Saved, gone on death, known
 * by every client (they draw the rope and the tied hands).
 *
 * @param captor who tied them (and leads them, unless knotted to a post)
 * @param knot   the fence post the rope is knotted to, if any
 * @param since  game time they were tied (the knot animation)
 */
public record Bound(UUID captor, Optional<BlockPos> knot, long since) {
    public static final Codec<Bound> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("captor").forGetter(Bound::captor),
            BlockPos.CODEC.optionalFieldOf("knot").forGetter(Bound::knot),
            Codec.LONG.optionalFieldOf("since", 0L).forGetter(Bound::since)
    ).apply(i, Bound::new));

    public static final StreamCodec<ByteBuf, Bound> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Bound::captor,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Bound::knot,
            ByteBufCodecs.VAR_LONG, Bound::since,
            Bound::new);

    public Bound led(UUID by) {
        return new Bound(by, Optional.empty(), this.since);
    }

    public Bound knotted(BlockPos post) {
        return new Bound(this.captor, Optional.of(post.immutable()), this.since);
    }
}
