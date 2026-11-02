package com.punizioni.misdeed;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * One thing a subject did, as the king's lens shows it (repeats of the same thing within a few
 * minutes add up: "broke 34 Stone", "hit Luigi 5 times").
 */
public final class Misdeed {
    public enum Kind {
        /** Took items from a container someone else placed. */
        THEFT(2),
        /** Broke a container someone else placed. */
        BROKE_OWNED(2),
        /** Hit a player, a villager, a golem or someone else's pet. */
        HIT(2),
        /** Killed one of those. */
        KILL(2),
        /** Lit TNT. */
        TNT(2),
        /** Opened a container someone else placed (and took nothing). */
        OPENED_OWNED(1),
        FIRE(1),
        LAVA(1),
        /** Took items from a container nobody is known to own. */
        TOOK(0),
        /** Opened a container nobody is known to own. */
        OPENED(0),
        /** Broke blocks. */
        BROKE(0),
        /** Killed animals. */
        KILL_ANIMAL(0);

        /** 0 = just a fact, 1 = suspicious, 2 = guilty (the subject glows red through the lens). */
        public final int severity;

        Kind(int severity) {
            this.severity = severity;
        }

        public String key() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public final Kind kind;
    /** What repeats add up on. */
    public final String key;
    /** What it was done to (an item, a block, a player, an animal...). */
    public final Component what;
    /** Whose it was (an owner), or where it came from, or null. */
    public final @Nullable Component whose;
    /** The item shown as the misdeed's picture. */
    public final Item icon;
    public final BlockPos pos;
    public final ResourceKey<Level> dimension;
    public long time;
    public int count;

    public Misdeed(Kind kind, String key, Component what, @Nullable Component whose, Item icon, BlockPos pos, ResourceKey<Level> dimension, long time,
                   int count) {
        this.kind = kind;
        this.key = key;
        this.what = what;
        this.whose = whose;
        this.icon = icon;
        this.pos = pos;
        this.dimension = dimension;
        this.time = time;
        this.count = count;
    }
}
