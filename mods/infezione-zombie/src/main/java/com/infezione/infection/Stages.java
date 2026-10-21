package com.infezione.infection;

/**
 * The numbers of the infection, without Minecraft classes: what each stage takes away and how often its
 * episodes come. Stage 0 is healthy, 1 to 4 get worse with time (Morso, Febbre, Delirio, Agonia), 5 is
 * the zombie, which stays until death or the command.
 */
public final class Stages {
    public static final int HEALTHY = 0;
    public static final int ZOMBIE = 5;
    /** The last stage the antidote can still cure. */
    public static final int LAST_CURABLE = 4;

    /** Max health taken away at each stage (2 = a heart). */
    private static final double[] HEALTH_LOST = {0, 0, 0, 2, 4, 0};
    /** Walking speed taken away at each stage (0.15 = 15% slower). */
    private static final double[] SLOWER = {0, 0, 0, 0, 0.15, 0};
    /** Food exhaustion added every tick (4 = a food point, sprinting adds 0.1 per block). */
    private static final float[] EXHAUSTION = {0, 0, 0.004F, 0.006F, 0.008F, 0};
    /** Seconds between two episodes (coughs, twitches, convulsions...), shortest and longest. */
    private static final int[][] EPISODE_SECONDS = {{0, 0}, {35, 60}, {18, 35}, {12, 24}, {8, 16}, {14, 28}};

    private Stages() {
    }

    public static boolean infected(int stage) {
        return stage > HEALTHY;
    }

    /** Stages 1 to 4 get worse with time; the zombie does not. */
    public static boolean progresses(int stage) {
        return stage > HEALTHY && stage < ZOMBIE;
    }

    public static int clamp(int stage) {
        return Math.max(HEALTHY, Math.min(ZOMBIE, stage));
    }

    public static double healthLost(int stage) {
        return HEALTH_LOST[clamp(stage)];
    }

    public static double slower(int stage) {
        return SLOWER[clamp(stage)];
    }

    public static float exhaustion(int stage) {
        return EXHAUSTION[clamp(stage)];
    }

    /** Ticks until the next episode, from a random number between 0 and 1. */
    public static int ticksToNextEpisode(int stage, double random) {
        int[] range = EPISODE_SECONDS[clamp(stage)];
        return (int) Math.round((range[0] + (range[1] - range[0]) * random) * 20);
    }

    /** Ticks a stage lasts, for minutes per stage from the config. */
    public static int stageTicks(double minutes) {
        return Math.max(20, (int) Math.round(minutes * 60 * 20));
    }
}
