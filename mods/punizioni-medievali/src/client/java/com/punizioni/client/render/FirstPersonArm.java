package com.punizioni.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.punizioni.data.Body;
import com.punizioni.data.Limb;
import com.punizioni.data.ModData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * Your own arm in first person, as the punishments left it: a stump where the hand was (and nothing
 * in it), or the arm broken on the wheel. The model is shared by every player, so the parts hidden for
 * the last player drawn in the world are set again here for you.
 */
public final class FirstPersonArm {
    private FirstPersonArm() {
    }

    private static Body body() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? ModData.body(mc.player) : Body.NONE;
    }

    /** No hand on that side: nothing in it either. */
    public static boolean handGone(InteractionHand hand) {
        Body b = body();
        return hand == InteractionHand.MAIN_HAND ? b.lacks(Limb.RIGHT_HAND) || b.torn() : b.lacks(Limb.LEFT_HAND) || b.torn();
    }

    public static void before(AvatarRenderer<?> renderer, ModelPart arm) {
        PlayerModel model = renderer.getModel();
        boolean right = arm == model.rightArm;
        boolean changed = BodyLayer.changed(body(), right ? 0 : 1);
        arm.skipDraw = changed;
        (right ? model.rightSleeve : model.leftSleeve).skipDraw = changed;
    }

    public static void after(AvatarRenderer<?> renderer, PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skin, ModelPart arm) {
        PlayerModel model = renderer.getModel();
        boolean right = arm == model.rightArm;
        Body b = body();
        int index = right ? 0 : 1;
        if (!BodyLayer.changed(b, index)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean slim = mc.player != null && mc.player.getSkin().model() == PlayerModelType.SLIM;
        poseStack.pushPose();
        arm.translateAndRotate(poseStack);
        BodyLayer.submitLimb(poseStack, collector, light, OverlayTexture.NO_OVERLAY, skin, index, slim, b);
        poseStack.popPose();
    }
}
