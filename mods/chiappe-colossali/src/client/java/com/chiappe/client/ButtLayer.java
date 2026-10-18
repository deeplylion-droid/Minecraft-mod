package com.chiappe.client;

import com.chiappe.Butt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * The cheeks: two round shapes behind the hips, following the body (crouching, swimming, flying),
 * wearing the back of the player's own legs from the skin (the trousers, for most skins) and its
 * outer layer on top. Model space here: Y down, the player's right is -X, the back is +Z.
 */
public class ButtLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Unit spheres, four corners per quad, in the order that faces outwards: a finer one for the big cheeks. */
    private static final float[] SPHERE = sphere(16, 24);
    private static final float[] SPHERE_FINE = sphere(28, 44);
    /** Skin areas (64x64 pixels): the upper back of each leg, and the same on the trousers layer. */
    private static final float[][] BASE_UV = {{12, 20, 16, 26}, {28, 52, 32, 58}};
    private static final float[][] OUTER_UV = {{12, 36, 16, 42}, {12, 52, 16, 58}};
    /** The player model is drawn at 15/16 of its size: the cheeks are given in real blocks. */
    private static final float MODEL_SCALE = 0.9375F;

    public ButtLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    private static float[] sphere(int lat, int lon) {
        float[] out = new float[lat * lon * 4 * 3];
        int n = 0;
        for (int j = 0; j < lat; j++) {
            for (int i = 0; i < lon; i++) {
                float[][] q = {corner(j, i, lat, lon), corner(j, i + 1, lat, lon), corner(j + 1, i + 1, lat, lon), corner(j + 1, i, lat, lon)};
                // keep the winding that faces outwards (the cull test of the render type needs it)
                float[] a = q[0], b = q[1], d = q[3];
                if (j == 0) {
                    b = q[2];
                }
                float cx = (b[1] - a[1]) * (d[2] - a[2]) - (b[2] - a[2]) * (d[1] - a[1]);
                float cy = (b[2] - a[2]) * (d[0] - a[0]) - (b[0] - a[0]) * (d[2] - a[2]);
                float cz = (b[0] - a[0]) * (d[1] - a[1]) - (b[1] - a[1]) * (d[0] - a[0]);
                float mx = (q[0][0] + q[1][0] + q[2][0] + q[3][0]), my = (q[0][1] + q[1][1] + q[2][1] + q[3][1]),
                        mz = (q[0][2] + q[1][2] + q[2][2] + q[3][2]);
                boolean outwards = cx * mx + cy * my + cz * mz > 0.0F;
                for (int k = 0; k < 4; k++) {
                    float[] c = q[outwards ? k : 3 - k];
                    out[n++] = c[0];
                    out[n++] = c[1];
                    out[n++] = c[2];
                }
            }
        }
        return out;
    }

    private static float[] corner(int j, int i, int lat, int lon) {
        double theta = Math.PI * j / lat;
        double phi = 2.0 * Math.PI * i / lon;
        return new float[]{(float) (Math.sin(theta) * Math.cos(phi)), (float) -Math.cos(theta), (float) (Math.sin(theta) * Math.sin(phi))};
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        ButtVisual v = state.getData(ButtVisual.KEY);
        if (v == null || v.skin == null || state.isInvisible || state.isSpectator) {
            return;
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        // placed in blocks (see Butt), drawn in the model's units
        double rw = v.diameter / 2.0;
        float r = (float) rw / MODEL_SCALE;
        float side = (float) (Butt.SIDE * r);
        float lift = (float) ((Butt.centreHeight(rw) - Butt.HIPS) / MODEL_SCALE);
        float back = (float) (Butt.back(rw) / MODEL_SCALE);
        float[][] cheek = new float[2][];
        for (int i = 0; i < 2; i++) {
            float sign = i == 0 ? -1.0F : 1.0F;
            float tall = 1.0F + v.s[i];
            float wide = 1.0F / Mth.sqrt(tall);
            cheek[i] = new float[]{sign * (side + v.x[i] * r), -(lift + v.y[i] * r), back + v.z[i] * r,
                    r * wide, (float) Butt.TALL * r * tall, (float) Butt.DEEP * r * wide, sign};
        }

        poseStack.pushPose();
        this.getParentModel().root().translateAndRotate(poseStack);
        this.getParentModel().body.translateAndRotate(poseStack);
        // the back of the hips
        poseStack.translate(0.0F, 12.0F / 16.0F, 2.0F / 16.0F);
        float[] sphere = rw > 0.75 ? SPHERE_FINE : SPHERE;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutCull(v.skin), (pose, buffer) -> {
            for (int i = 0; i < 2; i++) {
                emit(pose, buffer, sphere, cheek[i], 1.0F, BASE_UV[i], light, overlay);
                emit(pose, buffer, sphere, cheek[i], 1.04F, OUTER_UV[i], light, overlay);
            }
        });
        poseStack.popPose();
    }

    /** Darker deep in the cleft and under the cheeks (model space: y down, z back), so the shape reads from afar. */
    private static int shade(float inner, float down, float back) {
        float cleft = smooth(0.35F, 0.9F, inner) * (0.55F + 0.45F * smooth(-0.3F, 0.6F, back));
        float under = smooth(0.45F, 1.0F, down);
        int k = (int) (255.0F * (1.0F - 0.42F * cleft - 0.25F * under));
        return 0xFF000000 | k << 16 | k << 8 | k;
    }

    private static float smooth(float from, float to, float x) {
        float t = Mth.clamp((x - from) / (to - from), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** One cheek: c = centre x, y, z, the radius across, up and back, side sign. */
    private static void emit(PoseStack.Pose pose, VertexConsumer buffer, float[] sphere, float[] c, float grow, float[] uv, int light, int overlay) {
        float rw = c[3] * grow, rt = c[4] * grow, rd = c[5] * grow;
        float u0 = uv[0] / 64.0F, v0 = uv[1] / 64.0F, du = (uv[2] - uv[0]) / 64.0F, dv = (uv[3] - uv[1]) / 64.0F;
        float sign = c[6];
        for (int n = 0; n < sphere.length; n += 3) {
            float ux = sphere[n], uy = sphere[n + 1], uz = sphere[n + 2];
            // normals of a stretched sphere: divide by the stretch
            float nx = ux / rw, ny = uy / rt, nz = uz / rd;
            float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            // the skin is seen as from behind: across the cheek and top to bottom
            float u = u0 + (0.5F + 0.5F * ux * -sign) * du;
            float vv = v0 + (0.5F + 0.5F * uy) * dv;
            buffer.addVertex(pose, c[0] + ux * rw, c[1] + uy * rt, c[2] + uz * rd)
                    .setColor(shade(ux * -sign, uy, uz))
                    .setUv(u, vv)
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, nx / len, ny / len, nz / len);
        }
    }
}
