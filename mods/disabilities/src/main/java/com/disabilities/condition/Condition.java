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
    BLIND(0x9AA0B4, "cieco"),
    ACHROMATOPSIA(0xC8C8C8, "acromatopsia"),
    MYOPIA(0x8CC8F0, "miope"),
    ONE_EYE(0x7A7A80, "monocolo"),
    ALBINISM(0xFFE680, "albino"),
    DEAF(0xE8B496, "sordo"),
    MUTE(0xE05A70, "muto"),
    DYSLEXIA(0x5AAAFF, "dislessico"),
    WHEELCHAIR(0x2A6AE0, "sedia"),
    ONE_LEG(0xC08C50, "stampelle"),
    ONE_ARM(0x50B070, "monco"),
    DWARFISM(0xD06040, "nano"),
    OBESITY(0x4A8CD2, "obeso"),
    PARKINSON(0xFFDC3C, "parkinson"),
    NARCOLEPSY(0xAAC8FF, "narcolessia"),
    TOURETTE(0xF04A4A, "tourette"),
    AMNESIA(0xBE78FF, "amnesia"),
    VERTIGO(0x5ADCC8, "vertigini"),
    ASTHMA(0xF08296, "asma"),
    HEMOPHILIA(0xC8141E, "emofilia"),
    ALLERGY(0xFF78B4, "allergia");

    /** How many conditions every player gets. */
    public static final int PER_PLAYER = 2;

    public final int color;
    /** Short Italian name used by the /disabilita command. */
    public final String command;

    Condition(int color, String command) {
        this.color = color;
        this.command = command;
    }

    public int bit() {
        return 1 << this.ordinal();
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

    /** Finds a condition by its id or its command name. */
    public static Condition byId(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        for (Condition c : values()) {
            if (c.id().equals(key) || c.command.equals(key)) {
                return c;
            }
        }
        return null;
    }

    /** All the pairs that go together, as masks. */
    public static java.util.List<Integer> validPairs() {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        Condition[] all = values();
        for (int i = 0; i < all.length; i++) {
            for (int j = i + 1; j < all.length; j++) {
                if (all[i].compatibleWith(all[j])) {
                    out.add(all[i].bit() | all[j].bit());
                }
            }
        }
        return out;
    }

    public static Condition byOrdinal(int ordinal) {
        Condition[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }
}
