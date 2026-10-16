package com.filorosso.server;

import com.filorosso.data.Couple;
import com.filorosso.data.CoupleBook;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.HashSet;
import java.util.Set;

/** The couples' leaderboard, on the right of the screen (vanilla sidebar scoreboard). */
public final class Scores {
    private Scores() {
    }

    public static final String OBJECTIVE = "filorosso";

    public static void refresh(MinecraftServer server) {
        Scoreboard board = server.getScoreboard();
        Objective obj = board.getObjective(OBJECTIVE);
        if (obj == null) {
            obj = board.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY,
                    Component.literal("❤ ").withStyle(ChatFormatting.RED).append(Component.translatable("filorosso.leaderboard").withStyle(ChatFormatting.LIGHT_PURPLE))
                            .append(Component.literal(" ❤").withStyle(ChatFormatting.RED)),
                    ObjectiveCriteria.RenderType.INTEGER, true, null);
            board.setDisplayObjective(DisplaySlot.SIDEBAR, obj);
        }
        Set<String> wanted = new HashSet<>();
        for (Couple c : CoupleBook.get(server).couples) {
            String label = c.label();
            wanted.add(label);
            board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(label), obj).set(c.points);
        }
        for (PlayerScoreEntry e : board.listPlayerScores(obj)) {
            if (!wanted.contains(e.owner())) {
                board.resetSinglePlayerScore(ScoreHolder.forNameOnly(e.owner()), obj);
            }
        }
    }

    /** 1 = first. */
    public static int rank(MinecraftServer server, Couple couple) {
        int rank = 1;
        for (Couple c : CoupleBook.get(server).couples) {
            if (c != couple && c.points > couple.points) {
                rank++;
            }
        }
        return rank;
    }
}
