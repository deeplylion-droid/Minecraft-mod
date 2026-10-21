package com.infezione.infection;

/**
 * The episodes of the infection: the server picks them, everyone around sees them (and hears them), the
 * infected player also feels them. {@code ticks} is how long the client animates them.
 */
public enum Symptom {
    /** Stage 1 on: the bite throbs, the hand goes to the neck. */
    THROB(1, 36, "morso"),
    /** Stage 2 on: a dry cough, a puff from the mouth. */
    COUGH(2, 16, "tosse"),
    /** Stage 3 on: the head and the body jerk. */
    TWITCH(3, 7, "spasmo"),
    /** Stage 3 on: the world spins (Nausea) and the infected stumbles. */
    DIZZY(3, 140, "vertigini"),
    /** Stage 4 on: a wet cough that brings up blood. */
    BLOOD_COUGH(4, 24, "sangue"),
    /** Stage 4 on: the whole body shakes, bones crack, you can barely move. */
    CONVULSION(4, 44, "convulsioni"),
    /** Stage 4 on: the eyes go dark for a moment. */
    BLACKOUT(4, 50, "svenimento"),
    /** The zombie groans. */
    GROAN(5, 30, "lamento"),
    /** The transformation into a zombie (not picked at random: it comes with stage 5). */
    TRANSFORM(5, 60, "trasformazione");

    public final int minStage;
    public final int ticks;
    /** Its name in /infezione sintomo. */
    public final String command;

    Symptom(int minStage, int ticks, String command) {
        this.minStage = minStage;
        this.ticks = ticks;
        this.command = command;
    }

    private static final Symptom[] VALUES = values();

    public static Symptom byCommand(String name) {
        for (Symptom symptom : VALUES) {
            if (symptom.command.equals(name)) {
                return symptom;
            }
        }
        return null;
    }

    public static Symptom byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : COUGH;
    }

    /**
     * An episode for an infected player at {@code stage}, from a random number between 0 and 1: the
     * worse the stage, the worse the episodes.
     */
    public static Symptom pick(int stage, double random) {
        Symptom[] pool = switch (stage) {
            case 1 -> new Symptom[]{THROB};
            case 2 -> new Symptom[]{COUGH, COUGH, THROB};
            case 3 -> new Symptom[]{COUGH, COUGH, TWITCH, TWITCH, DIZZY};
            case 4 -> new Symptom[]{BLOOD_COUGH, BLOOD_COUGH, TWITCH, CONVULSION, CONVULSION, BLACKOUT, DIZZY};
            case 5 -> new Symptom[]{GROAN};
            default -> new Symptom[]{};
        };
        return pool.length == 0 ? null : pool[Math.min(pool.length - 1, (int) (random * pool.length))];
    }
}
