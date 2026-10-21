package com.infezione.infection;

/** Why a player's infection changed, so the clients know what to show. */
public enum Change {
    /** Nothing new: the state again (every second to the player, or to someone who just came in sight). */
    NONE,
    /** Bitten: the command infected them. */
    INFECTED,
    /** The infection got worse by itself. */
    WORSE,
    /** The command put them at a stage. */
    SET,
    /** Cured by the antidote or the command. */
    CURED,
    /** Died: the infection is gone. */
    DIED,
    /** The clock stopped or started again. */
    PAUSE;

    private static final Change[] VALUES = values();

    public static Change byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }
}
