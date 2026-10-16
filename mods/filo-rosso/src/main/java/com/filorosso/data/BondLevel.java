package com.filorosso.data;

import net.minecraft.network.chat.Component;

/** How strong a couple's bond is: the thread changes material and new abilities unlock. */
public enum BondLevel {
    THREAD(0, 0xD8202A),
    RIBBON(120, 0xE8406A),
    BRAID(360, 0xC03060),
    GOLD(800, 0xF0C040),
    STARS(1600, 0xFFC8F0);

    public static final BondLevel[] ALL = values();

    /** Affinity needed to reach this level. */
    public final int xp;
    public final int color;

    BondLevel(int xp, int color) {
        this.xp = xp;
        this.color = color;
    }

    /** 1 to 5. */
    public int number() {
        return this.ordinal() + 1;
    }

    public static BondLevel forXp(int xp) {
        BondLevel level = THREAD;
        for (BondLevel l : ALL) {
            if (xp >= l.xp) {
                level = l;
            }
        }
        return level;
    }

    public BondLevel next() {
        return this.ordinal() + 1 < ALL.length ? ALL[this.ordinal() + 1] : null;
    }

    public boolean atLeast(BondLevel other) {
        return this.ordinal() >= other.ordinal();
    }

    public Component displayName() {
        return Component.translatable("filorosso.level." + this.name().toLowerCase(java.util.Locale.ROOT));
    }
}
