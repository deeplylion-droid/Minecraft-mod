package com.filorosso.data;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.util.Locale;

/** The couple's fate for this live (session): drawn again every time the server starts. */
public enum BondType {
    CLASSIC("classico", 0xD8202A, 1),
    ELASTIC("elastico", 0x40C8F0, 3),
    FIRE("fuoco", 0xFF7020, 3),
    MAGNET("magnetico", 0xA060FF, 3),
    SWAP("scambio", 0x40E080, 3),
    JEALOUS("geloso", 0x70D020, 3),
    SHIELD("scudo", 0x60A0FF, 3),
    /** What one of you drinks or catches, the other feels too: potions, poisons, golden apples. */
    TWINS("gemelli", 0xF080FF, 3);

    public static final BondType[] ALL = values();

    public final String command;
    public final int color;
    private final int weight;

    BondType(String command, int color, int weight) {
        this.command = command;
        this.color = color;
        this.weight = weight;
    }

    public static BondType byOrdinal(int i) {
        return i >= 0 && i < ALL.length ? ALL[i] : CLASSIC;
    }

    public static BondType byCommand(String s) {
        for (BondType t : ALL) {
            if (t.command.equals(s) || t.name().equalsIgnoreCase(s)) {
                return t;
            }
        }
        return null;
    }

    /** A random fate, never the same as {@code previous} when possible. */
    public static BondType draw(RandomSource random, BondType previous) {
        int total = 0;
        for (BondType t : ALL) {
            total += t == previous ? 0 : t.weight;
        }
        int r = random.nextInt(total);
        for (BondType t : ALL) {
            if (t == previous) {
                continue;
            }
            r -= t.weight;
            if (r < 0) {
                return t;
            }
        }
        return CLASSIC;
    }

    public String key() {
        return "filorosso.type." + this.name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable(this.key());
    }

    public Component description() {
        return Component.translatable(this.key() + ".desc");
    }
}
