package com.punizioni.witch;

import net.minecraft.world.entity.EntityDimensions;

/**
 * The bodies the curses give, in blocks: the hitbox and the eyes (both sides use them: the server
 * for collisions and reach, the client for the camera and the drawing).
 */
public final class CurseShapes {
    private CurseShapes() {
    }

    /** The toad: a big squat thing, the eyes on top of its head. */
    public static final float TOAD_WIDTH = 0.85F;
    public static final float TOAD_HEIGHT = 0.62F;
    public static final float TOAD_EYES = 0.5F;

    /** The head in the butt: no head on the shoulders, the eyes at the height of the hips. */
    public static final float BUTT_HEIGHT = 1.42F;
    public static final float BUTT_EYES = 0.8F;

    /** The balloon: a round body 1.6 blocks across, the little head on top, the legs dangling under it. */
    public static final float BALLOON_WIDTH = 1.5F;
    public static final float BALLOON_HEIGHT = 2.4F;
    public static final float BALLOON_EYES = 2.12F;
    /** How high over the ground a balloon floats (the feet). */
    public static final float BALLOON_HOVER = 2.4F;

    public static EntityDimensions dimensions(CurseKind kind, boolean crouching) {
        return switch (kind) {
            case TOAD -> EntityDimensions.scalable(TOAD_WIDTH, TOAD_HEIGHT).withEyeHeight(TOAD_EYES);
            case BUTT -> crouching
                    ? EntityDimensions.scalable(0.6F, BUTT_HEIGHT - 0.2F).withEyeHeight(BUTT_EYES - 0.14F)
                    : EntityDimensions.scalable(0.6F, BUTT_HEIGHT).withEyeHeight(BUTT_EYES);
            case BALLOON -> EntityDimensions.scalable(BALLOON_WIDTH, BALLOON_HEIGHT).withEyeHeight(BALLOON_EYES);
        };
    }
}
