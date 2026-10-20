package com.disabilities.gesture;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/** The signs a mute player can make. Each one has an icon in the badge font (U+E100 + ordinal). */
public enum Gesture {
    HELLO, YES, NO, COME, HELP, DANGER, THANKS, FOLLOW;

    /** How long the gesture (arm animation and bubble) lasts, in ticks. */
    public static final int DURATION = 50;

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public char glyph() {
        return (char) (0xE100 + this.ordinal());
    }

    public MutableComponent displayName() {
        return Component.translatable("disabilities.gesture." + this.id());
    }

    public static Gesture byOrdinal(int ordinal) {
        Gesture[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }
}
