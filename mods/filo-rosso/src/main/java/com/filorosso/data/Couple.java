package com.filorosso.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Two players tied by the red thread. Lives in the {@link CoupleBook}; changed only on the server. */
public final class Couple {
    public static final Codec<Couple> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(c -> c.id),
            UUIDUtil.CODEC.fieldOf("a").forGetter(c -> c.a),
            UUIDUtil.CODEC.fieldOf("b").forGetter(c -> c.b),
            Codec.STRING.fieldOf("name_a").forGetter(c -> c.nameA),
            Codec.STRING.fieldOf("name_b").forGetter(c -> c.nameB),
            Codec.INT.fieldOf("color").forGetter(c -> c.color),
            Codec.INT.fieldOf("xp").forGetter(c -> c.xp),
            Codec.INT.fieldOf("type").forGetter(c -> c.type),
            Codec.INT.fieldOf("type_session").forGetter(c -> c.typeSession),
            Codec.INT.fieldOf("points").forGetter(c -> c.points),
            Codec.LONG.fieldOf("since").forGetter(c -> c.since),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("progress", Map.of()).forGetter(c -> c.progress),
            Codec.STRING.listOf().optionalFieldOf("done", List.of()).forGetter(c -> new ArrayList<>(c.done)),
            Codec.INT.optionalFieldOf("hits", 0).forGetter(c -> c.bossHits)
    ).apply(i, Couple::new));

    public final int id;
    public final UUID a;
    public final UUID b;
    public String nameA;
    public String nameB;
    public int color;
    public int xp;
    public int type;
    /** The session in which {@link #type} was drawn. */
    public int typeSession;
    public int points;
    public long since;
    /** Progress of this session's challenges. */
    public final Map<String, Integer> progress = new HashMap<>();
    /** Challenges this couple finished this session. */
    public final Set<String> done = new HashSet<>();
    /** Couple hits on Recisione (for the final reward). */
    public int bossHits;
    /** Game time until which the thread is cut (not saved). */
    public long cutUntil;

    public Couple(int id, UUID a, UUID b, String nameA, String nameB, int color, int xp, int type, int typeSession, int points,
                  long since, Map<String, Integer> progress, List<String> done, int bossHits) {
        this.id = id;
        this.a = a;
        this.b = b;
        this.nameA = nameA;
        this.nameB = nameB;
        this.color = color;
        this.xp = xp;
        this.type = type;
        this.typeSession = typeSession;
        this.points = points;
        this.since = since;
        this.progress.putAll(progress);
        this.done.addAll(done);
        this.bossHits = bossHits;
    }

    public boolean has(UUID id) {
        return this.a.equals(id) || this.b.equals(id);
    }

    public UUID other(UUID id) {
        return this.a.equals(id) ? this.b : this.a;
    }

    public String nameOf(UUID id) {
        return this.a.equals(id) ? this.nameA : this.nameB;
    }

    public void setName(UUID id, String name) {
        if (this.a.equals(id)) {
            this.nameA = name;
        } else if (this.b.equals(id)) {
            this.nameB = name;
        }
    }

    public BondLevel level() {
        return BondLevel.forXp(this.xp);
    }

    public BondType bondType() {
        return BondType.byOrdinal(this.type);
    }

    /** "Mario ❤ Peach", for the leaderboard. */
    public String label() {
        return this.nameA + " ❤ " + this.nameB;
    }
}
