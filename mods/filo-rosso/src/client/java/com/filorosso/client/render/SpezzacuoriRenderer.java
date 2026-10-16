package com.filorosso.client.render;

import com.filorosso.FiloRosso;
import com.filorosso.entity.Spezzacuori;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** Lo Spezzacuori: a giant (seven blocks), animated by hand from what he is doing. */
public class SpezzacuoriRenderer extends MobRenderer<Spezzacuori, SpezzacuoriRenderState, SpezzacuoriModel> {
    private static final Identifier TEXTURE = FiloRosso.id("textures/entity/spezzacuori.png");
    private static final RenderType GLOW = RenderTypes.eyes(FiloRosso.id("textures/entity/spezzacuori_glow.png"));

    public SpezzacuoriRenderer(EntityRendererProvider.Context context) {
        super(context, new SpezzacuoriModel(CubeModels.load(context.getResourceManager(), FiloRosso.id("geometry/spezzacuori.json"))), 2.4F);
        this.addLayer(new EyesLayer<SpezzacuoriRenderState, SpezzacuoriModel>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    public SpezzacuoriRenderState createRenderState() {
        return new SpezzacuoriRenderState();
    }

    @Override
    public void extractRenderState(Spezzacuori entity, SpezzacuoriRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.action = entity.action();
        state.actionTime = entity.actionTime(partial);
        state.raging = entity.raging();
    }

    @Override
    public Identifier getTextureLocation(SpezzacuoriRenderState state) {
        return TEXTURE;
    }

    /** No sideways flip when he dies: he falls on his knees (in the model). */
    @Override
    protected float getFlipDegrees() {
        return 0.0F;
    }

    @Override
    protected void setupRotations(SpezzacuoriRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        if (state.action == Spezzacuori.RISE) {
            // he climbs out of the ground
            float k = Mth.clamp(state.actionTime / 55.0F, 0.0F, 1.0F);
            k = k * k * (3.0F - 2.0F * k);
            poseStack.translate(0.0F, -(1.0F - k) * 7.6F, 0.0F);
        }
        if (state.raging) {
            poseStack.translate((Mth.sin(state.ageInTicks * 2.1F) * 0.02F), 0.0F, 0.0F);
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(Spezzacuori entity, float partialTicks) {
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(4.0, 2.0, 4.0);
    }
}
