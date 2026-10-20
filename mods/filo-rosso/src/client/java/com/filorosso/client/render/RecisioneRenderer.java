package com.filorosso.client.render;

import com.filorosso.FiloRosso;
import com.filorosso.entity.Recisione;
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
 * Recisione, la Marionetta: the Blockbench model geometry/recisione.bbmodel (made with the monster
 * skill; open blockbench/recisione.bbmodel in Blockbench to see or change it), with its eyes, heart
 * and cut ends glowing in the dark. Now and then its light goes out for a moment.
 */
public class RecisioneRenderer extends MobRenderer<Recisione, RecisioneRenderState, RecisioneModel> {
    private static final Identifier TEXTURE = FiloRosso.id("textures/entity/recisione.png");
    private static final RenderType GLOW = RenderTypes.eyes(FiloRosso.id("textures/entity/recisione_glow.png"));

    public RecisioneRenderer(EntityRendererProvider.Context context) {
        super(context, new RecisioneModel(BbModel.load(context.getResourceManager(), FiloRosso.id("geometry/recisione.bbmodel"), "hitbox")), 1.2F);
        this.addLayer(new EyesLayer<RecisioneRenderState, RecisioneModel>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }

            @Override
            public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, RecisioneRenderState state, float yRot, float xRot) {
                if (!state.glowOff) {
                    super.submit(poseStack, collector, light, state, yRot, xRot);
                }
            }
        });
    }

    @Override
    public RecisioneRenderState createRenderState() {
        return new RecisioneRenderState();
    }

    @Override
    public void extractRenderState(Recisione entity, RecisioneRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.action = entity.action();
        state.actionTime = entity.actionTime(partial);
        state.phase = entity.phase();
        state.phaseTime = entity.phaseTime(partial);
        state.sinceHurt = entity.hurtTime > 0 ? 10 - entity.hurtTime + partial : 100.0F;
        state.seed = entity.getId() * 7919 + 13;
        // only a flash of red when hit, never while it comes apart
        state.hasRedOverlay = entity.hurtTime > 7 && entity.deathTime == 0;
        float glitch = state.phase == 3 ? 0.1F : state.phase == 2 ? 0.06F : 0.03F;
        if (state.sinceHurt < 8.0F) {
            glitch = Math.max(glitch, 0.5F);
        }
        if (state.action == Recisione.BLINK) {
            glitch = 0.8F;
        } else if (state.action == Recisione.BELL && state.actionTime > 8.0F && state.actionTime < 40.0F) {
            glitch = Math.max(glitch, 0.3F);
        } else if (state.action == Recisione.RISE) {
            glitch = Math.max(glitch, 0.15F);
        }
        if (entity.deathTime > 0) {
            glitch = entity.deathTime > 60 ? 0.4F : 0.05F;
        }
        state.glitch = glitch;
        // the light goes out once it has collapsed, and for a moment now and then
        state.glowOff = entity.deathTime > 26 || RecisioneModel.hash(state.seed + 40, Mth.floor(state.ageInTicks / 3.0F)) < 0.035F;
    }

    @Override
    public Identifier getTextureLocation(RecisioneRenderState state) {
        return TEXTURE;
    }

    /** No sideways flip when it dies: it collapses like a puppet whose strings were cut. */
    @Override
    protected float getFlipDegrees() {
        return 0.0F;
    }

    private static float ease(float t) {
        float k = Mth.clamp(t, 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    @Override
    protected void setupRotations(RecisioneRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        if (state.action == Recisione.RISE) {
            // it climbs out of the ground
            poseStack.translate(0.0F, -(1.0F - ease(state.actionTime / 56.0F)) * 10.5F, 0.0F);
        }
        // it grows in its last form (scaled from its feet)
        if (state.phase >= 3) {
            float g = 1.0F + 0.15F * ease(state.phaseTime / 40.0F);
            poseStack.scale(g, g, g);
        }
        if (state.action == Recisione.BLINK) {
            // squeezed into a line and gone, then back out of a line somewhere else
            float t = state.actionTime;
            float k = t < 8.0F ? ease(t / 8.0F) : 1.0F - ease((t - 8.0F) / 8.0F);
            float xz = Math.max(0.03F, 1.0F - 0.97F * k);
            poseStack.scale(xz, 1.0F + 0.4F * k, xz);
        }
        if (RecisioneModel.glitchFrame(state)) {
            int frame = Mth.floor(state.ageInTicks);
            float dx = (RecisioneModel.hash(state.seed + 1, frame) - 0.5F) * 0.8F;
            float dz = (RecisioneModel.hash(state.seed + 2, frame) - 0.5F) * 0.8F;
            poseStack.translate(dx, 0.0F, dz);
        }
    }

    /** Its strings rise five blocks above its head, and when it lies down it takes up a lot of ground. */
    @Override
    protected AABB getBoundingBoxForCulling(Recisione entity, float partialTicks) {
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(7.0, 1.0, 7.0).expandTowards(0.0, 6.0, 0.0).expandTowards(0.0, -11.0, 0.0);
    }
}
