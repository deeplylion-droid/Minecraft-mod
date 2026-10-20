package com.disabilities;

import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;

/** Hooks the client fills in, so common code (items) can trigger client-only effects. */
public final class Sided {
    private Sided() {
    }

    /** The white cane was used by the local player: show the surroundings for a moment. */
    public static Consumer<Player> sonar = player -> {
    };
}
