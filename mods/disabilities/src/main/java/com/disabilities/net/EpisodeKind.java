package com.disabilities.net;

/** What the server asks a player's client to do to its own body or camera. */
public enum EpisodeKind {
    /** Allergy: head snaps down, eyes water. */
    SNEEZE,
    /** Tourette motor tics, done by the client so they look natural to everyone. */
    TIC_HEAD,
    TIC_JUMP,
    TIC_SWING,
    TIC_CROUCH,
    TIC_SPIN,
    TIC_BLINK,
    TIC_HIT,
    /** Vertigo: the camera lurches as you trip. */
    STUMBLE,
    /** Amnesia: the inventory was shuffled. */
    FORGOT,
    /** Albinism: the sun burns. */
    SUNBURN;

    public static EpisodeKind byOrdinal(int ordinal) {
        EpisodeKind[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }

    public boolean isTic() {
        return this.name().startsWith("TIC_");
    }
}
