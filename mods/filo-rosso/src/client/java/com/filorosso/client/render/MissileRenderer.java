package com.filorosso.client.render;

import com.filorosso.FiloRosso;
import com.filorosso.entity.Missile;
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

/** Recisione's missiles: a black box that does not spin, it skips from one angle to the next. */
public class MissileRenderer extends EntityRenderer<Missile, MissileRenderer.State> {
    private static final Identifier TEXTURE = FiloRosso.id("textures/entity/missile.png");
    private static final RenderType GLOW = RenderTypes.eyes(FiloRosso.id("textures/entity/missile_glow.png"));
    private static final int FULL_BRIGHT = 0xF000F0;

    public static class State extends EntityRenderState {
        public int seed;
    }

    static class Model extends EntityModel<State> {
        private final ModelPart core;
        private final ModelPart[] shards = new ModelPart[4];

        Model(CubeModels.Baked baked) {
            super(baked.root(), RenderTypes::entityCutout);
            this.core = baked.part("core");
            for (int i = 0; i < this.shards.length; i++) {
                this.shards[i] = baked.part("shard_" + i);
            }
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            int frame = Mth.floor(s.ageInTicks * 0.7F);
            float pulse = 0.85F + RecisioneModel.hash(s.seed, frame) * 0.3F;
            this.core.xScale = pulse;
            this.core.yScale = pulse;
            this.core.zScale = pulse;
            for (int i = 0; i < this.shards.length; i++) {
                ModelPart p = this.shards[i];
                int seed = s.seed + i * 131;
                p.visible = RecisioneModel.hash(seed, frame) > 0.25F;
                p.x += (RecisioneModel.hash(seed + 1, frame) - 0.5F) * 6.0F;
                p.y += (RecisioneModel.hash(seed + 2, frame) - 0.5F) * 6.0F;
                p.z += (RecisioneModel.hash(seed + 3, frame) - 0.5F) * 6.0F;
            }
        }
    }

    private final Model model;

    public MissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new Model(CubeModels.load(context.getResourceManager(), FiloRosso.id("geometry/missile.json")));
        this.shadowRadius = 0.35F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Missile entity, State state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.seed = entity.getId() * 4099 + 7;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.45F, 0.0F);
        int frame = Mth.floor(state.ageInTicks * 0.5F);
        poseStack.rotate(new Quaternionf().rotationYXZ(RecisioneModel.hash(state.seed + 5, frame) * Mth.TWO_PI,
                RecisioneModel.hash(state.seed + 6, frame) * Mth.TWO_PI, RecisioneModel.hash(state.seed + 7, frame) * Mth.TWO_PI));
        poseStack.scale(-1.4F, -1.4F, 1.4F);
        collector.submitModel(this.model, state, poseStack, this.model.renderType(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
                state.outlineColor);
        collector.submitModel(this.model, state, poseStack, GLOW, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1, null, EntityRenderState.NO_OUTLINE);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
