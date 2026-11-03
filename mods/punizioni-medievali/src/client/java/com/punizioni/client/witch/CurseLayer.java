package com.punizioni.client.witch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.punizioni.Punizioni;
import com.punizioni.client.render.BbModel;
import com.punizioni.client.render.Visuals;
import com.punizioni.witch.CurseKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * What the witch's curse has made of a player: the toad (it grows out of the green cloud in a
 * moment) or the balloon (it swells up from the chest; popped, it whizzes round emptying). The head
 * in the butt needs nothing here: Poses moves the head.
 */
public class CurseLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier TOAD_TEXTURE = Punizioni.id("textures/entity/toad.png");
    private static final Identifier TOAD_GLOW = Punizioni.id("textures/entity/toad_glow.png");
    private static final RenderType TOAD = RenderTypes.entityCutout(TOAD_TEXTURE);
    private static final RenderType TOAD_EYES = RenderTypes.eyes(TOAD_GLOW);

    private final ToadModel toad;

    public CurseLayer(AvatarRenderer<?> renderer, ResourceManager manager) {
        super(renderer);
        this.toad = new ToadModel(BbModel.load(manager, Punizioni.id("geometry/toad.bbmodel")));
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Visuals v = state.getData(Visuals.KEY);
        if (v == null || v.curse == null || state.isInvisible) {
            return;
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        if (v.curse == CurseKind.TOAD) {
            this.toad(poseStack, collector, light, overlay, state, v);
        } else if (v.curse == CurseKind.BALLOON) {
            this.balloon(poseStack, collector, light, overlay, state, v);
        }
    }

    private void toad(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, AvatarRenderState state, Visuals v) {
        // it grows out of the cloud: too big for a moment, then its size
        float g = Mth.clamp(v.curseAge / 16.0F, 0.0F, 1.0F);
        float size = g >= 1.0F ? 1.0F : 0.15F + 0.85F * (1.0F - (1.0F - g) * (1.0F - g)) + 0.18F * Mth.sin(g * Mth.PI);
        float air = v.airborne ? Mth.clamp(0.45F + Math.abs(v.vy) * 5.0F, 0.0F, 1.0F) : 0.0F;
        ToadModel.Pose pose = new ToadModel.Pose(v.time, state.walkAnimationPos, state.walkAnimationSpeed, air, v.croak, v.swing, v.hurt,
                Mth.clamp(state.yRot, -60.0F, 60.0F), Mth.clamp(state.xRot, -40.0F, 40.0F), v.seed);
        poseStack.pushPose();
        // grow from the feet
        poseStack.translate(0.0F, 1.501F, 0.0F);
        poseStack.scale(size, size, size);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        collector.submitModel(this.toad, pose, poseStack, TOAD, light, overlay, EntityRenderState.NO_OUTLINE);
        collector.submitModel(this.toad, pose, poseStack, TOAD_EYES, light, overlay, EntityRenderState.NO_OUTLINE);
        poseStack.popPose();
    }

    private void balloon(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, AvatarRenderState state, Visuals v) {
        Identifier skin = state.skin.body().texturePath();
        boolean thin = state.skin.model() == PlayerModelType.SLIM;
        float headX = Mth.clamp(state.xRot, -35.0F, 35.0F) * Mth.DEG_TO_RAD;
        float headY = Mth.clamp(state.yRot, -50.0F, 50.0F) * Mth.DEG_TO_RAD;
        poseStack.pushPose();
        if (v.popAge >= 0.0F && v.hasEntityPose && !v.gui) {
            // popped: it whizzes round (in the world's frame), spinning
            Vec3 d = BalloonShape.zip(v.seed, v.popAge, drop(state));
            Matrix4f rest = new Matrix4f(v.entityPose).invert().mul(poseStack.last().pose());
            poseStack.last().pose().set(new Matrix4f(v.entityPose).translate((float) d.x, (float) d.y, (float) d.z).mul(rest));
            float cy = BalloonShape.centerY(1.0F) / 16.0F;
            poseStack.translate(0.0F, cy, 0.0F);
            poseStack.rotate(new org.joml.Quaternionf().rotationXYZ(v.popAge * 0.9F, v.popAge * 1.7F, v.popAge * 0.6F));
            poseStack.translate(0.0F, -cy, 0.0F);
        }
        Balloons.submit(poseStack, collector, v, skin, thin, light, overlay, headX, headY);
        poseStack.popPose();
    }

    /** How far under the balloon the ground is (blocks), where the empty skin ends up. */
    private static double drop(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 2.0;
        }
        Vec3 from = new Vec3(state.x, state.y + 0.5, state.z);
        Vec3 to = from.add(0.0, -24.0, 0.0);
        var hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) {
            return 2.4;
        }
        return Math.max(0.0, state.y - hit.getLocation().y);
    }
}
