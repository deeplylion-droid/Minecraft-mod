package com.disabilities.condition;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * The 21 disabilities a player can pick. Each one has an icon (a glyph of the badge font, U+E000 + ordinal)
 * and a colour; some pairs make no sense together and are not allowed.
 */
public enum Condition {
    BLIND(0x9AA0B4),
    ACHROMATOPSIA(0xC8C8C8),
    MYOPIA(0x8CC8F0),
    ONE_EYE(0x7A7A80),
    ALBINISM(0xFFE680),
    DEAF(0xE8B496),
    MUTE(0xE05A70),
    DYSLEXIA(0x5AAAFF),
    WHEELCHAIR(0x2A6AE0),
    ONE_LEG(0xC08C50),
    ONE_ARM(0x50B070),
    DWARFISM(0xD06040),
    OBESITY(0x4A8CD2),
    PARKINSON(0xFFDC3C),
    NARCOLEPSY(0xAAC8FF),
    TOURETTE(0xF04A4A),
    AMNESIA(0xBE78FF),
    VERTIGO(0x5ADCC8),
    ASTHMA(0xF08296),
    HEMOPHILIA(0xC8141E),
    ALLERGY(0xFF78B4);

    /** How many conditions every player gets. */
    public static final int PER_PLAYER = 2;

    public final int color;

    Condition(int color) {
        this.color = color;
    }

    /** Lower-case id used in commands, translation keys and textures. */
    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** The icon as a character of the badge font. */
    public char glyph() {
        return (char) (0xE000 + this.ordinal());
    }

    public MutableComponent displayName() {
        return Component.translatable("disabilities.condition." + this.id());
    }

    public MutableComponent forYou() {
        return Component.translatable("disabilities.condition." + this.id() + ".you");
    }

    public MutableComponent forOthers() {
        return Component.translatable("disabilities.condition." + this.id() + ".others");
    }

    private static final Set<Condition> SIGHT = EnumSet.of(ACHROMATOPSIA, MYOPIA, ONE_EYE, ALBINISM, DYSLEXIA);

    /** False for pairs that cannot go together (blind and short-sighted, wheelchair and crutches...). */
    public boolean compatibleWith(Condition other) {
        if (this == other) {
            return false;
        }
        return !excludes(this, other) && !excludes(other, this);
    }

    private static boolean excludes(Condition a, Condition b) {
        return switch (a) {
            // nothing left to see for the other eye conditions, nothing to read for dyslexia
            case BLIND -> SIGHT.contains(b);
            // the wheelchair replaces the crutches
            case WHEELCHAIR -> b == ONE_LEG;
            // crutches need both arms
            case ONE_LEG -> b == ONE_ARM;
            default -> false;
        };
    }

    public static Condition byId(String id) {
        for (Condition c : values()) {
            if (c.id().equals(id.toLowerCase(Locale.ROOT))) {
                return c;
            }
        }
        return null;
    }

    public static Condition byOrdinal(int ordinal) {
        Condition[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }
}
