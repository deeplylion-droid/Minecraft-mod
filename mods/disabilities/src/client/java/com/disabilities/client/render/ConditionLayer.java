package com.disabilities.client.render;

import com.disabilities.condition.Condition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Pose;

/** Draws the visible aids on players: glasses, eye patch, hearing aids, wheelchair, crutches. */
public class ConditionLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public ConditionLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Visuals v = state.getData(Visuals.KEY);
        if (v == null || state.isInvisible || state.isSpectator) {
            return;
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);

        String glasses = v.has(Condition.BLIND) ? Accessories.DARK_GLASSES
                : v.has(Condition.MYOPIA) ? Accessories.THICK_GLASSES
                : v.has(Condition.ACHROMATOPSIA) ? Accessories.TINTED_GLASSES : null;
        boolean patch = v.has(Condition.ONE_EYE);
        boolean aids = v.has(Condition.DEAF);
        if (glasses != null || patch || aids) {
            poseStack.pushPose();
            this.getParentModel().root().translateAndRotate(poseStack);
            this.getParentModel().getHead().translateAndRotate(poseStack);
            if (patch) {
                draw(Accessories.EYE_PATCH, poseStack, collector, light, overlay, state);
            }
            if (glasses != null) {
                draw(glasses, poseStack, collector, light, overlay, state);
            }
            if (aids) {
                draw(Accessories.HEARING_AIDS, poseStack, collector, light, overlay, state);
            }
            poseStack.popPose();
        }

        boolean lying = state.pose == Pose.SLEEPING || state.swimAmount > 0.0F || state.isFallFlying || state.isAutoSpinAttack;
        if (v.has(Condition.WHEELCHAIR) && !state.isPassenger && !lying) {
            poseStack.pushPose();
            this.getParentModel().root().translateAndRotate(poseStack);
            draw(Accessories.WHEELCHAIR, poseStack, collector, light, overlay, state);
            poseStack.popPose();
        }
        if (v.has(Condition.ONE_LEG) && !state.isPassenger && !lying) {
            poseStack.pushPose();
            this.getParentModel().root().translateAndRotate(poseStack);
            draw(Accessories.CRUTCHES, poseStack, collector, light, overlay, state);
            poseStack.popPose();
        }
    }

    private static void draw(String name, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, AvatarRenderState state) {
        AccessoryModel model = Accessories.get(name);
        if (model == null) {
            return;
        }
        collector.submitModel(model, state, poseStack, RenderTypes.entityTranslucent(model.texture), light, overlay, -1, null,
                state.outlineColor);
    }
}
