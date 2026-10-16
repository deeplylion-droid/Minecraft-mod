package com.filorosso.client;

import com.filorosso.net.Net;
import com.filorosso.server.Events;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the server told this client (events, challenges, cooldowns, couples for the tab list). */
public final class ClientState {
    private ClientState() {
    }

    public static Net.WorldState world = Net.WorldState.NONE;
    public static Net.CoupleInfo info = Net.CoupleInfo.NONE;
    public static List<Long> cooldowns = List.of();
    public static Map<UUID, Integer> roster = new HashMap<>();
    public static List<Net.Candidate> candidates = List.of();

    public static Events.Kind event() {
        int e = world.event();
        return e >= 0 && e < Events.Kind.ALL.length ? Events.Kind.ALL[e] : null;
    }

    public static float length() {
        return world.length();
    }

    public static long readyAt(int ability) {
        return ability < cooldowns.size() ? cooldowns.get(ability) : 0L;
    }

    public static void reset() {
        world = Net.WorldState.NONE;
        info = Net.CoupleInfo.NONE;
        cooldowns = List.of();
        roster = new HashMap<>();
        candidates = List.of();
    }
}
