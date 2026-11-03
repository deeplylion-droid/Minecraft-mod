package com.punizioni.client.witch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.punizioni.Punizioni;
import com.punizioni.client.render.BbModel;
import com.punizioni.client.render.Flames;
import com.punizioni.entity.WitchCauldron;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The giant cauldron: the iron pot on its legs over the logs and the fire, and the brew drawn
 * apart, in the colour it has now (it heaves as it boils, jumps when something falls in, glows
 * when a curse is ready).
 */
public class CauldronRenderer extends EntityRenderer<WitchCauldron, CauldronRenderer.State> {
    private static final Identifier TEXTURE = Punizioni.id("textures/entity/witch_cauldron.png");
    private static final Identifier GLOW = Punizioni.id("textures/entity/witch_cauldron_glow.png");

    public static class State extends EntityRenderState {
        public float yaw;
        public float time;
        public int color;
        public boolean ready;
        public int event;
        public float sinceEvent;
    }

    /** The cauldron's model with one of its two halves shown: the pot (and all the rest), or the brew. */
    static final class Half extends EntityModel<State> {
        private final boolean brewOnly;
        private final @Nullable ModelPart pot;
        private final @Nullable ModelPart brew;
        private final @Nullable ModelPart bubbles;

        Half(BbModel bb, boolean brewOnly) {
            super(bb.root, RenderTypes::entityCutout);
            this.brewOnly = brewOnly;
            this.pot = bb.find("pot");
            this.brew = bb.find("brew");
            this.bubbles = bb.find("bubbles");
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            if (this.pot != null) {
                this.pot.visible = !this.brewOnly;
            }
            if (this.brew != null) {
                this.brew.visible = this.brewOnly;
                // it heaves as it boils; whatever falls in makes it jump
                float heave = Mth.sin(s.time * 0.27F) * 0.18F + Mth.sin(s.time * 0.61F + 1.0F) * 0.07F;
                if (s.event != WitchCauldron.EVENT_NONE && s.sinceEvent < 20.0F) {
                    float k = 1.0F - s.sinceEvent / 20.0F;
                    float amp = s.event == WitchCauldron.EVENT_READY || s.event == WitchCauldron.EVENT_FAIL ? 1.4F : 0.6F;
                    heave += Mth.sin(s.sinceEvent * 1.3F) * amp * k;
                }
                this.brew.y -= heave;
                this.brew.yRot = s.time * 0.01F;
            }
            if (this.bubbles != null) {
                // the big bubbles swell and burst, one after the other
                float b = (s.time * 0.05F) % 1.0F;
                this.bubbles.y -= b * 0.8F;
                float size = 0.4F + b * 0.9F;
                this.bubbles.xScale = size;
                this.bubbles.yScale = size;
                this.bubbles.zScale = size;
                this.bubbles.visible = this.brewOnly && b < 0.92F;
            }
        }
    }

    private final Half pot;
    private final Half brew;

    public CauldronRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.pot = new Half(BbModel.load(context.getResourceManager(), Punizioni.id("geometry/witch_cauldron.bbmodel")), false);
        this.brew = new Half(BbModel.load(context.getResourceManager(), Punizioni.id("geometry/witch_cauldron.bbmodel")), true);
        this.shadowRadius = 1.0F;
        this.shadowStrength = 0.7F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WitchCauldron cauldron, State s, float partial) {
        super.extractRenderState(cauldron, s, partial);
        s.yaw = cauldron.getYRot();
        s.time = cauldron.level().getGameTime() + partial;
        s.color = cauldron.color();
        s.ready = cauldron.brew() != null;
        s.event = cauldron.event();
        s.sinceEvent = cauldron.sinceEvent(partial);
        if (s.event == WitchCauldron.EVENT_FAIL && s.sinceEvent < 40.0F) {
            // a ruined brew: black, clearing back to water
            s.color = ARGB.srgbLerp(Mth.clamp(s.sinceEvent / 40.0F, 0.0F, 1.0F), 0x101010, WitchCauldron.WATER) & 0xFFFFFF;
        }
    }

    @Override
    public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180.0F - s.yaw);
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        collector.submitModel(this.pot, s, poseStack, RenderTypes.entityCutout(TEXTURE), s.lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
                s.outlineColor);
        int tint = 0xFF000000 | s.color;
        collector.submitModel(this.brew, s, poseStack, RenderTypes.entityCutout(TEXTURE), s.lightCoords, OverlayTexture.NO_OVERLAY, tint, null,
                s.outlineColor);
        // a curse glows in the dark (a little even when it is not ready)
        float glow = s.ready ? 0.75F + 0.25F * Mth.sin(s.time * 0.2F) : 0.18F;
        collector.submitModel(this.brew, s, poseStack, RenderTypes.eyes(GLOW), s.lightCoords, OverlayTexture.NO_OVERLAY, ARGB.color(glow, s.color),
                null, s.outlineColor);
        poseStack.popPose();
        // the fire under the pot, among the logs
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.06F, 0.0F);
        Flames.ring(collector, poseStack, s.time, 1.0F, 9, 0.62F, 0.55F, 0.34F, 97L);
        poseStack.popPose();
        poseStack.popPose();
        super.submit(s, poseStack, collector, camera);
    }
}
