package com.punizioni.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.punizioni.Punizioni;
import com.punizioni.entity.GruelPot;
import com.punizioni.entity.Prop;
import com.punizioni.entity.RoastPig;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Draws a prop with its Blockbench model (PropModel); the pot of gruel also gets the fire under it. */
public class PropRenderer<T extends Prop> extends EntityRenderer<T, PropRenderState> {
    private final PropModel model;
    private final Identifier texture;
    private final boolean fire;

    public PropRenderer(EntityRendererProvider.Context context, String name, boolean fire) {
        super(context);
        this.model = new PropModel(BbModel.load(context.getResourceManager(), Punizioni.id("geometry/" + name + ".bbmodel")));
        this.texture = Punizioni.id("textures/entity/" + name + ".png");
        this.fire = fire;
        this.shadowRadius = 0.5F;
        this.shadowStrength = 0.6F;
    }

    @Override
    public PropRenderState createRenderState() {
        return new PropRenderState();
    }

    @Override
    public void extractRenderState(T prop, PropRenderState s, float partial) {
        super.extractRenderState(prop, s, partial);
        s.yaw = prop.getYRot();
        s.time = prop.level().getGameTime() + partial;
        s.taken = prop instanceof RoastPig pig ? pig.taken() : 0;
        s.level = prop instanceof GruelPot pot ? pot.portions() / (float) GruelPot.MAX : 0.0F;
    }

    @Override
    public void submit(PropRenderState s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180.0F - s.yaw);
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        collector.submitModel(this.model, s, poseStack, this.model.renderType(this.texture), s.lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
                s.outlineColor);
        poseStack.popPose();
        if (this.fire) {
            // the fire under the pot, among the logs
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.08F, 0.0F);
            Flames.ring(collector, poseStack, s.time, 0.75F, 5, 0.13F, 0.42F, 0.24F, 53L);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.submit(s, poseStack, collector, camera);
    }
}
