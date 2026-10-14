package com.belowbedrock.world;

import com.belowbedrock.BelowBedrock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** The faded dimension: same seed and generator as the overworld, drained of colour and of life. */
public final class Faded {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, BelowBedrock.id("faded"));

    private Faded() {
    }

    public static boolean is(Level level) {
        return level != null && level.dimension() == LEVEL;
    }

    /** The dimensions whose void does not kill: it lets you fall through instead. */
    public static boolean hasDeepVoid(Level level) {
        return level.dimension() == Level.OVERWORLD || is(level);
    }
}
