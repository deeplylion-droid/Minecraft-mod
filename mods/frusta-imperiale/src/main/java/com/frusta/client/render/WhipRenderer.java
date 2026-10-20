package com.frusta.client.render;

import com.frusta.client.WhipAnim;
import com.frusta.motion.V3;
import com.frusta.motion.Whip;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the whips: a braided tube that follows the whip's points (smoothed with Catmull-Rom),
 * thick at the handle and thin at the tip, lit by the blocks it passes through, and shimmering if the
 * whip is enchanted. Texture: {@code u} around, {@code v} from the handle (0) to the tip (1).
 */
public final class WhipRenderer {
    private static final int SUB = 3;
    private static final int SIDES = 8;
    private static final int POINTS = (Whip.NODES - 1) * SUB + 1;
    private static final double MAX_DISTANCE = 128.0;

    private static final float[] PX = new float[POINTS + 1];
    private static final float[] PY = new float[POINTS + 1];
    private static final float[] PZ = new float[POINTS + 1];
    private static final float[] TX = new float[POINTS + 1];
    private static final float[] TY = new float[POINTS + 1];
    private static final float[] TZ = new float[POINTS + 1];
    private static final float[] NX = new float[POINTS + 1];
    private static final float[] NY = new float[POINTS + 1];
    private static final float[] NZ = new float[POINTS + 1];
    private static final float[] BX = new float[POINTS + 1];
    private static final float[] BY = new float[POINTS + 1];
    private static final float[] BZ = new float[POINTS + 1];
    private static final float[] RADIUS = new float[POINTS + 1];
    private static final float[] TEX_V = new float[POINTS + 1];
    private static final int[] LIGHT = new int[POINTS + 1];
    private static final int[] NODE_LIGHT = new int[Whip.NODES];
    private static final float[] COS = new float[SIDES + 1];
    private static final float[] SIN = new float[SIDES + 1];

    static {
        for (int j = 0; j <= SIDES; j++) {
            double theta = 2.0 * Math.PI * j / SIDES;
            COS[j] = (float) Math.cos(theta);
            SIN[j] = (float) Math.sin(theta);
        }
    }

    private WhipRenderer() {
    }

    public static RenderType renderType(ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
    }

    /** Draws every whip (after the entities, in the world). */
    public static void renderAll(PoseStack poseStack, Vec3 camera, Iterable<Map.Entry<Integer, WhipAnim>> anims) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean anyFoil = false;
        for (Map.Entry<Integer, WhipAnim> entry : anims) {
            WhipAnim view = entry.getValue();
            if (!view.hasNodes()) {
                continue;
            }
            V3 first = view.node(0);
            if (first.sub(new V3(camera.x, camera.y, camera.z)).length() > MAX_DISTANCE) {
                continue;
            }
            RenderType type = renderType(view.material().lashTexture());
            VertexConsumer consumer = ItemRenderer.getArmorFoilBuffer(buffers, type, false, view.foil());
            draw(view, level, poseStack, consumer, camera);
            buffers.endBatch(type);
            anyFoil |= view.foil();
        }
        if (anyFoil) {
            // after the whips: the glint only shows where a whip already is
            buffers.endBatch(RenderType.armorEntityGlint());
        }
    }

    private static void draw(WhipAnim view, ClientLevel level, PoseStack poseStack, VertexConsumer consumer, Vec3 camera) {
        build(view, level, camera);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        for (int i = 0; i < POINTS; i++) {
            for (int j = 0; j < SIDES; j++) {
                emit(consumer, matrix, normal, i, j);
                emit(consumer, matrix, normal, i, j + 1);
                emit(consumer, matrix, normal, i + 1, j + 1);
                emit(consumer, matrix, normal, i + 1, j);
            }
        }
    }

    /** Smooth centre line, frames carried along it, radius and light at every point. */
    private static void build(WhipAnim view, ClientLevel level, Vec3 camera) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int k = 0; k < Whip.NODES; k++) {
            V3 n = view.node(k);
            pos.set(n.x(), n.y(), n.z());
            NODE_LIGHT[k] = LevelRenderer.getLightColor(level, pos);
        }
        int p = 0;
        for (int k = 0; k < Whip.NODES - 1; k++) {
            V3 p0 = view.node(Math.max(0, k - 1));
            V3 p1 = view.node(k);
            V3 p2 = view.node(k + 1);
            V3 p3 = view.node(Math.min(Whip.NODES - 1, k + 2));
            for (int s = 0; s < SUB; s++) {
                setPoint(p++, catmull(p0, p1, p2, p3, (double) s / SUB), camera, NODE_LIGHT[s * 2 < SUB ? k : k + 1]);
            }
        }
        setPoint(p, view.node(Whip.NODES - 1), camera, NODE_LIGHT[Whip.NODES - 1]);

        // tangents
        for (int i = 0; i < POINTS; i++) {
            int a = Math.max(0, i - 1);
            int b = Math.min(POINTS - 1, i + 1);
            float dx = PX[b] - PX[a];
            float dy = PY[b] - PY[a];
            float dz = PZ[b] - PZ[a];
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-6F) {
                dx = 0;
                dy = -1;
                dz = 0;
                len = 1;
            }
            TX[i] = dx / len;
            TY[i] = dy / len;
            TZ[i] = dz / len;
            double f = (double) i / (POINTS - 1);
            RADIUS[i] = (float) Whip.radius(f);
            TEX_V[i] = (float) f;
        }
        // a pointed end for the cracker, a little past the last point
        int last = POINTS - 1;
        float r = RADIUS[last];
        PX[POINTS] = PX[last] + TX[last] * r * 1.5F;
        PY[POINTS] = PY[last] + TY[last] * r * 1.5F;
        PZ[POINTS] = PZ[last] + TZ[last] * r * 1.5F;
        TX[POINTS] = TX[last];
        TY[POINTS] = TY[last];
        TZ[POINTS] = TZ[last];
        RADIUS[POINTS] = 0.0F;
        TEX_V[POINTS] = 1.0F;
        LIGHT[POINTS] = LIGHT[last];

        // frames carried along the curve (projection-based parallel transport), starting "up"
        float ux = 0;
        float uy = 1;
        float uz = 0;
        if (Math.abs(TY[0]) > 0.9F) {
            ux = 1;
            uy = 0;
        }
        for (int i = 0; i <= POINTS; i++) {
            float d = ux * TX[i] + uy * TY[i] + uz * TZ[i];
            float ax = ux - d * TX[i];
            float ay = uy - d * TY[i];
            float az = uz - d * TZ[i];
            float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            if (len < 1e-4F) {
                ax = TY[i];
                ay = -TX[i];
                az = 0;
                len = (float) Math.sqrt(ax * ax + ay * ay);
                if (len < 1e-4F) {
                    ax = 1;
                    ay = 0;
                    len = 1;
                }
            }
            NX[i] = ax / len;
            NY[i] = ay / len;
            NZ[i] = az / len;
            BX[i] = TY[i] * NZ[i] - TZ[i] * NY[i];
            BY[i] = TZ[i] * NX[i] - TX[i] * NZ[i];
            BZ[i] = TX[i] * NY[i] - TY[i] * NX[i];
            ux = NX[i];
            uy = NY[i];
            uz = NZ[i];
        }
    }

    private static void setPoint(int i, V3 v, Vec3 camera, int light) {
        PX[i] = (float) (v.x() - camera.x);
        PY[i] = (float) (v.y() - camera.y);
        PZ[i] = (float) (v.z() - camera.z);
        LIGHT[i] = light;
    }

    private static V3 catmull(V3 p0, V3 p1, V3 p2, V3 p3, double s) {
        double s2 = s * s;
        double s3 = s2 * s;
        return new V3(
                0.5 * (2 * p1.x() + (-p0.x() + p2.x()) * s + (2 * p0.x() - 5 * p1.x() + 4 * p2.x() - p3.x()) * s2
                        + (-p0.x() + 3 * p1.x() - 3 * p2.x() + p3.x()) * s3),
                0.5 * (2 * p1.y() + (-p0.y() + p2.y()) * s + (2 * p0.y() - 5 * p1.y() + 4 * p2.y() - p3.y()) * s2
                        + (-p0.y() + 3 * p1.y() - 3 * p2.y() + p3.y()) * s3),
                0.5 * (2 * p1.z() + (-p0.z() + p2.z()) * s + (2 * p0.z() - 5 * p1.z() + 4 * p2.z() - p3.z()) * s2
                        + (-p0.z() + 3 * p1.z() - 3 * p2.z() + p3.z()) * s3));
    }

    private static void emit(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, int i, int j) {
        float c = COS[j];
        float s = SIN[j];
        float rx = c * NX[i] + s * BX[i];
        float ry = c * NY[i] + s * BY[i];
        float rz = c * NZ[i] + s * BZ[i];
        float r = RADIUS[i];
        consumer.vertex(matrix, PX[i] + rx * r, PY[i] + ry * r, PZ[i] + rz * r)
                .color(255, 255, 255, 255)
                .uv((float) j / SIDES, TEX_V[i])
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LIGHT[i])
                .normal(normal, rx, ry, rz)
                .endVertex();
    }
}
