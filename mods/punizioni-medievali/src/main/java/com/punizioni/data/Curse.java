package com.punizioni.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.punizioni.witch.CurseKind;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * A witch's curse on a player, until death: which one, and since when (game time: every client
 * plays the change from it). A balloon that has been popped also knows when (it whizzes round
 * deflating, then the player dies).
 */
public record Curse(int kind, long since, long popAt) {
    public static final Codec<Curse> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("kind").forGetter(Curse::kind),
            Codec.LONG.fieldOf("since").forGetter(Curse::since),
            Codec.LONG.optionalFieldOf("pop_at", 0L).forGetter(Curse::popAt)).apply(i, Curse::new));
    public static final StreamCodec<ByteBuf, Curse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Curse::kind, ByteBufCodecs.VAR_LONG, Curse::since, ByteBufCodecs.VAR_LONG, Curse::popAt, Curse::new);

    public @Nullable CurseKind type() {
        return CurseKind.byIndex(this.kind);
    }

    public boolean is(CurseKind kind) {
        return this.kind == kind.ordinal();
    }

    public boolean popping() {
        return this.popAt > 0L;
    }
}
