package com.rainbowyoyo.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rainbowyoyo.client.YoyoArm;
import com.rainbowyoyo.config.YoyoConfig;
import com.rainbowyoyo.entity.YoyoEntity;
import com.rainbowyoyo.motion.V3;
import com.rainbowyoyo.motion.YoyoMotion;
import com.rainbowyoyo.util.Rainbow;
import java.util.ArrayDeque;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the thrown yo-yo: the string from the owner's hand, the glowing rainbow trail and the
 * spinning yo-yo itself, all at the exact position of its motion for the current frame.
 */
public class YoyoRenderer extends EntityRenderer<YoyoEntity> {
    private static final float STRING_HALF_WIDTH = 0.02F;
    private static final int STRING_SEGMENTS = 20;
    private static final long TRAIL_MILLIS = 380L;
    private static final int TRAIL_MAX_POINTS = 64;

    public YoyoRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(YoyoEntity entity) {
        return YoyoModelRenderer.TEXTURE;
    }

    /** The string can cross the whole screen even when the yo-yo itself is out of view. */
    @Override
    public boolean shouldRender(YoyoEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(YoyoEntity yoyo, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        Player owner = yoyo.getOwner();
        if (owner == null) {
            return;
        }
        Vec3 base = yoyo.getPosition(partialTick); // where the dispatcher put the pose stack
        Vec3 pos = yoyo.renderPosition(partialTick);
        HumanoidArm arm = owner.getMainArm();
        Vec3 hand = YoyoArm.handTip(owner, partialTick, arm, YoyoArm.finalAngles(owner, partialTick, arm, pos));
        Vec3 camera = this.entityRenderDispatcher.camera.getPosition();
        float scale = yoyo.renderScale(partialTick);

        poseStack.pushPose();
        drawString(poseStack, buffers, light, base, hand, pos, yoyo.renderSlack(partialTick), camera);
        if (YoyoConfig.CLIENT.trail.get()) {
            drawTrail(poseStack, buffers, yoyo, base, pos, camera, scale);
        }
        poseStack.translate(pos.x - base.x, pos.y - base.y, pos.z - base.z);
        poseStack.mulPose(orientation(yoyo, owner, partialTick, hand, pos));
        poseStack.scale(scale, scale, scale);
        YoyoModelRenderer.render(poseStack, buffers, light, OverlayTexture.NO_OVERLAY, yoyo.renderGlow(partialTick));
        poseStack.popPose();
    }

    /** Turns the mesh (axle along X) so its axle follows the trick, then spins it around the axle. */
    private static Quaternionf orientation(YoyoEntity yoyo, Player owner, float partialTick, Vec3 hand, Vec3 pos) {
        Vector3f target = switch (YoyoMotion.axle(yoyo.getPhase())) {
            case RIGHT -> toVector(V3.right(yoyo.getPhaseYaw()));
            case UP -> new Vector3f(0.0F, 1.0F, 0.0F);
            case FORWARD -> direction(hand.subtract(pos), owner, partialTick);
            case STRING -> direction(pos.subtract(hand).cross(new Vec3(0.0, 1.0, 0.0)), owner, partialTick);
        };
        long now = Util.getNanos();
        if (yoyo.renderAxle == null) {
            yoyo.renderAxle = target;
        } else {
            float dt = Math.min(0.1F, Math.max(0.0F, (now - yoyo.renderAxleNanos) / 1.0E9F));
            if (yoyo.renderAxle.dot(target) < 0.0F) {
                target.negate(); // an axle has no front: never flip around
            }
            yoyo.renderAxle.lerp(target, 1.0F - (float) Math.exp(-dt * 14.0F));
            if (yoyo.renderAxle.lengthSquared() < 1.0E-6F) {
                yoyo.renderAxle.set(target);
            }
            yoyo.renderAxle.normalize();
        }
        yoyo.renderAxleNanos = now;
        Vector3f axle = yoyo.renderAxle;
        return new Quaternionf().rotationTo(1.0F, 0.0F, 0.0F, axle.x(), axle.y(), axle.z())
                .rotateX(yoyo.renderSpin(partialTick));
    }

    private static Vector3f direction(Vec3 v, Player owner, float partialTick) {
        if (v.lengthSqr() < 1.0E-6) {
            return toVector(V3.right(owner.getViewYRot(partialTick)));
        }
        Vec3 n = v.normalize();
        return new Vector3f((float) n.x, (float) n.y, (float) n.z);
    }

    private static Vector3f toVector(V3 v) {
        return new Vector3f((float) v.x(), (float) v.y(), (float) v.z());
    }

    // ------------------------------------------------------------------ string

    private static void drawString(PoseStack poseStack, MultiBufferSource buffers, int light, Vec3 base,
                                   Vec3 from, Vec3 to, float slack, Vec3 camera) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(YoyoModelRenderer.WHITE));
        PoseStack.Pose pose = poseStack.last();
        double sag = slack * Math.min(1.5, 0.08 * from.distanceTo(to));
        Vec3 previous = from;
        for (int i = 1; i <= STRING_SEGMENTS; i++) {
            double t = i / (double) STRING_SEGMENTS;
            Vec3 next = from.lerp(to, t).add(0.0, -sag * 4.0 * t * (1.0 - t), 0.0);
            Vec3 side = side(next.subtract(previous), previous.subtract(camera), STRING_HALF_WIDTH);
            if (side != null) {
                stringVertex(consumer, pose, previous.add(side).subtract(base), light);
                stringVertex(consumer, pose, previous.subtract(side).subtract(base), light);
                stringVertex(consumer, pose, next.subtract(side).subtract(base), light);
                stringVertex(consumer, pose, next.add(side).subtract(base), light);
            }
            previous = next;
        }
    }

    private static void stringVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p, int light) {
        consumer.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
                .color(250, 250, 255, 255)
                .uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    /** Half-width vector across a segment, facing the camera, or null if the segment is degenerate. */
    private static Vec3 side(Vec3 along, Vec3 view, double halfWidth) {
        Vec3 side = along.cross(view);
        if (side.lengthSqr() < 1.0E-12) {
            side = along.cross(new Vec3(0.0, 1.0, 0.0));
            if (side.lengthSqr() < 1.0E-12) {
                return null;
            }
        }
        return side.normalize().scale(halfWidth);
    }

    // ------------------------------------------------------------------ trail

    /** A glowing rainbow ribbon through the yo-yo's positions of the last few hundred milliseconds. */
    private static void drawTrail(PoseStack poseStack, MultiBufferSource buffers, YoyoEntity yoyo, Vec3 base,
                                  Vec3 pos, Vec3 camera, float scale) {
        long now = Util.getMillis();
        ArrayDeque<double[]> trail = yoyo.trail;
        double[] newest = trail.peekFirst();
        if (newest == null || squaredDistance(newest, pos) > 0.0025) {
            trail.addFirst(new double[]{pos.x, pos.y, pos.z, now});
        }
        while (!trail.isEmpty() && (now - (long) trail.peekLast()[3] > TRAIL_MILLIS || trail.size() > TRAIL_MAX_POINTS)) {
            trail.removeLast();
        }
        if (trail.size() < 3) {
            return;
        }
        double[][] points = trail.toArray(new double[0][]);
        int n = points.length;
        Vec3[] centers = new Vec3[n];
        Vec3[] sides = new Vec3[n];
        int[] colors = new int[n];
        for (int i = 0; i < n; i++) {
            centers[i] = new Vec3(points[i][0], points[i][1], points[i][2]);
        }
        for (int i = 0; i < n; i++) {
            Vec3 along = centers[Math.min(n - 1, i + 1)].subtract(centers[Math.max(0, i - 1)]);
            float age = Math.min(1.0F, (now - (long) points[i][3]) / (float) TRAIL_MILLIS);
            float fade = 1.0F - age;
            sides[i] = side(along, centers[i].subtract(camera), 0.6 * scale * fade);
            int rgb = Rainbow.rgb((points[i][3] * 0.0011 + i * 0.035) % 1.0);
            float bright = (float) Math.pow(fade, 1.3) * 0.85F;
            colors[i] = ((int) (((rgb >> 16) & 255) * bright) << 16) | ((int) (((rgb >> 8) & 255) * bright) << 8)
                    | (int) ((rgb & 255) * bright);
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(YoyoModelRenderer.WHITE));
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i + 1 < n; i++) {
            if (sides[i] == null || sides[i + 1] == null) {
                continue;
            }
            Vec3 a1 = centers[i].add(sides[i]).subtract(base);
            Vec3 a2 = centers[i].subtract(sides[i]).subtract(base);
            Vec3 b1 = centers[i + 1].add(sides[i + 1]).subtract(base);
            Vec3 b2 = centers[i + 1].subtract(sides[i + 1]).subtract(base);
            // Both windings: the eyes render type culls back faces.
            trailVertex(consumer, pose, a1, colors[i]);
            trailVertex(consumer, pose, a2, colors[i]);
            trailVertex(consumer, pose, b2, colors[i + 1]);
            trailVertex(consumer, pose, b1, colors[i + 1]);
            trailVertex(consumer, pose, a1, colors[i]);
            trailVertex(consumer, pose, b1, colors[i + 1]);
            trailVertex(consumer, pose, b2, colors[i + 1]);
            trailVertex(consumer, pose, a2, colors[i]);
        }
    }

    private static void trailVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p, int rgb) {
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        consumer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255)
                .uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    private static double squaredDistance(double[] p, Vec3 v) {
        double dx = p[0] - v.x;
        double dy = p[1] - v.y;
        double dz = p[2] - v.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
