package com.punizioni.client.royal;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Your own whip in first person. The handle is drawn by vanilla in the hand pass (its own field of
 * view, its own depth); the lash is drawn there too, starting exactly at the handle's tip, with its
 * world-space points brought into the hand pass so they land on screen where the world would show
 * them: through the camera's rotation, the same view bobbing, and the ratio of the two fields of view.
 */
public final class FirstPersonLash {
    private FirstPersonLash() {
    }

    /** The hand pass' pose before any hand: the inverse camera rotation and the bobbing. */
    private static final Matrix4f START = new Matrix4f();
    private static boolean started;

    public static void begin(PoseStack poseStack) {
        START.set(poseStack.last().pose());
        started = true;
    }

    /** The whip in a hand, as vanilla is about to draw its handle there. */
    public static void draw(PoseStack poseStack, SubmitNodeCollector collector, HumanoidArm arm, int light, float partial) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || !started) {
            return;
        }
        CameraRenderState camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        Matrix4f view = camera.viewRotationMatrix;
        float tanWorld = 1.0F / camera.projectionMatrix.m11();
        float tanHand = (float) Math.tan(Math.toRadians(camera.hudFov) * 0.5);
        float k = tanWorld > 1.0E-4F ? tanHand / tanWorld : 1.0F;
        // world (minus the camera) to this pass: view^-1 * fov ratio * bobbing * view
        Matrix4f toPass = new Matrix4f(view).invert().mul(new Matrix4f().scaling(k, k, 1.0F)).mul(view).mul(START).mul(view);
        Matrix4f fromPass = new Matrix4f(toPass).invert();

        PoseStack.Pose pose = poseStack.last().copy();
        WhipHandle.apply(pose, arm == HumanoidArm.LEFT, true);
        Vector3f tip = new Vector3f();
        Vector3f dir = new Vector3f();
        WhipHandle.tip(pose, tip, dir);
        fromPass.transformPosition(tip);
        fromPass.transformDirection(dir);
        if (dir.lengthSquared() < 1.0E-8F) {
            dir.set(0.0F, 1.0F, 0.0F);
        }
        dir.normalize();
        Vec3 c = camera.pos;
        long now = level.getGameTime();
        double time = now + partial;
        Lash lash = Lashes.of(player.getId());
        lash.update(level, time, now, c.x + tip.x, c.y + tip.y, c.z + tip.z, dir.x, dir.y, dir.z, player.getViewYRot(partial),
                player.getViewXRot(partial));
        Lashes.afterUpdate(level, lash, time);
        Lashes.submit(collector, lash, toPass, c.x, c.y, c.z, light);
    }
}
