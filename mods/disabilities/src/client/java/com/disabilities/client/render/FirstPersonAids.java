package com.disabilities.client.render;

import com.disabilities.client.Me;
import com.disabilities.condition.Condition;
import com.disabilities.condition.ModData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** In first person you see your own wheelchair or crutches when you look down. */
public final class FirstPersonAids {
    private FirstPersonAids() {
    }

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(FirstPersonAids::submit);
    }

    private static void submit(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.getCameraEntity() != player || !mc.options.getCameraType().isFirstPerson() || player.isSpectator()
                || player.isPassenger() || player.isSwimming() || player.isVisuallySwimming() || player.isFallFlying() || ModData.asleep(player)) {
            return;
        }
        boolean chair = Me.has(Condition.WHEELCHAIR);
        boolean crutches = Me.has(Condition.ONE_LEG);
        if (!chair && !crutches) {
            return;
        }
        AccessoryModel model = Accessories.get(chair ? Accessories.WHEELCHAIR : Accessories.CRUTCHES);
        if (model == null) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;
        AvatarRenderState state = new AvatarRenderState();
        state.walkAnimationPos = player.walkAnimation.position(partial);
        state.walkAnimationSpeed = player.walkAnimation.speed(partial);
        int light = mc.getEntityRenderDispatcher().getRenderer(player).getPackedLightCoords(player, partial);
        float scale = player.getScale();
        float bodyRot = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);

        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(Mth.lerp(partial, player.xo, player.getX()) - camera.x, Mth.lerp(partial, player.yo, player.getY()) - camera.y,
                Mth.lerp(partial, player.zo, player.getZ()) - camera.z);
        if (chair) {
            poseStack.translate(0.0, -4.0 / 16.0 * 0.9375 * scale, 0.0);
        }
        poseStack.scale(scale, scale, scale);
        poseStack.rotateDegrees(Axis.YP, 180.0F - bodyRot);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        if (!chair) {
            // crutches: squashed towards their tips and moved apart, so the pads are not in your face
            poseStack.translate(0.0F, 1.5F, 0.0F);
            poseStack.scale(1.35F, 0.68F, 1.0F);
            poseStack.translate(0.0F, -1.5F, 0.0F);
        }
        context.submitNodeCollector().submitModel(model, state, poseStack, RenderTypes.entityTranslucent(model.texture), light,
                OverlayTexture.NO_OVERLAY, -1, null, 0);
        poseStack.popPose();
    }
}
