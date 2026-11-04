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
    SUNBURN,
    // 1.1.0
    /** Epilepsy: the warning before a seizure (the world wobbles). */
    AURA,
    /** Alien hand: your own hand hits your face. */
    SLAPPED,
    /** Alien hand: it grabs your head and turns it. */
    PULLED,
    /** Gigantism, blind without the cane: you hit your head. */
    BONK,
    /** Brittle bones: crack. */
    FRACTURE,
    /** Narcolepsy: the eyes close for a moment. */
    NOD,
    /** Amnesia: where am I? The eyes wander. */
    LOST,
    /** Asthma: a coughing fit. */
    COUGH,
    /** Weak heart: someone brought you back. */
    REVIVED;

    public static EpisodeKind byOrdinal(int ordinal) {
        EpisodeKind[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }

    public boolean isTic() {
        return this.name().startsWith("TIC_");
    }
}
