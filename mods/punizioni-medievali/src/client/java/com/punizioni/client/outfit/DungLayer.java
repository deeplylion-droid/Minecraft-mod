package com.punizioni.client.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.punizioni.Punizioni;
import com.punizioni.client.render.Visuals;
import com.punizioni.data.Body;
import com.punizioni.data.Filth;
import com.punizioni.data.Limb;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelType;

import java.util.List;
import java.util.Random;

/**
 * The dung itself, in 3D, where it hit: a lump stuck on that spot of the body, crumbs round it,
 * over the clothes and the armour (the stain under it is painted on the skin by MarkedSkins, and
 * the clothes would hide it). Wet and shiny at first, then dull. Washed off with the stains.
 */
public class DungLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier TEXTURE = Punizioni.id("textures/entity/dung.png");
    private static final RenderType TYPE = RenderTypes.entityCutout(TEXTURE);

    /** The vanilla boxes of the parts (wide arms; slim ones are a pixel thinner). */
    private static final float[][] BOXES = {
            {-4, -8, -4, 4, 0, 4}, {-4, 0, -2, 4, 12, 2}, {-3, -2, -2, 1, 10, 2}, {-1, -2, -2, 3, 10, 2}, {-2, 0, -2, 2, 12, 2}, {-2, 0, -2, 2, 12, 2}};

    public DungLayer(AvatarRenderer<?> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Visuals v = state.getData(Visuals.KEY);
        if (v == null || state.isInvisible || v.filth.clean()) {
            return;
        }
        PlayerModel model = this.getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        boolean slim = state.skin.model() == PlayerModelType.SLIM;
        List<Integer> splats = v.filth.splats();
        float wet = Mth.clamp(1.0F - v.sinceSplat / 600.0F, 0.0F, 1.0F);
        for (int part = 0; part <= Filth.LEFT_LEG; part++) {
            ModelPart mp = part(model, part);
            if (mp == null || !mp.visible || mp.skipDraw || hidden(v.body, part)) {
                continue;
            }
            float[] box = BOXES[part].clone();
            if (slim && part == Filth.RIGHT_ARM) {
                box[0] += 1.0F;
            } else if (slim && part == Filth.LEFT_ARM) {
                box[3] -= 1.0F;
            }
            boolean covered = covered(state, part);
            float[][] lumps = new float[splats.size()][];
            int n = 0;
            for (int i = 0; i < splats.size(); i++) {
                int s = splats.get(i);
                if (Filth.part(s) == part) {
                    lumps[n++] = lump(box, s, covered, i == splats.size() - 1 ? wet : 0.0F);
                }
            }
            if (n == 0) {
                continue;
            }
            final float[][] todo = java.util.Arrays.copyOf(lumps, n);
            poseStack.pushPose();
            model.root().translateAndRotate(poseStack);
            mp.translateAndRotate(poseStack);
            poseStack.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
            collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> {
                for (float[] l : todo) {
                    draw(pose, buffer, l, light, overlay);
                }
            });
            poseStack.popPose();
        }
    }

    private static ModelPart part(PlayerModel m, int part) {
        return switch (part) {
            case Filth.HEAD -> m.head;
            case Filth.BODY -> m.body;
            case Filth.RIGHT_ARM -> m.rightArm;
            case Filth.LEFT_ARM -> m.leftArm;
            case Filth.RIGHT_LEG -> m.rightLeg;
            default -> m.leftLeg;
        };
    }

    private static boolean hidden(Body b, int part) {
        return switch (part) {
            case Filth.HEAD -> b.headless();
            case Filth.RIGHT_ARM -> b.lacks(Limb.RIGHT_HAND) || b.torn();
            case Filth.LEFT_ARM -> b.lacks(Limb.LEFT_HAND) || b.torn();
            case Filth.RIGHT_LEG -> b.lacks(Limb.RIGHT_FOOT) || b.torn();
            case Filth.LEFT_LEG -> b.lacks(Limb.LEFT_FOOT) || b.torn();
            default -> false;
        };
    }

    /** Whether clothes or armour are over that part (the lump sits on them, not on the skin). */
    private static boolean covered(AvatarRenderState state, int part) {
        return switch (part) {
            case Filth.HEAD -> !state.headEquipment.isEmpty();
            case Filth.BODY, Filth.RIGHT_ARM, Filth.LEFT_ARM -> !state.chestEquipment.isEmpty();
            default -> !state.legsEquipment.isEmpty() || !state.feetEquipment.isEmpty();
        };
    }

    /**
     * Where the lump of this splat goes, in the part's pixels: its middle on the face, the
     * outward direction, its size, the crumbs. {x, y, z, axis, sign, half size, height, seed, wet}
     */
    private static float[] lump(float[] b, int s, boolean covered, float wet) {
        int face = Filth.face(s);
        float u = Filth.u(s);
        float v = Filth.v(s);
        float w = b[3] - b[0];
        float h = b[4] - b[1];
        float d = b[5] - b[2];
        // over the skin's outer layer, or over the clothes; the face is never covered by the mod's hats
        float off = covered && !(face == Filth.FRONT && b[1] < -7.0F) ? 1.2F : 0.55F;
        float half = 0.55F + Filth.size(s) * 0.22F;
        // keep the lump on its face
        float mu = Mth.clamp(u, half / Math.max(1.0F, face == Filth.RIGHT || face == Filth.LEFT ? d : w), 1.0F - half / Math.max(1.0F,
                face == Filth.RIGHT || face == Filth.LEFT ? d : w));
        float mv = Mth.clamp(v, half / Math.max(1.0F, face == Filth.TOP || face == Filth.BOTTOM ? d : h), 1.0F - half / Math.max(1.0F,
                face == Filth.TOP || face == Filth.BOTTOM ? d : h));
        float x;
        float y;
        float z;
        int axis;
        float sign;
        switch (face) {
            case Filth.FRONT -> {
                x = b[0] + mu * w;
                y = b[1] + mv * h;
                z = b[2] - off;
                axis = 2;
                sign = -1.0F;
            }
            case Filth.BACK -> {
                x = b[3] - mu * w;
                y = b[1] + mv * h;
                z = b[5] + off;
                axis = 2;
                sign = 1.0F;
            }
            case Filth.RIGHT -> {
                z = b[5] - mu * d;
                y = b[1] + mv * h;
                x = b[0] - off;
                axis = 0;
                sign = -1.0F;
            }
            case Filth.LEFT -> {
                z = b[2] + mu * d;
                y = b[1] + mv * h;
                x = b[3] + off;
                axis = 0;
                sign = 1.0F;
            }
            case Filth.TOP -> {
                x = b[0] + mu * w;
                z = b[5] - mv * d;
                y = b[1] - off;
                axis = 1;
                sign = -1.0F;
            }
            default -> {
                x = b[0] + mu * w;
                z = b[2] + mv * d;
                y = b[4] + off;
                axis = 1;
                sign = 1.0F;
            }
        }
        return new float[]{x, y, z, axis, sign, half, 0.45F + Filth.size(s) * 0.12F, Filth.seed(s), wet};
    }

    /** A lump: a low box on the face, a smaller one on top, crumbs round it. */
    private static void draw(PoseStack.Pose pose, VertexConsumer buffer, float[] l, int light, int overlay) {
        int axis = (int) l[3];
        float sign = l[4];
        float half = l[5];
        float height = l[6];
        Random r = new Random((long) l[7] * 7919L + 13L);
        float wet = l[8];
        // the two directions along the face
        int a1 = axis == 0 ? 2 : 0;
        int a2 = axis == 1 ? 2 : 1;
        int tint = shade(0.85F + 0.15F * r.nextFloat() + 0.2F * wet);
        box(pose, buffer, l, axis, sign, a1, a2, 0.0F, 0.0F, half, half * (0.75F + 0.4F * r.nextFloat()), 0.0F, height, r, light, overlay, tint);
        box(pose, buffer, l, axis, sign, a1, a2, (r.nextFloat() - 0.5F) * half * 0.5F, (r.nextFloat() - 0.5F) * half * 0.5F, half * 0.55F,
                half * 0.5F, height, height * 1.8F, r, light, overlay, shade(0.95F + 0.25F * wet));
        int crumbs = 2 + r.nextInt(3);
        for (int i = 0; i < crumbs; i++) {
            float ang = r.nextFloat() * Mth.TWO_PI;
            float dist = half * (1.2F + r.nextFloat() * 0.7F);
            float s = 0.16F + r.nextFloat() * 0.14F;
            box(pose, buffer, l, axis, sign, a1, a2, Mth.cos(ang) * dist, Mth.sin(ang) * dist, s, s, 0.0F, s * 1.4F, r, light, overlay,
                    shade(0.8F + 0.2F * r.nextFloat()));
        }
    }

    private static int shade(float k) {
        int c = Mth.clamp((int) (k * 255.0F), 0, 255);
        return 0xFF000000 | c << 16 | c << 8 | c;
    }

    /** A box with its base on the face: (o1, o2) along it, half sizes h1 h2, from height z0 to z1 outward. */
    private static void box(PoseStack.Pose pose, VertexConsumer buffer, float[] l, int axis, float sign, int a1, int a2, float o1, float o2, float h1,
                            float h2, float z0, float z1, Random r, int light, int overlay, int tint) {
        float[] lo = new float[3];
        float[] hi = new float[3];
        lo[a1] = l[a1] + o1 - h1;
        hi[a1] = l[a1] + o1 + h1;
        lo[a2] = l[a2] + o2 - h2;
        hi[a2] = l[a2] + o2 + h2;
        float base = l[axis];
        if (sign > 0) {
            lo[axis] = base + z0;
            hi[axis] = base + z1;
        } else {
            lo[axis] = base - z1;
            hi[axis] = base - z0;
        }
        float u0 = r.nextInt(12) / 16.0F;
        float v0 = r.nextInt(12) / 16.0F;
        float du = 0.25F;
        cuboid(pose, buffer, lo, hi, u0, v0, du, light, overlay, tint);
    }

    private static void cuboid(PoseStack.Pose pose, VertexConsumer b, float[] lo, float[] hi, float u0, float v0, float du, int light, int overlay,
                               int tint) {
        float x0 = lo[0], y0 = lo[1], z0 = lo[2], x1 = hi[0], y1 = hi[1], z1 = hi[2];
        float u1 = u0 + du;
        float v1 = v0 + du;
        // in the model's space y points down: "up" faces are at y0
        quad(pose, b, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, u0, v0, u1, v1, light, overlay, tint);
        quad(pose, b, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, u0, v0, u1, v1, light, overlay, tint);
        quad(pose, b, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, u0, v0, u1, v1, light, overlay, tint);
        quad(pose, b, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, u0, v0, u1, v1, light, overlay, tint);
        quad(pose, b, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, u0, v0, u1, v1, light, overlay, tint);
        quad(pose, b, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, u0, v0, u1, v1, light, overlay, tint);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer b, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float nx, float ny, float nz, float u0, float v0, float u1, float v1, int light, int overlay,
                             int tint) {
        b.addVertex(pose, ax, ay, az).setColor(tint).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        b.addVertex(pose, bx, by, bz).setColor(tint).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        b.addVertex(pose, cx, cy, cz).setColor(tint).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        b.addVertex(pose, dx, dy, dz).setColor(tint).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
