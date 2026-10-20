package com.disabilities.util;

/**
 * Dyslexia: shuffles the letters inside words. The first letter stays, the others move around and
 * change order every fraction of a second; b/d and p/q sometimes flip. Pure and deterministic, so the
 * width of a string and what is drawn agree within a frame.
 */
public final class Scrambler {
    private Scrambler() {
    }

    /**
     * Returns the new order of the code points: result[i] is the index of the code point to show at i.
     * {@code time} changes the shuffle (e.g. milliseconds / 450).
     */
    public static int[] order(int[] codepoints, long time) {
        int n = codepoints.length;
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        int wordIndex = 0;
        int i = 0;
        while (i < n) {
            if (!Character.isLetter(codepoints[i])) {
                i++;
                continue;
            }
            int start = i;
            while (i < n && Character.isLetter(codepoints[i])) {
                i++;
            }
            int len = i - start;
            if (len >= 3) {
                // each word changes at its own pace, so the text never settles
                long bucket = (time + wordIndex * 3L) / (2 + (wordIndex % 3));
                long seed = bucket * 31 + wordIndex * 7919L + len;
                for (int k = start + len - 1; k > start + 1; k--) {
                    int j = start + 1 + (int) Math.floorMod(mix(seed, k), (long) (k - start));
                    int t = order[k];
                    order[k] = order[j];
                    order[j] = t;
                }
            }
            wordIndex++;
        }
        return order;
    }

    /** Sometimes shows a letter mirrored (b/d, p/q, n/u, m/w), like a dyslexic reader sees it. */
    public static int mirror(int codepoint, int position, long time) {
        if (Math.floorMod(mix(time / 3, position), 9L) != 0) {
            return codepoint;
        }
        return switch (codepoint) {
            case 'b' -> 'd';
            case 'd' -> 'b';
            case 'p' -> 'q';
            case 'q' -> 'p';
            case 'n' -> 'u';
            case 'u' -> 'n';
            case 'm' -> 'w';
            case 'w' -> 'm';
            default -> codepoint;
        };
    }

    /** Swaps two letters in some words: what a dyslexic player's chat messages look like to the others. */
    public static String typos(String message, long seed) {
        StringBuilder out = new StringBuilder(message);
        int word = 0;
        int i = 0;
        while (i < out.length()) {
            if (!Character.isLetter(out.charAt(i))) {
                i++;
                continue;
            }
            int start = i;
            while (i < out.length() && Character.isLetter(out.charAt(i))) {
                i++;
            }
            int len = i - start;
            if (len >= 3 && Math.floorMod(mix(seed, word), 100L) < 55) {
                int a = start + 1 + (int) Math.floorMod(mix(seed + 1, word), (long) (len - 2));
                char t = out.charAt(a);
                out.setCharAt(a, out.charAt(a + 1));
                out.setCharAt(a + 1, t);
            }
            word++;
        }
        return out.toString();
    }

    private static long mix(long seed, long i) {
        long z = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return z ^ (z >>> 33);
    }
}
