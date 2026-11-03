package com.punizioni.witch;

import java.util.UUID;

/**
 * When the cursed do things by themselves: the toad croaks, the head in the butt grunts and gets a
 * fart in the face. Worked out from the player's id and the game time, so the server (sounds) and
 * every client (the throat swelling, the cheeks quivering) agree without a packet.
 */
public final class CurseClock {
    private CurseClock() {
    }

    public static final int CROAK = 1;
    public static final int GRUNT = 2;
    public static final int FART = 3;

    /** Seconds between two, on average. */
    private static int every(int what) {
        return switch (what) {
            case CROAK -> 6;
            case GRUNT -> 11;
            default -> 28;
        };
    }

    /** Whether one starts at this tick. */
    public static boolean at(UUID id, long tick, int what) {
        long window = Math.floorDiv(tick, 20L);
        int h = mix(id, window * 31L + what);
        return h % every(what) == 0 && Math.floorMod(tick, 20L) == (h >>> 9) % 20;
    }

    /** Ticks since the last one started (at most {@code max} back), or -1. */
    public static int since(UUID id, long tick, int what, int max) {
        for (int d = 0; d <= max; d++) {
            if (at(id, tick - d, what)) {
                return d;
            }
        }
        return -1;
    }

    private static int mix(UUID id, long salt) {
        long h = id.getMostSignificantBits() * 31L ^ id.getLeastSignificantBits() ^ salt * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (int) h & 0x7FFFFFFF;
    }
}
