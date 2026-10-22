package com.filorosso.client.render;

import com.filorosso.FiloRosso;
import com.filorosso.entity.Burattinaio;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Il Burattinaio: the Blockbench model geometry/burattinaio.bbmodel with its eyes, the crack in its
 * mask and its strings glowing in the dark.
 */
public class BurattinaioRenderer extends MobRenderer<Burattinaio, BurattinaioRenderState, BurattinaioModel> {
    private static final Identifier TEXTURE = FiloRosso.id("textures/entity/burattinaio.png");
    private static final RenderType GLOW = RenderTypes.eyes(FiloRosso.id("textures/entity/burattinaio_glow.png"));

    public BurattinaioRenderer(EntityRendererProvider.Context context) {
        super(context, new BurattinaioModel(BbModel.load(context.getResourceManager(), FiloRosso.id("geometry/burattinaio.bbmodel"), "hitbox")), 1.6F);
        this.addLayer(new EyesLayer<BurattinaioRenderState, BurattinaioModel>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }

            @Override
            public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, BurattinaioRenderState state, float yRot, float xRot) {
                if (!state.glowOff) {
                    super.submit(poseStack, collector, light, state, yRot, xRot);
                }
            }
        });
    }

    @Override
    public BurattinaioRenderState createRenderState() {
        return new BurattinaioRenderState();
    }

    @Override
    public void extractRenderState(Burattinaio entity, BurattinaioRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.action = entity.action();
        state.actionTime = entity.actionTime(partial);
        state.phase = entity.phase();
        state.phaseTime = entity.phaseTime(partial);
        state.sinceHurt = entity.hurtTime > 0 ? 10 - entity.hurtTime + partial : 100.0F;
        state.seed = entity.getId() * 7919 + 77;
        state.hasRedOverlay = entity.hurtTime > 7 && entity.deathTime == 0;
        float glitch = state.phase == 3 ? 0.08F : state.phase == 2 ? 0.05F : 0.025F;
        if (state.sinceHurt < 8.0F) {
            glitch = Math.max(glitch, 0.4F);
        }
        if (state.action == Burattinaio.BLINK) {
            glitch = 0.8F;
        } else if (state.action == Burattinaio.DARK) {
            glitch = Math.max(glitch, 0.25F);
        }
        if (entity.deathTime > 0) {
            glitch = entity.deathTime > 72 ? 0.3F : 0.04F;
        }
        state.glitch = glitch;
        state.glowOff = entity.deathTime > 20 || RecisioneModel.hash(state.seed + 40, Mth.floor(state.ageInTicks / 3.0F)) < 0.03F
                || state.action == Burattinaio.DARK && state.actionTime > 4.0F && state.actionTime < 58.0F
                && RecisioneModel.hash(state.seed + 41, Mth.floor(state.ageInTicks)) < 0.5F;
    }

    @Override
    public Identifier getTextureLocation(BurattinaioRenderState state) {
        return TEXTURE;
    }

    @Override
    protected float getFlipDegrees() {
        return 0.0F;
    }

    private static float ease(float t) {
        float k = Mth.clamp(t, 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    @Override
    protected void setupRotations(BurattinaioRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        if (state.action == Burattinaio.DESCENT) {
            // up a little, then it drops onto the boards and stays low, then back up
            float t = state.actionTime;
            float up = ease(t / 14.0F) * (1.0F - ease((t - 16.0F) / 4.0F));
            float down = ease((t - 16.0F) / 4.0F) * (1.0F - ease((t - 34.0F) / 10.0F));
            poseStack.translate(0.0F, 1.5F * up - Burattinaio.DROP * down, 0.0F);
        }
        if (state.action == Burattinaio.BLINK) {
            float t = state.actionTime;
            float k = t < 8.0F ? ease(t / 8.0F) : 1.0F - ease((t - 8.0F) / 8.0F);
            float xz = Math.max(0.03F, 1.0F - 0.97F * k);
            poseStack.scale(xz, 1.0F + 0.3F * k, xz);
        }
        if (BurattinaioModel.glitchFrame(state)) {
            int frame = Mth.floor(state.ageInTicks);
            float dx = (RecisioneModel.hash(state.seed + 1, frame) - 0.5F) * 0.9F;
            float dz = (RecisioneModel.hash(state.seed + 2, frame) - 0.5F) * 0.9F;
            poseStack.translate(dx, 0.0F, dz);
        }
    }

    /** Its strings rise far above its head, its arms and controls reach far out. */
    @Override
    protected AABB getBoundingBoxForCulling(Burattinaio entity, float partialTicks) {
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(8.0, 1.0, 8.0).expandTowards(0.0, 13.0, 0.0).expandTowards(0.0, -6.0, 0.0);
    }
}
