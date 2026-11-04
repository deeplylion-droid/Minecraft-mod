package com.disabilities.net;

/**
 * Short body movements everybody sees on a player (the server sends them to all who can see):
 * what the alien hand does, CPR, a deaf player cupping an ear, scratching, the inhaler, nodding
 * off, yawning, coughing, looking around.
 */
public enum MotionKind {
    /** Alien hand: slaps its owner's face. */
    ALIEN_SLAP(14),
    /** Alien hand: throws what the player holds. */
    ALIEN_THROW(12),
    /** Alien hand: punches forward. */
    ALIEN_PUNCH(10),
    /** Alien hand: reaches out and knocks something out of someone's hand. */
    ALIEN_GRAB(16),
    /** Alien hand: waves at nobody. */
    ALIEN_WAVE(40),
    /** Chest compressions on someone in cardiac arrest. */
    CPR(10),
    /** Deaf: a hand cupped behind the ear. */
    EAR(40),
    /** Allergy: rubbing the itchy eyes and nose. */
    ITCH(40),
    /** Asthma: the inhaler to the mouth. */
    INHALER(24),
    /** Narcolepsy: the head drops for a moment. */
    NOD(30),
    /** Narcolepsy: a big yawn. */
    YAWN(40),
    /** Asthma: a coughing fit. */
    COUGH(30),
    /** Amnesia and claustrophobia: looking around, lost. */
    LOOK_AROUND(60);

    /** How long it lasts, in ticks. */
    public final int ticks;

    MotionKind(int ticks) {
        this.ticks = ticks;
    }

    public static MotionKind byOrdinal(int ordinal) {
        MotionKind[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }
}
