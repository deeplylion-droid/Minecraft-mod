package com.punizioni.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Dung on a player: every splat that hit them and where (a part of the body, a face of it and a
 * point on that face, so every client paints it in the same place of the skin), and when the last
 * one landed (fresh dung is wet and runs). Washed off in water and in the rain, the oldest first.
 */
public record Filth(List<Integer> splats, long lastHit) {
    /** At most this many stains (a new one pushes out the oldest). */
    public static final int MAX = 24;
    /** From this many stains on, flies buzz around them. */
    public static final int FLIES = 4;
    public static final Filth CLEAN = new Filth(List.of(), 0L);

    public static final int HEAD = 0;
    public static final int BODY = 1;
    public static final int RIGHT_ARM = 2;
    public static final int LEFT_ARM = 3;
    public static final int RIGHT_LEG = 4;
    public static final int LEFT_LEG = 5;

    /** The faces of a part, as they are laid out in the skin. */
    public static final int TOP = 0;
    public static final int BOTTOM = 1;
    public static final int RIGHT = 2;
    public static final int FRONT = 3;
    public static final int LEFT = 4;
    public static final int BACK = 5;

    public static final Codec<Filth> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.listOf().fieldOf("splats").forGetter(Filth::splats),
            Codec.LONG.fieldOf("last_hit").forGetter(Filth::lastHit)).apply(i, Filth::new));
    public static final StreamCodec<ByteBuf, Filth> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Filth::splats, ByteBufCodecs.VAR_LONG, Filth::lastHit, Filth::new);

    public int stains() {
        return this.splats.size();
    }

    public boolean clean() {
        return this.splats.isEmpty();
    }

    /** One more splat (the oldest goes if there are too many). */
    public Filth splat(int packed, long now) {
        List<Integer> list = new ArrayList<>(this.splats);
        list.add(packed);
        while (list.size() > MAX) {
            list.removeFirst();
        }
        return new Filth(List.copyOf(list), now);
    }

    /** Washed: the oldest {@code amount} stains go. */
    public Filth washed(int amount) {
        if (amount >= this.splats.size()) {
            return CLEAN;
        }
        return new Filth(List.copyOf(this.splats.subList(amount, this.splats.size())), this.lastHit);
    }

    // ------------------------------------------------------------------ one splat, in an int

    /**
     * A splat: the part (0-5), the face of it (0-5), where on the face (0..1 across, 0..1 down, in
     * the skin's own directions), how big (0-3) and its own random number (0-255), for the shape.
     */
    public static int pack(int part, int face, float u, float v, int size, int seed) {
        int pu = Mth.clamp(Math.round(u * 31.0F), 0, 31);
        int pv = Mth.clamp(Math.round(v * 31.0F), 0, 31);
        return (part & 7) | (face & 7) << 3 | pu << 6 | pv << 11 | (size & 3) << 16 | (seed & 255) << 18;
    }

    public static int part(int splat) {
        return splat & 7;
    }

    public static int face(int splat) {
        return splat >> 3 & 7;
    }

    public static float u(int splat) {
        return (splat >> 6 & 31) / 31.0F;
    }

    public static float v(int splat) {
        return (splat >> 11 & 31) / 31.0F;
    }

    public static int size(int splat) {
        return splat >> 16 & 3;
    }

    public static int seed(int splat) {
        return splat >> 18 & 255;
    }
}
