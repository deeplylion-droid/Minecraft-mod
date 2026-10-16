package com.filorosso.data;

import com.filorosso.FiloRosso;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Every couple of the world, the current session and its challenges. Saved with the overworld. */
public final class CoupleBook extends SavedData {
    public static final Codec<CoupleBook> CODEC = RecordCodecBuilder.create(i -> i.group(
            Couple.CODEC.listOf().optionalFieldOf("couples", List.of()).forGetter(b -> b.couples),
            Codec.INT.optionalFieldOf("session", 0).forGetter(b -> b.session),
            Codec.INT.optionalFieldOf("next_id", 1).forGetter(b -> b.nextId),
            Codec.STRING.listOf().optionalFieldOf("challenges", List.of()).forGetter(b -> b.challenges),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("finished", Map.of()).forGetter(b -> b.finished),
            Codec.BOOL.optionalFieldOf("boss_beaten", false).forGetter(b -> b.bossBeaten)
    ).apply(i, CoupleBook::new));

    public static final SavedDataType<CoupleBook> TYPE = new SavedDataType<>(FiloRosso.id("couples"), CoupleBook::new, CODEC, null);

    public final List<Couple> couples = new ArrayList<>();
    public int session;
    public int nextId = 1;
    /** This session's challenges. */
    public final List<String> challenges = new ArrayList<>();
    /** How many couples finished each challenge (the first ones get more points). */
    public final Map<String, Integer> finished = new HashMap<>();
    public boolean bossBeaten;

    public CoupleBook() {
    }

    private CoupleBook(List<Couple> couples, int session, int nextId, List<String> challenges, Map<String, Integer> finished, boolean bossBeaten) {
        this.couples.addAll(couples);
        this.session = session;
        this.nextId = nextId;
        this.challenges.addAll(challenges);
        this.finished.putAll(finished);
        this.bossBeaten = bossBeaten;
    }

    public static CoupleBook get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Couple of(UUID player) {
        for (Couple c : this.couples) {
            if (c.has(player)) {
                return c;
            }
        }
        return null;
    }

    public Couple byId(int id) {
        for (Couple c : this.couples) {
            if (c.id == id) {
                return c;
            }
        }
        return null;
    }

    /** The first colour no couple is using. */
    public int freeColor() {
        for (int color = 0; color < Palette.COLORS.length; color++) {
            int c = color;
            if (this.couples.stream().noneMatch(x -> x.color == c)) {
                return color;
            }
        }
        return this.couples.size() % Palette.COLORS.length;
    }
}
