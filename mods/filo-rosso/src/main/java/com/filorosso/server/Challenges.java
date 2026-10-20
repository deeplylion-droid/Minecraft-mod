package com.filorosso.server;

import com.filorosso.data.Couple;
import com.filorosso.data.CoupleBook;
import com.filorosso.net.Net;
import com.filorosso.sound.ModSounds;
import com.filorosso.util.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Imprese di coppia": three challenges every live, the same for all couples. The first couple to
 * finish one gets 3 points, the second 2, the others 1 (the leaderboard on the right).
 */
public final class Challenges {
    private Challenges() {
    }

    public static final String KILLS = "kills";
    public static final String CATCHES = "catches";
    public static final String FEED = "feed";
    public static final String SLEEP = "sleep";
    public static final String DIAMONDS = "diamonds";
    public static final String NETHER = "nether";
    public static final String DEEP = "deep";
    public static final String HIGH = "high";
    public static final String BREED = "breed";
    public static final String FISH = "fish";
    public static final String THREAD_HITS = "thread_hits";
    public static final String LEVEL_UP = "level_up";
    public static final String REVIVES = "revives";
    public static final String COMBOS = "combos";
    public static final String SHARDS = "shards";

    private static final Map<String, Integer> GOALS = new java.util.LinkedHashMap<>();

    static {
        GOALS.put(KILLS, 20);
        GOALS.put(CATCHES, 3);
        GOALS.put(FEED, 6);
        GOALS.put(SLEEP, 1);
        GOALS.put(DIAMONDS, 3);
        GOALS.put(NETHER, 1);
        GOALS.put(DEEP, 1);
        GOALS.put(HIGH, 1);
        GOALS.put(BREED, 4);
        GOALS.put(FISH, 6);
        GOALS.put(THREAD_HITS, 25);
        GOALS.put(LEVEL_UP, 1);
        GOALS.put(REVIVES, 1);
        GOALS.put(COMBOS, 10);
        GOALS.put(SHARDS, 3);
    }

    public static final int PER_SESSION = 3;

    public static List<String> all() {
        return new ArrayList<>(GOALS.keySet());
    }

    public static int goal(String id) {
        return GOALS.getOrDefault(id, 1);
    }

    public static MutableComponent name(String id) {
        return Component.translatable("filorosso.challenge." + id, goal(id));
    }

    /** Stats seen last time, per player: diamonds mined, fish caught, animals bred. */
    private static final Map<UUID, int[]> LAST_STATS = new HashMap<>();
    /** Last time a couple was rewarded for sleeping together (once a night). */
    private static final Map<Integer, Long> SLEPT = new HashMap<>();

    public static void newSession(MinecraftServer server) {
        CoupleBook book = CoupleBook.get(server);
        List<String> pool = all();
        Collections.shuffle(pool, new java.util.Random(server.overworld().getRandom().nextLong()));
        book.challenges.clear();
        book.challenges.addAll(pool.subList(0, PER_SESSION));
        book.finished.clear();
        for (Couple c : book.couples) {
            c.progress.clear();
            c.done.clear();
        }
        book.setDirty();
        LAST_STATS.clear();
        announce(server);
        for (Couple c : book.couples) {
            sendInfo(server, c);
        }
        Scores.refresh(server);
    }

    public static void set(MinecraftServer server, List<String> ids) {
        CoupleBook book = CoupleBook.get(server);
        book.challenges.clear();
        book.challenges.addAll(ids);
        book.finished.clear();
        for (Couple c : book.couples) {
            c.progress.clear();
            c.done.clear();
            sendInfo(server, c);
        }
        book.setDirty();
        announce(server);
    }

    public static void announce(MinecraftServer server) {
        CoupleBook book = CoupleBook.get(server);
        if (book.challenges.isEmpty()) {
            return;
        }
        server.getPlayerList().broadcastSystemMessage(Component.empty().append(Text.icon(Text.STAR, 0xFFD030)).append(Component.literal(" "))
                .append(Component.translatable("filorosso.msg.challenges").withStyle(ChatFormatting.GOLD)), false);
        for (String id : book.challenges) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(" - ").withStyle(ChatFormatting.GRAY).append(name(id).withStyle(ChatFormatting.YELLOW)), false);
        }
    }

    public static void add(MinecraftServer server, Couple couple, String id, int amount) {
        CoupleBook book = CoupleBook.get(server);
        if (couple == null || amount <= 0 || !book.challenges.contains(id) || couple.done.contains(id) || !book.couples.contains(couple)) {
            return;
        }
        int goal = goal(id);
        int progress = Math.min(goal, couple.progress.getOrDefault(id, 0) + amount);
        couple.progress.put(id, progress);
        book.setDirty();
        if (progress >= goal) {
            couple.done.add(id);
            int rank = book.finished.merge(id, 1, Integer::sum);
            int points = rank == 1 ? 3 : rank == 2 ? 2 : 1;
            couple.points += points;
            Component names = Text.names(couple.nameA, couple.nameB, couple.color);
            server.getPlayerList().broadcastSystemMessage(Component.empty().append(Text.icon(Text.STAR, 0xFFD030)).append(Component.literal(" "))
                    .append(Component.translatable("filorosso.msg.challenge_done", names, name(id).withStyle(ChatFormatting.YELLOW), rank, points)
                            .withStyle(ChatFormatting.GOLD)), false);
            for (UUID uid : new UUID[]{couple.a, couple.b}) {
                ServerPlayer p = Bonds.player(server, uid);
                if (p != null) {
                    Bonds.sound(p, ModSounds.CHALLENGE, 1.0F);
                    Bonds.title(p, Component.translatable("filorosso.title.challenge").withStyle(ChatFormatting.GOLD),
                            name(id).withStyle(ChatFormatting.YELLOW), 5, 50, 15);
                }
            }
            Scores.refresh(server);
            for (Couple c : book.couples) {
                sendInfo(server, c);
            }
            return;
        }
        sendInfo(server, couple);
    }

    public static void onBond(MinecraftServer server, Couple couple) {
        sendInfo(server, couple);
        Scores.refresh(server);
    }

    public static void sendInfo(MinecraftServer server, Couple couple) {
        CoupleBook book = CoupleBook.get(server);
        List<Integer> progress = new ArrayList<>();
        List<Integer> goals = new ArrayList<>();
        List<Integer> done = new ArrayList<>();
        for (String id : book.challenges) {
            progress.add(couple.progress.getOrDefault(id, 0));
            goals.add(goal(id));
            done.add(couple.done.contains(id) ? 1 : 0);
        }
        Net.CoupleInfo info = new Net.CoupleInfo(couple.points, Scores.rank(server, couple), book.couples.size(), new ArrayList<>(book.challenges),
                progress, goals, done);
        for (UUID uid : new UUID[]{couple.a, couple.b}) {
            ServerPlayer p = Bonds.player(server, uid);
            if (p != null) {
                Bonds.send(p, info);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % 40 != 0) {
            return;
        }
        CoupleBook book = CoupleBook.get(server);
        if (book.challenges.isEmpty()) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Couple c = book.of(p.getUUID());
            int[] stats = {
                    p.getStats().getValue(Stats.BLOCK_MINED.get(Blocks.DIAMOND_ORE)) + p.getStats().getValue(Stats.BLOCK_MINED.get(Blocks.DEEPSLATE_DIAMOND_ORE)),
                    p.getStats().getValue(Stats.CUSTOM.get(Stats.FISH_CAUGHT)),
                    p.getStats().getValue(Stats.CUSTOM.get(Stats.ANIMALS_BRED))
            };
            int[] last = LAST_STATS.put(p.getUUID(), stats);
            if (c == null || last == null) {
                continue;
            }
            add(server, c, DIAMONDS, stats[0] - last[0]);
            add(server, c, FISH, stats[1] - last[1]);
            add(server, c, BREED, stats[2] - last[2]);
        }
        for (Couple c : book.couples) {
            ServerPlayer a = Bonds.player(server, c.a);
            ServerPlayer b = Bonds.player(server, c.b);
            if (!Links.usable(a) || !Links.usable(b)) {
                continue;
            }
            if (a.level().dimension() == Level.NETHER && b.level().dimension() == Level.NETHER) {
                add(server, c, NETHER, 1);
            }
            boolean together = a.level() == b.level() && a.distanceTo(b) < 12.0;
            if (together && a.level().dimension() == Level.OVERWORLD && a.getY() < 0 && b.getY() < 0) {
                add(server, c, DEEP, 1);
            }
            if (together && a.getY() > 150 && b.getY() > 150) {
                add(server, c, HIGH, 1);
            }
            if (together && a.isSleeping() && b.isSleeping()) {
                add(server, c, SLEEP, 1);
                Long slept = SLEPT.get(c.id);
                if (slept == null || now - slept > 12000) {
                    SLEPT.put(c.id, now);
                    Affinity.add(server, c, 15, true);
                }
            }
        }
    }

    public static void forget(ServerPlayer player) {
        LAST_STATS.remove(player.getUUID());
    }
}
