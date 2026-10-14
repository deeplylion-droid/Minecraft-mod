package com.belowbedrock.world;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/** What is written on the signs of the faded world (translation keys, one per line). */
public final class Signs {
    /** Message id and number of lines; the text is in the language files: sign.belowbedrock.<id>.<line>. */
    private static final String[] IDS = {"turn_back", "never_sleeps", "counted", "no_rain", "lid", "been_here", "eyes", "stop",
            "house", "dig", "animals", "arrow", "hello", "its_me", "brb", "no_sun", "waiting", "day_one", "help", "question",
            "behind", "quiet", "again", "empty", "not_yours", "nothing_grows"};
    private static final int[] LINES = {3, 3, 4, 2, 3, 3, 3, 3, 3, 3, 2, 1, 1, 1, 2, 2, 3, 1, 1, 1, 2, 2, 2, 0, 3, 3};

    private Signs() {
    }

    public static Component[] random(RandomSource random) {
        int i = random.nextInt(IDS.length);
        return message(IDS[i], LINES[i]);
    }

    /** The four lines of a sign, vertically centred. */
    public static Component[] message(String id, int lines) {
        Component[] text = {Component.empty(), Component.empty(), Component.empty(), Component.empty()};
        int first = (4 - lines) / 2;
        for (int line = 0; line < lines; line++) {
            text[first + line] = Component.translatable("sign.belowbedrock." + id + "." + (line + 1));
        }
        return text;
    }
}
