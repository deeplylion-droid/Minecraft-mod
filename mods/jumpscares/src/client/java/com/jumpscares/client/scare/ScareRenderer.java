package com.jumpscares.client.scare;

import com.jumpscares.scare.ScareKind;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Draws the monsters. The victim's one is drawn in camera space right after the world (depth
 * cleared, so no wall can get between you and it); the ones you witness are drawn in the world,
 * in front of the victim's face.
 */
public final class ScareRenderer {
    /** Camera-space lights: one from below like a torch under the chin, one from above. */
    private static final Vector3f LIGHT_BELOW = new Vector3f(0.2F, -0.6F, 1.0F).normalize();
    private static final Vector3f LIGHT_ABOVE = new Vector3f(-0.5F, 0.8F, 0.3F).normalize();
    private static final ScarePose WITNESS = new ScarePose();

    private ScareRenderer() {
    }

    /** Called by GameRenderer.renderLevel after the world and before the hand. */
    public static void renderVictim(float partialTick) {
        ScareClient.Scare scare = ScareClient.active();
        if (scare == null) {
            return;
        }
        ScarePose p = ScareClient.pose(partialTick);
        ScareModel model = ScareModels.get(scare.kind);
        if (p == null || !p.visible || model == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // same size on screen whatever the field of view setting
        float fovFix = (float) (Math.tan(Math.toRadians(35.0)) / Math.tan(Math.toRadians(mc.options.fov().get() / 2.0)));
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderLights(LIGHT_BELOW, LIGHT_ABOVE);
        PoseStack poseStack = new PoseStack();
        place(poseStack, scare.kind, model, p, fovFix);
        ScareAnimator.animate(scare.kind, model, p, scare.time(partialTick));
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        draw(model, poseStack, buffers, LightTexture.FULL_BRIGHT);
        buffers.endBatch();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        Lighting.setupLevel();
    }

    /** Called after the entities: the monsters jumping at other players. */
    public static void renderWitnesses(WorldRenderContext context) {
        if (ScareClient.witnessed().isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true);
        Vec3 cam = context.camera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (ScareClient.Scare scare : ScareClient.witnessed()) {
            Entity victim = context.world().getEntity(scare.victim);
            ScareModel model = ScareModels.get(scare.kind);
            if (victim == null || model == null || (victim == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson())) {
                continue;
            }
            float t = scare.time(partialTick);
            if (t >= scare.kind.lungeTick() + 1) {
                continue;
            }
            ScareTimeline.compute(scare.kind, scare.seed, t, WITNESS);
            if (!WITNESS.visible) {
                continue;
            }
            WITNESS.z = Math.min(WITNESS.z, -0.32F); // stays in front of the victim's face
            Vec3 eye = victim.getEyePosition(partialTick);
            PoseStack poseStack = new PoseStack();
            poseStack.translate(eye.x - cam.x, eye.y - cam.y, eye.z - cam.z);
            // the victim's own camera rotation (as in Camera.setRotation)
            poseStack.mulPose(new Quaternionf().rotationYXZ((float) Math.PI - victim.getViewYRot(partialTick) * Mth.DEG_TO_RAD,
                    -victim.getViewXRot(partialTick) * Mth.DEG_TO_RAD, 0.0F));
            place(poseStack, scare.kind, model, WITNESS, 1.0F);
            ScareAnimator.animate(scare.kind, model, WITNESS, t);
            stareAtWitness(model, poseStack, scare.kind, t);
            int light = LevelRenderer.getLightColor(context.world(), BlockPos.containing(eye));
            draw(model, poseStack, buffers, light);
            buffers.endBatch(RenderType.entityCutoutNoCull(model.texture));
            buffers.endBatch(RenderType.eyes(model.glow));
        }
    }

    /** While it screams at the victim, its head slowly turns all the way round to stare at you. */
    private static void stareAtWitness(ScareModel model, PoseStack poseStack, ScareKind kind, float t) {
        ModelPart head = model.part("head");
        float turn = Mth.clamp((t - kind.impactTick - 3.0F) / 4.0F, 0.0F, 1.0F);
        if (head == null || turn <= 0.0F) {
            return;
        }
        turn = turn * turn * (3.0F - 2.0F * turn);
        // where the camera (the origin here) is, seen from the monster: its face looks down -z
        Vector3f eye = new Matrix4f(poseStack.last().pose()).invert().transformPosition(new Vector3f());
        float yaw = (float) Math.atan2(-eye.x, -eye.z);
        float pitch = (float) Math.atan2(eye.y, Math.sqrt(eye.x * eye.x + eye.z * eye.z));
        head.yRot += yaw * 0.9F * turn;
        head.xRot += Mth.clamp(pitch, -0.8F, 0.8F) * 0.7F * turn;
    }

    private static void place(PoseStack poseStack, ScareKind kind, ScareModel model, ScarePose p, float scale) {
        poseStack.translate(p.x * scale, (p.y + ScareTimeline.center(kind) * model.unit) * scale, p.z * scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(p.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(p.pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(p.roll));
        float s = 16.0F * model.unit;
        // model space is y-down with the face towards -z: turn it to face the camera
        poseStack.scale(s, -s, -s);
    }

    private static void draw(ScareModel model, PoseStack poseStack, MultiBufferSource buffers, int light) {
        model.root.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(model.texture)), light, OverlayTexture.NO_OVERLAY);
        model.root.render(poseStack, buffers.getBuffer(RenderType.eyes(model.glow)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }
}
