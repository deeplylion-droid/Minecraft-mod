package com.punizioni.client.royal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.punizioni.Punizioni;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The royal mantles: one cloth per player wearing one, drawn every frame. The texture's left half is
 * the outside (red velvet, gold embroidery, an ermine border), the right half the lining (ermine).
 */
public final class Capes {
    private Capes() {
    }

    public static final Identifier TEXTURE = Punizioni.id("textures/entity/royal_cape.png");
    public static final RenderType TYPE = RenderTypes.entityCutoutCull(TEXTURE);

    private static final Map<Integer, CapeCloth> CAPES = new HashMap<>();

    public static CapeCloth of(int entityId) {
        return CAPES.computeIfAbsent(entityId, id -> new CapeCloth());
    }

    public static void reset() {
        CAPES.clear();
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        for (Iterator<CapeCloth> it = CAPES.values().iterator(); it.hasNext(); ) {
            if (now - it.next().lastSeen > 200L) {
                it.remove();
            }
        }
    }

    /** The simulated mantle, through {@code toSubmit} (world point minus base, then the matrix). */
    public static void submit(SubmitNodeCollector collector, CapeCloth cloth, Matrix4f toSubmit, double baseX, double baseY, double baseZ, int light,
                              int overlay) {
        if (!cloth.ready()) {
            return;
        }
        int rows = CapeCloth.ROWS;
        int cols = CapeCloth.COLS;
        float[] x = new float[rows * cols];
        float[] y = new float[rows * cols];
        float[] z = new float[rows * cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                x[i] = (float) (cloth.px(r, c) - baseX);
                y[i] = (float) (cloth.py(r, c) - baseY);
                z[i] = (float) (cloth.pz(r, c) - baseZ);
            }
        }
        boolean flip = cloth.outerFlip;
        PoseStack ps = new PoseStack();
        ps.mulPose(toSubmit);
        collector.submitCustomGeometry(ps, TYPE, (pose, buffer) -> sheet(pose, buffer, x, y, z, rows, cols, flip, light, overlay));
    }

    /**
     * A still mantle in the body's own space, for the inventory's little window: down to the heels, a
     * little apart from the legs, with soft folds.
     */
    public static void submitStill(SubmitNodeCollector collector, PoseStack bodyPose, int light, int overlay) {
        int rows = 9;
        int cols = CapeCloth.COLS;
        float[] x = new float[rows * cols];
        float[] y = new float[rows * cols];
        float[] z = new float[rows * cols];
        Vector3f a = new Vector3f();
        for (int r = 0; r < rows; r++) {
            float v = (float) r / (rows - 1);
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                CapeCloth.anchor(c, a);
                float u = (float) c / (cols - 1) - 0.5F;
                float spread = 1.0F + v * 0.55F;
                x[i] = a.x * spread;
                y[i] = a.y + v * 23.0F / 16.0F;
                z[i] = a.z + (v * 3.2F + Mth.sin(u * 9.0F + 0.6F) * 0.5F * v) / 16.0F;
            }
        }
        // in the model's space the winding that faces the back is the other one
        collector.submitCustomGeometry(bodyPose, TYPE, (pose, buffer) -> sheet(pose, buffer, x, y, z, rows, cols, true, light, overlay));
    }

    private static void sheet(PoseStack.Pose pose, VertexConsumer buffer, float[] x, float[] y, float[] z, int rows, int cols, boolean flip, int light,
                              int overlay) {
        float[] nx = new float[rows * cols];
        float[] ny = new float[rows * cols];
        float[] nz = new float[rows * cols];
        Vector3f h = new Vector3f();
        Vector3f d = new Vector3f();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                int l = r * cols + Math.max(0, c - 1);
                int rr = r * cols + Math.min(cols - 1, c + 1);
                int u = Math.max(0, r - 1) * cols + c;
                int dn = Math.min(rows - 1, r + 1) * cols + c;
                // down x across: the same handedness as the quads' (down, then across) winding
                d.set(x[dn] - x[u], y[dn] - y[u], z[dn] - z[u]);
                h.set(x[rr] - x[l], y[rr] - y[l], z[rr] - z[l]);
                d.cross(h);
                if (d.lengthSquared() < 1.0E-12F) {
                    d.set(0.0F, 0.0F, 1.0F);
                }
                d.normalize();
                if (flip) {
                    d.negate();
                }
                nx[i] = d.x;
                ny[i] = d.y;
                nz[i] = d.z;
            }
        }
        for (int r = 0; r + 1 < rows; r++) {
            float v0 = (float) r / (rows - 1);
            float v1 = (float) (r + 1) / (rows - 1);
            for (int c = 0; c + 1 < cols; c++) {
                float u0 = (float) c / (cols - 1);
                float u1 = (float) (c + 1) / (cols - 1);
                int a = r * cols + c;
                int b = (r + 1) * cols + c;
                int e = (r + 1) * cols + c + 1;
                int f = r * cols + c + 1;
                // the velvet, facing away from the body
                if (!flip) {
                    quad(pose, buffer, x, y, z, nx, ny, nz, a, b, e, f, u0 * 0.5F, u0 * 0.5F, u1 * 0.5F, u1 * 0.5F, v0, v1, v1, v0, 1.0F, light,
                            overlay);
                    quad(pose, buffer, x, y, z, nx, ny, nz, f, e, b, a, 0.5F + u1 * 0.5F, 0.5F + u1 * 0.5F, 0.5F + u0 * 0.5F, 0.5F + u0 * 0.5F,
                            v0, v1, v1, v0, -1.0F, light, overlay);
                } else {
                    quad(pose, buffer, x, y, z, nx, ny, nz, f, e, b, a, u1 * 0.5F, u1 * 0.5F, u0 * 0.5F, u0 * 0.5F, v0, v1, v1, v0, 1.0F, light,
                            overlay);
                    quad(pose, buffer, x, y, z, nx, ny, nz, a, b, e, f, 0.5F + u0 * 0.5F, 0.5F + u0 * 0.5F, 0.5F + u1 * 0.5F, 0.5F + u1 * 0.5F,
                            v0, v1, v1, v0, -1.0F, light, overlay);
                }
            }
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float[] x, float[] y, float[] z, float[] nx, float[] ny, float[] nz, int i0,
                             int i1, int i2, int i3, float u0, float u1, float u2, float u3, float v0, float v1, float v2, float v3, float side,
                             int light, int overlay) {
        vertex(pose, buffer, x, y, z, nx, ny, nz, i0, u0, v0, side, light, overlay);
        vertex(pose, buffer, x, y, z, nx, ny, nz, i1, u1, v1, side, light, overlay);
        vertex(pose, buffer, x, y, z, nx, ny, nz, i2, u2, v2, side, light, overlay);
        vertex(pose, buffer, x, y, z, nx, ny, nz, i3, u3, v3, side, light, overlay);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float[] x, float[] y, float[] z, float[] nx, float[] ny, float[] nz, int i,
                               float u, float v, float side, int light, int overlay) {
        buffer.addVertex(pose, x[i], y[i], z[i]).setColor(-1).setUv(u, v).setOverlay(overlay).setLight(light)
                .setNormal(pose, nx[i] * side, ny[i] * side, nz[i] * side);
    }
}
