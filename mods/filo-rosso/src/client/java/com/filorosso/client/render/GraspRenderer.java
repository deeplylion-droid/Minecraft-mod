package com.filorosso.client.render;

import com.filorosso.FiloRosso;
import com.filorosso.entity.Grasp;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/** The hand from the ground: five black fingers come up around you, open, and close. */
public class GraspRenderer extends EntityRenderer<Grasp, GraspRenderer.State> {
    private static final Identifier TEXTURE = FiloRosso.id("textures/entity/grasp.png");
    private static final RenderType GLOW = RenderTypes.eyes(FiloRosso.id("textures/entity/grasp_glow.png"));
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float D = Mth.DEG_TO_RAD;

    public static class State extends EntityRenderState {
        /** 0 = under the ground, 1 = all the way up. */
        public float rise;
        /** 0 = open, 1 = closed. */
        public float close;
        public float yaw;
        public int seed;
    }

    static class Model extends EntityModel<State> {
        private final ModelPart[] fingers = new ModelPart[5];
        private final ModelPart[] tips = new ModelPart[5];

        Model(CubeModels.Baked baked) {
            super(baked.root(), RenderTypes::entityCutout);
            for (int i = 0; i < 5; i++) {
                this.fingers[i] = baked.part("finger_" + i);
                this.tips[i] = baked.part("finger_" + i + "_tip");
            }
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            for (int i = 0; i < 5; i++) {
                // open: leaning out like claws; closed: bent in over whoever is in the middle
                float wiggle = Mth.sin(s.ageInTicks * 0.9F + i * 1.7F) * 4.0F * (1.0F - s.close);
                this.fingers[i].xRot = Mth.lerp(s.close, -30.0F, 28.0F) * D + wiggle * D;
                this.tips[i].xRot = Mth.lerp(s.close, 12.0F, 80.0F) * D + (RecisioneModel.hash(s.seed + i, 0) - 0.5F) * 14.0F * D;
            }
        }
    }

    private final Model model;

    public GraspRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new Model(CubeModels.load(context.getResourceManager(), FiloRosso.id("geometry/grasp.json")));
        this.shadowRadius = 0.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Grasp entity, State state, float partial) {
        super.extractRenderState(entity, state, partial);
        float t = entity.tickCount + partial;
        state.rise = entity.rise(partial);
        float k = Mth.clamp((t - (Grasp.CLOSE - 2.0F)) / 3.0F, 0.0F, 1.0F);
        state.close = k * k * (3.0F - 2.0F * k);
        state.yaw = entity.getYRot();
        state.seed = entity.getId() * 613 + 3;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.rise <= 0.01F) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, -(1.0F - state.rise) * 2.8F - 0.15F, 0.0F);
        poseStack.rotate(new Quaternionf().rotationY(-state.yaw * D));
        poseStack.scale(-1.25F, -1.25F, 1.25F);
        collector.submitModel(this.model, state, poseStack, this.model.renderType(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
                state.outlineColor);
        collector.submitModel(this.model, state, poseStack, GLOW, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1, null, EntityRenderState.NO_OUTLINE);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
