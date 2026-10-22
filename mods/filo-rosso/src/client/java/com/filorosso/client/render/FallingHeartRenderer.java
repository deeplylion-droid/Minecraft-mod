package com.filorosso.client.render;

import com.filorosso.FiloRosso;
import com.filorosso.entity.FallingHeart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/** A heart of the "Pioggia di cuori": a glowing pixel heart that always faces you, beating, with a soft halo. */
public class FallingHeartRenderer extends EntityRenderer<FallingHeart, FallingHeartRenderer.State> {
    private static final RenderType HEART = RenderTypes.entityCutout(FiloRosso.id("textures/entity/falling_heart.png"));
    private static final RenderType HALO = RenderTypes.eyes(FiloRosso.id("textures/entity/heart_halo.png"));

    public static class State extends EntityRenderState {
        public float time;
        public int seed;
    }

    public FallingHeartRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.2F;
        this.shadowStrength = 0.4F;
    }

    @Override
    protected int getBlockLightLevel(FallingHeart entity, BlockPos pos) {
        return 15;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FallingHeart entity, State state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.time = entity.tickCount + partial;
        state.seed = entity.getId();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        float t = state.time + state.seed * 13.0F;
        // lub-dub: two quick beats, then a rest
        float beat = Mth.sin(t * 0.21F);
        float pulse = 1.0F + 0.12F * Math.max(0.0F, beat * beat * beat);
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.3F + 0.08F * Mth.sin(t * 0.08F), 0.0F);
        poseStack.rotate(camera.orientation);
        poseStack.rotate(Axis.ZP.rotationDegrees(8.0F * Mth.sin(t * 0.05F)));
        float halo = 1.5F * pulse;
        poseStack.pushPose();
        poseStack.scale(halo, halo, halo);
        int glow = ARGB.color(0.55F + 0.2F * Mth.sin(t * 0.15F), 0xFF6090);
        collector.submitCustomGeometry(poseStack, HALO, (pose, buffer) -> quad(pose, buffer, LightCoordsUtil.FULL_BRIGHT, glow, 0.01F));
        poseStack.popPose();
        float s = 0.75F * pulse;
        poseStack.scale(s, s, s);
        collector.submitCustomGeometry(poseStack, HEART, (pose, buffer) -> quad(pose, buffer, LightCoordsUtil.FULL_BRIGHT, 0xFFFFFFFF, 0.0F));
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int light, int color, float z) {
        vertex(buffer, pose, -0.5F, -0.5F, z, 0.0F, 1.0F, color, light);
        vertex(buffer, pose, 0.5F, -0.5F, z, 1.0F, 1.0F, color, light);
        vertex(buffer, pose, 0.5F, 0.5F, z, 1.0F, 0.0F, color, light);
        vertex(buffer, pose, -0.5F, 0.5F, z, 0.0F, 0.0F, color, light);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color, int light) {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
