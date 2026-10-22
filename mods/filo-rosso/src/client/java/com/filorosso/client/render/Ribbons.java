package com.filorosso.client.render;

import com.filorosso.FiloRosso;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Ribbons that always face the camera, through points given in the world (threads, strings, rings). */
public final class Ribbons {
    private Ribbons() {
    }

    public static final RenderType GLOW = RenderTypes.eyes(FiloRosso.id("textures/entity/glow_line.png"));
    public static final RenderType THREAD = RenderTypes.entityCutout(FiloRosso.id("textures/entity/thread.png"));

    /**
     * Draws through world points; {@code origin} is the world point the pose stack is at (the
     * camera in a level renderer, the entity in an entity renderer).
     */
    public static void draw(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, Vec3 origin, Vec3 camera, List<Vec3> points,
                            float width, int argb, int light, float v0, float v1) {
        int n = points.size() - 1;
        if (n < 1) {
            return;
        }
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            Vec3[] sides = new Vec3[n + 1];
            for (int i = 0; i <= n; i++) {
                Vec3 p = points.get(i);
                Vec3 tangent = points.get(Math.min(n, i + 1)).subtract(points.get(Math.max(0, i - 1)));
                Vec3 s = tangent.cross(camera.subtract(p));
                if (s.lengthSqr() < 1.0E-8) {
                    s = tangent.cross(new Vec3(0.0, 1.0, 0.0));
                }
                if (s.lengthSqr() < 1.0E-8) {
                    s = new Vec3(1.0, 0.0, 0.0);
                }
                sides[i] = s.normalize().scale(width * 0.5);
            }
            for (int i = 0; i < n; i++) {
                Vec3 p0 = points.get(i).subtract(origin);
                Vec3 p1 = points.get(i + 1).subtract(origin);
                vertex(buffer, pose, p0.subtract(sides[i]), 0.0F, v0, argb, light);
                vertex(buffer, pose, p0.add(sides[i]), 1.0F, v0, argb, light);
                vertex(buffer, pose, p1.add(sides[i + 1]), 1.0F, v1, argb, light);
                vertex(buffer, pose, p1.subtract(sides[i + 1]), 0.0F, v1, argb, light);
            }
        });
    }

    /** A soft glowing line (additive: a darker colour is a fainter line). */
    public static void glow(SubmitNodeCollector collector, PoseStack poseStack, Vec3 origin, Vec3 camera, List<Vec3> points, float width, int rgb,
                            float alpha) {
        int c = ARGB.scaleRGB(0xFF000000 | rgb, Mth.clamp(alpha, 0.0F, 1.0F));
        draw(collector, poseStack, GLOW, origin, camera, points, width, c, LightCoordsUtil.FULL_BRIGHT, 0.0F, 1.0F);
    }

    /** A red thread like the couples' own (tile of thread.png). */
    public static void thread(SubmitNodeCollector collector, PoseStack poseStack, Vec3 origin, Vec3 camera, List<Vec3> points, float width, int tile,
                              int light) {
        draw(collector, poseStack, THREAD, origin, camera, points, width, 0xFFFFFFFF, light, tile / 10.0F, (tile + 1) / 10.0F);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, float u, float v, int color, int light) {
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** Points of a straight segment with a little sag and a quiver (taut strings). */
    public static List<Vec3> line(Vec3 a, Vec3 b, int segments, double sag, double quiver, float time, int seed) {
        List<Vec3> out = new ArrayList<>(segments + 1);
        Vec3 d = b.subtract(a);
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double bell = 4.0 * t * (1.0 - t);
            Vec3 p = a.add(d.scale(t)).add(0.0, -sag * bell, 0.0);
            if (quiver > 0.0) {
                p = p.add(side.scale(quiver * bell * Math.sin(time * 1.9 + seed + t * 11.0)));
            }
            out.add(p);
        }
        return out;
    }

    /** A circle on the ground. */
    public static List<Vec3> circle(Vec3 c, double r, int segments, double spin) {
        List<Vec3> out = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double a = i * Math.PI * 2.0 / segments + spin;
            out.add(c.add(Math.cos(a) * r, 0.0, Math.sin(a) * r));
        }
        return out;
    }
}
