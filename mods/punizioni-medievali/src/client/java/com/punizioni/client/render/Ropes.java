package com.punizioni.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.punizioni.Punizioni;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Ropes (and other long strips) drawn as ribbons turned towards the camera. */
public final class Ropes {
    private Ropes() {
    }

    public static final Identifier ROPE = Punizioni.id("textures/entity/rope.png");
    private static final RenderType ROPE_TYPE = RenderTypes.entityCutout(ROPE);

    /** A hanging rope between two points: a little sag in the middle (blocks). */
    public static List<Vec3> sag(Vec3 a, Vec3 b, double sag, int segments) {
        List<Vec3> pts = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double k = i / (double) segments;
            pts.add(a.lerp(b, k).add(0.0, -sag * 4.0 * k * (1.0 - k), 0.0));
        }
        return pts;
    }

    /**
     * A strip along the points (world positions); origin is where the pose stack is (the entity),
     * camera where the eye is. The texture repeats every {@code width * 4} blocks along the rope.
     */
    public static void rope(SubmitNodeCollector collector, PoseStack poseStack, Vec3 origin, Vec3 camera, List<Vec3> pts, float width, int light) {
        strip(collector, poseStack, origin, camera, pts, width, ROPE_TYPE, light, 0xFFFFFFFF, width * 4.0F);
    }

    public static void strip(SubmitNodeCollector collector, PoseStack poseStack, Vec3 origin, Vec3 camera, List<Vec3> pts, float width,
                             RenderType type, int light, int color, float repeat) {
        if (pts.size() < 2) {
            return;
        }
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            float v = 0.0F;
            for (int i = 0; i < pts.size() - 1; i++) {
                Vec3 p0 = pts.get(i);
                Vec3 p1 = pts.get(i + 1);
                Vec3 dir = p1.subtract(p0);
                double len = dir.length();
                if (len < 1.0E-5) {
                    continue;
                }
                Vec3 side0 = side(p0, dir, camera, width);
                Vec3 side1 = side(p1, dir, camera, width);
                float v1 = v + (float) (len / repeat);
                Vec3 a = p0.subtract(origin);
                Vec3 b = p1.subtract(origin);
                Vec3 n = camera.subtract(p0).normalize();
                vertex(buffer, pose, a.subtract(side0), 0.0F, v, light, color, n);
                vertex(buffer, pose, b.subtract(side1), 0.0F, v1, light, color, n);
                vertex(buffer, pose, b.add(side1), 1.0F, v1, light, color, n);
                vertex(buffer, pose, a.add(side0), 1.0F, v, light, color, n);
                v = v1;
            }
        });
    }

    private static Vec3 side(Vec3 p, Vec3 dir, Vec3 camera, float width) {
        Vec3 toCam = camera.subtract(p);
        Vec3 s = dir.cross(toCam);
        double l = s.length();
        if (l < 1.0E-6) {
            s = dir.cross(new Vec3(0.0, 1.0, 0.0));
            l = Math.max(1.0E-6, s.length());
        }
        return s.scale(width * 0.5 / l);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, float u, float v, int light, int color, Vec3 n) {
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }
}
