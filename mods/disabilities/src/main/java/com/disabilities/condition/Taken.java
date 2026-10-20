package com.disabilities.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who has which conditions in this world, online or not: with the "unique" game rule two players
 * cannot share a condition, even if one of them is offline. Saved on the overworld.
 */
public record Taken(Map<UUID, Entry> entries) {
    public static final Taken EMPTY = new Taken(Map.of());

    public record Entry(String name, int mask) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Entry::name),
                Picks.CODEC.fieldOf("picks").forGetter(e -> new Picks(e.mask(), true))
        ).apply(i, (name, picks) -> new Entry(name, picks.mask())));
    }

    public static final Codec<Taken> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC).xmap(Taken::new, Taken::entries);

    /** Conditions held by everybody except {@code player}. */
    public int maskExcept(UUID player) {
        int mask = 0;
        for (Map.Entry<UUID, Entry> e : this.entries.entrySet()) {
            if (!e.getKey().equals(player)) {
                mask |= e.getValue().mask();
            }
        }
        return mask;
    }

    public Taken with(UUID player, String name, int mask) {
        Map<UUID, Entry> copy = new HashMap<>(this.entries);
        if (mask == 0) {
            copy.remove(player);
        } else {
            copy.put(player, new Entry(name, mask));
        }
        return new Taken(Map.copyOf(copy));
    }
}
