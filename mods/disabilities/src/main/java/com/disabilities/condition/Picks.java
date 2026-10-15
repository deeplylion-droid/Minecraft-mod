package com.disabilities.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * The conditions a player has (a bit per {@link Condition}) and whether the choice is done.
 * {@code settled} is false until the player has picked (or an operator exempted them).
 */
public record Picks(int mask, boolean settled) {
    public static final Picks NONE = new Picks(0, false);

    /** Saved by name, so adding conditions later does not scramble old worlds. */
    public static final Codec<Picks> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("conditions").forGetter(p -> p.list().stream().map(Condition::id).toList()),
            Codec.BOOL.optionalFieldOf("settled", false).forGetter(Picks::settled)
    ).apply(i, (ids, settled) -> {
        int mask = 0;
        for (String id : ids) {
            Condition c = Condition.byId(id);
            if (c != null) {
                mask |= c.bit();
            }
        }
        return new Picks(mask, settled);
    }));

    public static final StreamCodec<ByteBuf, Picks> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Picks::mask,
            ByteBufCodecs.BOOL, Picks::settled,
            Picks::new);

    public boolean has(Condition condition) {
        return (this.mask & condition.bit()) != 0;
    }

    public boolean isEmpty() {
        return this.mask == 0;
    }

    public List<Condition> list() {
        return list(this.mask);
    }

    public static List<Condition> list(int mask) {
        List<Condition> out = new ArrayList<>();
        for (Condition c : Condition.values()) {
            if ((mask & c.bit()) != 0) {
                out.add(c);
            }
        }
        return out;
    }

    public static int maskOf(Iterable<Condition> conditions) {
        int mask = 0;
        for (Condition c : conditions) {
            mask |= c.bit();
        }
        return mask;
    }

    /** True if the mask is a valid pick: exactly {@link Condition#PER_PLAYER} conditions that go together. */
    public static boolean isValidChoice(int mask) {
        List<Condition> list = list(mask);
        if (list.size() != Condition.PER_PLAYER) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (!list.get(i).compatibleWith(list.get(j))) {
                    return false;
                }
            }
        }
        return true;
    }
}
