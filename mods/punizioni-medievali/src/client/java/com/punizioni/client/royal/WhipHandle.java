package com.punizioni.client.royal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Where the overseer's whip handle ends, so the lash starts exactly there. The numbers are the
 * handle model's own (assets/punizioni/models/item/overseer_whip.json: the handle stands along +Y,
 * the lash comes out of its top) and its display transforms; keep them the same.
 */
public final class WhipHandle {
    private WhipHandle() {
    }

    /** The top of the handle, in the item model's pixels. */
    public static final float TIP_X = 8.0F;
    public static final float TIP_Y = 14.6F;
    public static final float TIP_Z = 8.0F;

    /** The display transforms (rotation in degrees, translation in pixels, scale), as in the model. */
    public static final float[] THIRD_ROTATION = {40.0F, 0.0F, 0.0F};
    public static final float[] THIRD_TRANSLATION = {0.0F, 1.6F, 1.4F};
    public static final float THIRD_SCALE = 0.85F;
    public static final float[] FIRST_ROTATION = {-28.0F, 0.0F, 8.0F};
    public static final float[] FIRST_TRANSLATION = {0.0F, 3.0F, -1.0F};
    public static final float FIRST_SCALE = 0.8F;

    /** Like ItemTransform.apply: translation, rotation, scale, then the model's centre to the origin (left hands mirrored). */
    public static void apply(PoseStack.Pose pose, boolean leftHand, boolean firstPerson) {
        float[] r = firstPerson ? FIRST_ROTATION : THIRD_ROTATION;
        float[] t = firstPerson ? FIRST_TRANSLATION : THIRD_TRANSLATION;
        float s = firstPerson ? FIRST_SCALE : THIRD_SCALE;
        float tx = (leftHand ? -t[0] : t[0]) / 16.0F;
        float ry = leftHand ? -r[1] : r[1];
        float rz = leftHand ? -r[2] : r[2];
        pose.translate(tx, t[1] / 16.0F, t[2] / 16.0F);
        pose.rotate(new Quaternionf().rotationXYZ(r[0] * (float) (Math.PI / 180.0), ry * (float) (Math.PI / 180.0), rz * (float) (Math.PI / 180.0)));
        pose.scale(s, s, s);
        pose.translate(-0.5F, -0.5F, -0.5F);
    }

    /** The third-person hand frame, as ItemInHandLayer builds it (the pose stack is at the model's root). */
    public static void thirdPersonHand(PlayerModel model, AvatarRenderState state, HumanoidArm arm, PoseStack poseStack) {
        model.translateToHand(state, arm, poseStack);
        poseStack.rotateDegrees(Axis.XP, -90.0F);
        poseStack.rotateDegrees(Axis.YP, 180.0F);
        boolean left = arm == HumanoidArm.LEFT;
        poseStack.translate((left ? -1 : 1) / 16.0F, 2.0F / 16.0F, -10.0F / 16.0F);
        apply(poseStack.last(), left, false);
    }

    /** The tip (out) and the direction along the handle (dir), in the pose's space. */
    public static void tip(PoseStack.Pose pose, Vector3f out, Vector3f dir) {
        pose.pose().transformPosition(TIP_X / 16.0F, TIP_Y / 16.0F, TIP_Z / 16.0F, out);
        pose.pose().transformDirection(0.0F, 1.0F, 0.0F, dir);
    }
}
