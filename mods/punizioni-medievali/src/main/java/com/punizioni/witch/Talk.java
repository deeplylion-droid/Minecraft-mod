package com.punizioni.witch;

import net.minecraft.util.RandomSource;

/** What the cursed say in the chat: the toad croaks, the head in the butt only mumbles. */
public final class Talk {
    private Talk() {
    }

    private static final String[] CROAKS = {"Cra", "Craa", "Cra-cra", "Croak", "Grrra", "Craaaa", "Rrrbit", "Cro"};
    private static final String[] MUMBLES = {"Mmf", "Mmmf", "Mfff", "Hmmf", "Mmmmh", "Mphh", "Mmf-mf", "Hnnf"};

    public static String croaks(String message, RandomSource random) {
        return replace(message, CROAKS, random);
    }

    public static String mumbles(String message, RandomSource random) {
        return replace(message, MUMBLES, random);
    }

    /** One sound per word; the end of a sentence keeps its ! or ?. */
    private static String replace(String message, String[] sounds, RandomSource random) {
        String[] words = message.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        int n = Math.max(1, Math.min(words.length, 14));
        for (int i = 0; i < n; i++) {
            String w = words[Math.min(i, words.length - 1)];
            String s = sounds[random.nextInt(sounds.length)];
            if (i > 0) {
                out.append(' ');
                s = s.toLowerCase(java.util.Locale.ROOT);
            }
            out.append(s);
            char last = w.isEmpty() ? ' ' : w.charAt(w.length() - 1);
            if (last == '!' || last == '?' || last == '.' || last == ',') {
                out.append(last);
            }
        }
        char end = out.charAt(out.length() - 1);
        if (end != '!' && end != '?' && end != '.') {
            out.append(random.nextBoolean() ? "!" : "...");
        }
        return out.toString();
    }
}
