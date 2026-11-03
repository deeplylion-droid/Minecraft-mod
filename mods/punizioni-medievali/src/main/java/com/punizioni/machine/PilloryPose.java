package com.punizioni.machine;

/**
 * The body in the pillory, in the vanilla player model's pixels (y down, -z towards the face): bent
 * forward at the hips behind the board, the head out in front of it, the arms through its two side
 * holes. The same numbers pose the drawn body (Poses), place the victim's eyes (VictimCamera), tell
 * the server what a thrown dung hit (BodyHit) and cut the holes in the board's model.
 */
public final class PilloryPose {
    private PilloryPose() {
    }

    /** Pixels of the player model in blocks (players are drawn at 15/16). */
    public static final double PX = 0.9375 / 16.0;

    /** How far the body bends forward at the hips (radians). */
    public static final float BEND = 0.85F;
    /** The top of the bent body (the neck), where the body part hangs from. */
    public static final float NECK_Y = 12.0F - 12.0F * (float) Math.cos(BEND);
    public static final float NECK_Z = -12.0F * (float) Math.sin(BEND);
    /** The shoulders, on the bent body. */
    public static final float SHOULDER_Y = NECK_Y + 2.0F * (float) Math.cos(BEND);
    public static final float SHOULDER_Z = NECK_Z + 2.0F * (float) Math.sin(BEND);
    /** The board's back and front faces (model z). */
    public static final float BOARD_BACK = -10.7F;
    public static final float BOARD_FRONT = -12.7F;
    /** The head's pivot (the middle of its bottom face), out in front of the board. */
    public static final float HEAD_Y = 5.2F;
    public static final float HEAD_Z = BOARD_FRONT - 4.0F;
    /** The arms: forward through the board, a little apart. */
    public static final float ARM_X_ROT = -1.72F;
    public static final float ARM_SPREAD = 0.28F;
    /** How far the head can turn and nod in the hole (radians). */
    public static final float HEAD_YAW = 0.45F;
    public static final float HEAD_PITCH = 0.45F;

    /** The platform the victim stands on (blocks). */
    public static final double PLATFORM = 0.25;
    /** The victim's feet are this far behind the middle of the board (blocks). */
    public static final double FEET_BEHIND = -(BOARD_BACK + BOARD_FRONT) * 0.5 * PX;
    /** The line between the two halves of the board, over the machine's feet (blocks). */
    public static final double BOARD_SPLIT = PLATFORM + (24.0 - 4.6) * PX;
    /** The board's bottom and top edges, half width and half thickness (blocks). */
    public static final double BOARD_BOTTOM = BOARD_SPLIT - 0.42;
    public static final double BOARD_TOP = BOARD_SPLIT + 0.44;
    public static final double BOARD_HALF_WIDTH = 0.86;
    public static final double BOARD_HALF_THICKNESS = (BOARD_BACK - BOARD_FRONT) * 0.5 * PX;
}
