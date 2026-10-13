package com.rainbowyoyo.motion;

/**
 * What the yo-yo is doing. The server decides the phase and sends it to every client, which then
 * replays the same motion locally (see {@link YoyoMotion}), so everybody sees the same smooth trick.
 */
public enum YoyoPhase {
    /** Quick shot with the attack key: straight out at the target and back into the hand. */
    SHOT(12, false, "shot"),
    /** Thrown with the use key: flies out to the end of the string. */
    THROW(5, false, "throw"),
    /** Spinning at the end of the string while the use key is held, following the crosshair. */
    SLEEP(0, false, "sleep"),
    /** Winding back into the hand. */
    RETURN(0, false, "return"),
    /** Three giant vertical loops in front of the player. */
    LOOP_THE_LOOP(28, true, "loop_the_loop"),
    /** Two fast horizontal laps around the player, like a buzz saw. */
    AROUND_THE_WORLD(30, true, "around_the_world"),
    /** Drops to the ground and rolls forward and back like a wheel. */
    WALK_THE_DOG(36, true, "walk_the_dog"),
    /** Shoots up into the sky and slams down in front of the player with a shockwave. */
    SHOOT_THE_MOON(34, true, "shoot_the_moon"),
    /** Combo finisher: grows, charges up and explodes into rainbow fireworks. */
    SUPERNOVA(50, true, "supernova");

    /** The four tricks that, all done in one combo, unlock the {@link #SUPERNOVA}. */
    public static final YoyoPhase[] BASIC_TRICKS = {LOOP_THE_LOOP, AROUND_THE_WORLD, WALK_THE_DOG, SHOOT_THE_MOON};

    /** Ticks the phase lasts (0 = until something ends it). */
    public final int duration;
    public final boolean trick;
    /** Used for translation keys ({@code trick.rainbowyoyo.<name>}). */
    public final String key;

    YoyoPhase(int duration, boolean trick, String key) {
        this.duration = duration;
        this.trick = trick;
        this.key = key;
    }

    /** Bit of this trick in the combo mask. */
    public int bit() {
        return 1 << ordinal();
    }

    public static int basicTricksMask() {
        int mask = 0;
        for (YoyoPhase trick : BASIC_TRICKS) {
            mask |= trick.bit();
        }
        return mask;
    }

    public static YoyoPhase byId(int id) {
        YoyoPhase[] values = values();
        return id >= 0 && id < values.length ? values[id] : SLEEP;
    }
}
