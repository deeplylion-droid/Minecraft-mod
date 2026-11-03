package com.punizioni.client.witch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.punizioni.Punizioni;
import com.punizioni.client.render.Visuals;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The balloon: a round body with the player's skin stretched over it (the shirt all the way round,
 * the outer layer on top, a shine), a knot underneath, the little arms sticking out of its sides
 * and the legs dangling; it wobbles, squashes when it bobs and when it is hit, leans the way it
 * drifts. The arms and legs are a model of its own (the player model is shared by everyone and
 * drawn later: its parts cannot carry a pose per player).
 */
public final class Balloons {
    private Balloons() {
    }

    private static final Identifier SHINE = Punizioni.id("textures/entity/balloon_shine.png");
    private static final RenderType SHINE_TYPE = RenderTypes.entityTranslucent(SHINE);
    private static final int LON = 24;
    private static final int LAT = 14;

    /** The arms, the legs (and, while it whizzes round, the head) of a balloon, posed by numbers. */
    static final class Limbs extends Model<float[]> {
        private final ModelPart head, body, rightArm, leftArm, rightLeg, leftLeg;

        Limbs(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
            this.head = root.getChild("head");
            this.body = root.getChild("body");
            this.rightArm = root.getChild("right_arm");
            this.leftArm = root.getChild("left_arm");
            this.rightLeg = root.getChild("right_leg");
            this.leftLeg = root.getChild("left_leg");
        }

        /**
         * {head shown, head y, k (inflation), shoulder x, shoulder y, arm scale, right arm x/z turn, left arm x/z turn,
         * leg y, leg scale, right leg x turn, left leg x turn, head x turn, head y turn}
         */
        @Override
        public void setupAnim(float[] p) {
            this.resetPose();
            this.body.visible = false;
            this.head.visible = p[0] > 0.5F;
            this.head.y = p[1];
            this.head.xRot = p[16];
            this.head.yRot = p[17];
            this.rightArm.x = -p[3];
            this.leftArm.x = p[3];
            this.rightArm.y = p[4];
            this.leftArm.y = p[4];
            for (ModelPart arm : new ModelPart[]{this.rightArm, this.leftArm}) {
                arm.xScale = p[5];
                arm.yScale = p[5];
                arm.zScale = p[5];
            }
            this.rightArm.xRot = p[6];
            this.rightArm.zRot = p[7];
            this.leftArm.xRot = p[8];
            this.leftArm.zRot = p[9];
            this.rightLeg.y = p[10];
            this.leftLeg.y = p[10];
            for (ModelPart leg : new ModelPart[]{this.rightLeg, this.leftLeg}) {
                leg.xScale = p[11];
                leg.yScale = p[11];
                leg.zScale = p[11];
            }
            this.rightLeg.xRot = p[12];
            this.leftLeg.xRot = p[13];
            this.rightLeg.zRot = p[14];
            this.leftLeg.zRot = p[15];
        }
    }

    private static Limbs wide;
    private static Limbs slim;

    private static Limbs limbs(boolean thin) {
        if (wide == null) {
            wide = new Limbs(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot());
            slim = new Limbs(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64).bakeRoot());
        }
        return thin ? slim : wide;
    }

    // ------------------------------------------------------------------ the wobble

    /** Up-down stretch of the round body now (1: round). */
    public static float stretch(Visuals v) {
        float s = 1.0F + 0.035F * Mth.sin(v.time * 0.21F + v.seed % 31);
        s += Mth.clamp(v.vy * 1.6F, -0.13F, 0.13F);
        if (v.hurt > 0.0F) {
            s += 0.16F * Mth.sin(v.hurt * 1.9F) * v.hurt / 10.0F;
        }
        return s;
    }

    /** How much the head goes up and down with the top of the body. */
    public static float headBob(Visuals v) {
        float k = BalloonShape.inflation(v.curseAge);
        return -(stretch(v) - 1.0F) * BalloonShape.radius(k) * k;
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the balloon in the player model's space (the CurseLayer's pose). */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Visuals v, Identifier skin, boolean thin, int light, int overlay,
                              float headXRot, float headYRot) {
        boolean popped = v.popAge >= 0.0F;
        float k = BalloonShape.inflation(v.curseAge);
        float air = popped ? BalloonShape.air(v.popAge) : 1.0F;
        float r = BalloonShape.radius(k) * Mth.lerp(air, 0.35F, 1.0F);
        float cy = BalloonShape.centerY(k);
        float sy = popped ? 1.0F + 0.25F * Mth.sin(v.popAge * 1.7F) * air : stretch(v);
        float sxz = 1.0F / Mth.sqrt(Math.max(0.2F, sy));
        if (popped) {
            // empty: a crumpled, flattened skin
            sy *= Mth.lerp(air, 0.18F, 1.0F);
            sxz *= Mth.lerp(air, 1.25F, 1.0F);
        }
        float pitch = popped ? 0.0F : Mth.clamp(-v.forward * 2.4F, -0.4F, 0.4F) + 0.05F * Mth.sin(v.time * 0.13F);
        float roll = popped ? 0.0F : Mth.clamp(v.side * 2.4F, -0.4F, 0.4F) + 0.04F * Mth.sin(v.time * 0.11F + 2.0F);

        poseStack.pushPose();
        // the round body: around its middle, leaning and squashing
        poseStack.translate(0.0F, cy / 16.0F, 0.0F);
        poseStack.rotate(new org.joml.Quaternionf().rotationXYZ(pitch, 0.0F, roll));
        poseStack.scale(sxz, sy, sxz);
        final float radius = r;
        RenderType base = RenderTypes.entityCutout(skin);
        RenderType outer = RenderTypes.entityTranslucent(skin);
        collector.submitCustomGeometry(poseStack, base, (pose, b) -> {
            sphere(pose, b, radius, 20.0F, 32.0F, light, overlay, -1);
            knot(pose, b, radius, light, overlay);
        });
        collector.submitCustomGeometry(poseStack, outer, (pose, b) -> sphere(pose, b, radius + 0.3F, 36.0F, 48.0F, light, overlay, -1));
        collector.submitCustomGeometry(poseStack, SHINE_TYPE, (pose, b) -> shine(pose, b, radius + 0.45F, light, overlay));
        poseStack.popPose();

        // the arms out of its sides, the legs under it (and the head while it whizzes round)
        float t = v.time + v.seed % 53;
        float paddle = Mth.clamp((Math.abs(v.forward) + Math.abs(v.side)) * 5.0F, 0.0F, 1.0F);
        float flail = popped ? 1.0F : 0.25F + 0.75F * paddle;
        float armOut = Mth.lerp(k, 0.0F, 1.15F);
        float[] p = new float[18];
        p[0] = popped ? 1.0F : 0.0F;
        p[1] = BalloonShape.neckY(k) + 3.0F * (1.0F - air);
        p[2] = k;
        float shrink = Mth.lerp(air, 0.4F, 1.0F);
        p[3] = BalloonShape.shoulderX(k) * shrink;
        p[4] = BalloonShape.shoulderY(k) * shrink + cy * (1.0F - shrink);
        p[5] = BalloonShape.armScale(k);
        p[6] = Mth.sin(t * 0.35F) * 0.5F * flail;
        p[7] = armOut + Mth.sin(t * 0.47F) * 0.25F * flail;
        p[8] = -Mth.sin(t * 0.35F) * 0.5F * flail;
        p[9] = -armOut - Mth.sin(t * 0.43F + 1.0F) * 0.25F * flail;
        p[10] = Mth.lerp(air, cy, BalloonShape.legY(k));
        p[11] = BalloonShape.legScale(k);
        p[12] = Mth.sin(t * 0.19F) * 0.35F * flail;
        p[13] = Mth.sin(t * 0.19F + 2.2F) * 0.35F * flail;
        p[14] = 0.06F;
        p[15] = -0.06F;
        p[16] = headXRot;
        p[17] = headYRot;
        collector.submitModel(limbs(thin), p, poseStack, RenderTypes.entityTranslucent(skin), light, overlay, EntityRenderState.NO_OUTLINE);
    }

    /** A popped balloon's empty skin, lying flat (the pose: on the ground, the model's frame: y down). */
    public static void submitEmpty(PoseStack poseStack, SubmitNodeCollector collector, Identifier skin, int light, int overlay) {
        poseStack.pushPose();
        poseStack.scale(1.35F, 0.1F, 1.2F);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(skin), (pose, b) -> {
            sphere(pose, b, 7.0F, 20.0F, 32.0F, light, overlay, 0xFFD8D0D0);
            knot(pose, b, 7.0F, light, overlay);
        });
        poseStack.popPose();
    }

    /**
     * A sphere of {@code r} pixels around the pose's origin, the skin's body strip (u 16..40, v
     * {@code v0}..{@code v1}) wrapped round it: the shirt's right side, front, left side and back,
     * in the same order as on the body.
     */
    private static void sphere(PoseStack.Pose pose, VertexConsumer b, float r, float v0, float v1, int light, int overlay, int color) {
        float s = 1.0F / 16.0F;
        double phi0 = 22.0 / 24.0 * Math.PI * 2.0;
        for (int i = 0; i < LAT; i++) {
            double th0 = Math.PI * i / LAT;
            double th1 = Math.PI * (i + 1) / LAT;
            for (int j = 0; j < LON; j++) {
                double ph0 = phi0 + Math.PI * 2.0 * j / LON;
                double ph1 = phi0 + Math.PI * 2.0 * (j + 1) / LON;
                float ua = (16.0F + 24.0F * j / LON) / 64.0F;
                float ub = (16.0F + 24.0F * (j + 1) / LON) / 64.0F;
                float va = (v0 + (v1 - v0) * i / LAT) / 64.0F;
                float vb = (v0 + (v1 - v0) * (i + 1) / LAT) / 64.0F;
                vertex(pose, b, r, s, th0, ph0, ua, va, light, overlay, color);
                vertex(pose, b, r, s, th1, ph0, ua, vb, light, overlay, color);
                vertex(pose, b, r, s, th1, ph1, ub, vb, light, overlay, color);
                vertex(pose, b, r, s, th0, ph1, ub, va, light, overlay, color);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer b, float r, float s, double th, double ph, float u, float v, int light, int overlay,
                               int color) {
        // the model's frame: y down, -x is the body's right, -z its front
        float nx = (float) (-Math.sin(th) * Math.cos(ph));
        float ny = (float) -Math.cos(th);
        float nz = (float) (-Math.sin(th) * Math.sin(ph));
        b.addVertex(pose, nx * r * s, ny * r * s, nz * r * s).setColor(color).setUv(u, v).setOverlay(overlay).setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /** The knot underneath: a little twisted nub of skin (the bottom of the shirt). */
    private static void knot(PoseStack.Pose pose, VertexConsumer b, float r, int light, int overlay) {
        float s = 1.0F / 16.0F;
        float top = r * 0.96F;
        float[][] rings = {{1.6F, top}, {0.9F, top + 1.4F}, {1.4F, top + 2.4F}, {0.0F, top + 2.9F}};
        for (int i = 0; i < rings.length - 1; i++) {
            float ra = rings[i][0];
            float ya = rings[i][1];
            float rb = rings[i + 1][0];
            float yb = rings[i + 1][1];
            for (int j = 0; j < 8; j++) {
                double a0 = Math.PI * 2.0 * j / 8;
                double a1 = Math.PI * 2.0 * (j + 1) / 8;
                float u0 = (28.0F + j) / 64.0F;
                float u1 = (29.0F + j) / 64.0F;
                float vv0 = (16.0F + i) / 64.0F;
                float vv1 = (17.0F + i) / 64.0F;
                quad(pose, b, s, ra, ya, a0, u0, vv0, rb, yb, a0, u0, vv1, rb, yb, a1, u1, vv1, ra, ya, a1, u1, vv0, light, overlay);
            }
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer b, float s, float r0, float y0, double a0, float u0, float v0, float r1, float y1,
                             double a1, float u1, float v1, float r2, float y2, double a2, float u2, float v2, float r3, float y3, double a3, float u3,
                             float v3, int light, int overlay) {
        knotVertex(pose, b, s, r0, y0, a0, u0, v0, light, overlay);
        knotVertex(pose, b, s, r1, y1, a1, u1, v1, light, overlay);
        knotVertex(pose, b, s, r2, y2, a2, u2, v2, light, overlay);
        knotVertex(pose, b, s, r3, y3, a3, u3, v3, light, overlay);
    }

    private static void knotVertex(PoseStack.Pose pose, VertexConsumer b, float s, float r, float y, double a, float u, float v, int light, int overlay) {
        float nx = (float) Math.cos(a);
        float nz = (float) Math.sin(a);
        b.addVertex(pose, nx * r * s, y * s, nz * r * s).setColor(-1).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, nx, 0.3F, nz);
    }

    /** The shine: a soft highlight up on its front, painted in balloon_shine.png (u round, v down). */
    private static void shine(PoseStack.Pose pose, VertexConsumer b, float r, int light, int overlay) {
        float s = 1.0F / 16.0F;
        double phi0 = 22.0 / 24.0 * Math.PI * 2.0;
        for (int i = 0; i < LAT; i++) {
            double th0 = Math.PI * i / LAT;
            double th1 = Math.PI * (i + 1) / LAT;
            for (int j = 0; j < LON; j++) {
                double ph0 = phi0 + Math.PI * 2.0 * j / LON;
                double ph1 = phi0 + Math.PI * 2.0 * (j + 1) / LON;
                float ua = (float) j / LON;
                float ub = (float) (j + 1) / LON;
                float va = (float) i / LAT;
                float vb = (float) (i + 1) / LAT;
                vertex(pose, b, r, s, th0, ph0, ua, va, light, overlay, -1);
                vertex(pose, b, r, s, th1, ph0, ua, vb, light, overlay, -1);
                vertex(pose, b, r, s, th1, ph1, ub, vb, light, overlay, -1);
                vertex(pose, b, r, s, th0, ph1, ub, va, light, overlay, -1);
            }
        }
    }
}
