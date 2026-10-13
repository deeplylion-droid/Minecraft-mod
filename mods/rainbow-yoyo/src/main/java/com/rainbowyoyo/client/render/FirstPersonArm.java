package com.rainbowyoyo.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rainbowyoyo.client.YoyoArm;
import com.rainbowyoyo.entity.YoyoEntity;
import com.rainbowyoyo.item.YoyoItem;
import com.rainbowyoyo.motion.ArmAim;
import com.rainbowyoyo.motion.V3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * In first person, while the yo-yo is out, your own arm is drawn in the world exactly where the
 * player model would have it, pointing along the string: you see your hand at the bottom of the
 * screen following the yo-yo through every trick.
 */
public final class FirstPersonArm {

    private FirstPersonArm() {
    }

    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isInvisible() || !mc.options.getCameraType().isFirstPerson()
                || mc.getCameraEntity() != player || !(player.getMainHandItem().getItem() instanceof YoyoItem)) {
            return;
        }
        YoyoEntity yoyo = YoyoEntity.clientYoyoOf(player);
        EntityRenderer<? super LocalPlayer> entityRenderer = mc.getEntityRenderDispatcher().getRenderer(player);
        if (yoyo == null || !(entityRenderer instanceof PlayerRenderer renderer)) {
            return;
        }
        float partialTick = event.getPartialTick();
        HumanoidArm arm = player.getMainArm();
        float[] angles = YoyoArm.finalAngles(player, partialTick, arm, yoyo.renderPosition(partialTick));
        boolean crouching = player.isCrouching();
        Vec3 feet = player.getPosition(partialTick);
        Vec3 camera = event.getCamera().getPosition();

        // Same transforms as the player renderer.
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(feet.x - camera.x, feet.y - camera.y + (crouching ? -0.125 : 0.0), feet.z - camera.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - (float) YoyoArm.bodyYaw(player, partialTick)));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        float scale = (float) ArmAim.PLAYER_SCALE;
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0F, (float) -ArmAim.MODEL_ROOT, 0.0F);

        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        ModelPart sleeve = arm == HumanoidArm.RIGHT ? model.rightSleeve : model.leftSleeve;
        V3 pivot = YoyoArm.pivot(arm, crouching);
        part.setPos((float) pivot.x(), (float) pivot.y(), 0.0F);
        part.setRotation(angles[0], angles[1], angles[2]);
        part.visible = true;
        sleeve.copyFrom(part);
        sleeve.visible = player.isModelPartShown(
                arm == HumanoidArm.RIGHT ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int light = mc.getEntityRenderDispatcher().getPackedLightCoords(player, partialTick);
        RenderType type = RenderType.entityTranslucent(player.getSkinTextureLocation());
        VertexConsumer consumer = buffers.getBuffer(type);
        part.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
        if (sleeve.visible) {
            sleeve.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
        }
        buffers.endBatch(type);
        poseStack.popPose();
    }
}
