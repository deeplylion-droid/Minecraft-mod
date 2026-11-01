package com.punizioni.data;

import java.util.Locale;

/** What the executioner's axe can take: hands and feet. */
public enum Limb {
    RIGHT_HAND("mano_destra"),
    LEFT_HAND("mano_sinistra"),
    RIGHT_FOOT("piede_destro"),
    LEFT_FOOT("piede_sinistro");

    public static final Limb[] ALL = values();

    /** The name in commands. */
    public final String command;

    Limb(String command) {
        this.command = command;
    }

    public int bit() {
        return 1 << this.ordinal();
    }

    public boolean hand() {
        return this == RIGHT_HAND || this == LEFT_HAND;
    }

    public boolean right() {
        return this == RIGHT_HAND || this == RIGHT_FOOT;
    }

    public String key() {
        return "punizioni.limb." + this.name().toLowerCase(Locale.ROOT);
    }

    public static Limb byCommand(String name) {
        for (Limb l : ALL) {
            if (l.command.equals(name)) {
                return l;
            }
        }
        return null;
    }

    public Limb next() {
        return ALL[(this.ordinal() + 1) % ALL.length];
    }
}
