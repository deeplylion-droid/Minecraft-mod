package com.jumpscares.scare;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;

import java.util.Locale;

/** The five monsters. The timings are shared by the server (when to scream) and the client (what to draw). */
public enum ScareKind {
    /** Springs up like a jack-in-the-box, laughing. */
    CLOWN("clown", "clown", 0xE8102A, 5),
    /** The White Lady: skips closer between glitches, then her jaw drops. */
    LADY("lady", "dama", 0xDCE6FF, 6),
    /** Drops from the ceiling on a thread. */
    SPIDER("spider", "ragno", 0x9A0A14, 6),
    /** Lurches at you like a broken stop-motion puppet. */
    CORPSE("corpse", "cadavere", 0x7A9A3A, 4),
    /** Rises from below in a roar of fire. */
    DEMON("demon", "demone", 0xFF5A00, 7);

    /** Ticks the monster spends in your face before the last lunge. */
    public static final int HOLD = 18;
    /** Ticks of the last lunge into your face. */
    public static final int LUNGE = 3;
    /** Ticks of black (and fading back) at the end. */
    public static final int BLACK = 13;

    /** Resource name (models, textures, sounds, items). */
    public final String id;
    /** Name used by /jumpscare (Italian, like the rest of the user's commands). */
    public final String command;
    /** Colour of the flash when it hits. */
    public final int color;
    /** Tick at which the monster reaches your face. */
    public final int impactTick;

    ScareKind(String id, String command, int color, int impactTick) {
        this.id = id;
        this.command = command;
        this.color = color;
        this.impactTick = impactTick;
    }

    /** Tick at which the last lunge starts. */
    public int lungeTick() {
        return this.impactTick + HOLD;
    }

    /** Tick at which the screen is completely black. */
    public int blackTick() {
        return this.lungeTick() + LUNGE;
    }

    /** Total length of the scare in ticks. */
    public int length() {
        return this.blackTick() + BLACK;
    }

    public MutableComponent displayName() {
        return Component.translatable("jumpscares.kind." + this.id);
    }

    public static ScareKind byId(int ordinal) {
        ScareKind[] all = values();
        return all[Math.floorMod(ordinal, all.length)];
    }

    /** Accepts the Italian command name or the English id; null if unknown. */
    public static ScareKind byName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (ScareKind kind : values()) {
            if (kind.command.equals(n) || kind.id.equals(n)) {
                return kind;
            }
        }
        return null;
    }

    public static ScareKind random(RandomSource random) {
        return byId(random.nextInt(values().length));
    }
}
