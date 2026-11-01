package com.punizioni.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.punizioni.Punizioni;
import com.punizioni.data.Body;
import com.punizioni.data.Limb;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.PlayerModelType;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * What the punishments did to a body, drawn over the player model (whose limbs Poses hides when
 * they need to look different): stumps where a hand or a foot was (the skin's own arm and leg, cut
 * short, with raw flesh and bone at the end), arms and legs broken on the wheel and bent the wrong way,
 * sockets where the horses tore them off, the neck without a head. And the executioner's gear in 3D.
 */
public class BodyLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public static final Identifier STUMP = Punizioni.id("textures/entity/stump.png");
    private static final Identifier GEAR = Punizioni.id("textures/entity/executioner.png");
    public static final RenderType STUMP_TYPE = RenderTypes.entityCutout(STUMP);

    /** One limb's replacement pieces (skin texture), for one skin shape. */
    private record LimbParts(ModelPart stump, ModelPart stumpOver, ModelPart upper, ModelPart upperOver, ModelPart lower, ModelPart lowerOver,
                             ModelPart shortLower, ModelPart shortLowerOver, ModelPart cutCap, ModelPart brokenCutCap, ModelPart socket) {
    }

    private static final LimbParts[] WIDE = new LimbParts[4];
    private static final LimbParts[] SLIM = new LimbParts[4];
    private static final ModelPart NECK = cap(-4.0F, -1.0F, -2.0F, 8.0F, 1.0F, 4.0F);

    static {
        // right arm, left arm, right leg, left leg: texture offsets of the base and the overlay layer
        int[][] uv = {{40, 16, 40, 32}, {32, 48, 48, 48}, {0, 16, 0, 32}, {16, 48, 0, 48}};
        for (int i = 0; i < 4; i++) {
            WIDE[i] = limb(i, uv[i], 4.0F);
            SLIM[i] = i < 2 ? limb(i, uv[i], 3.0F) : WIDE[i];
        }
    }

    private final ModelPart hood, apron, bracerRight, bracerLeft, breechesRight, breechesLeft, bootRight, bootLeft;

    public BodyLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ResourceManager manager) {
        super(parent);
        BbModel gear = BbModel.load(manager, Punizioni.id("geometry/executioner.bbmodel"));
        this.hood = gearPart(gear, "hood");
        this.apron = gearPart(gear, "apron");
        this.bracerRight = gearPart(gear, "bracer_right");
        this.bracerLeft = gearPart(gear, "bracer_left");
        this.breechesRight = gearPart(gear, "breeches_right");
        this.breechesLeft = gearPart(gear, "breeches_left");
        this.bootRight = gearPart(gear, "boot_right");
        this.bootLeft = gearPart(gear, "boot_left");
    }

    private static ModelPart gearPart(BbModel bb, String name) {
        ModelPart p = bb.find(name);
        if (p != null) {
            // drawn from the vanilla part's pivot: the bone's own offset is already there
            p.setInitialPose(PartPose.ZERO);
            p.resetPose();
        }
        return p;
    }

    public static ModelPart box(int u, int v, float x, float y, float z, float w, float h, float d, float grow) {
        ModelPart.Cube c = new ModelPart.Cube(u, v, x, y, z, w, h, d, grow, grow, grow, false, 64.0F, 64.0F, EnumSet.allOf(Direction.class));
        return new ModelPart(List.of(c), Map.of());
    }

    /** Raw flesh and bone (stump.png, 16x16): a thin slab. */
    public static ModelPart cap(float x, float y, float z, float w, float h, float d) {
        ModelPart.Cube c = new ModelPart.Cube(0, 0, x, y, z, w, h, d, 0.02F, 0.02F, 0.02F, false, 16.0F, 16.0F, EnumSet.allOf(Direction.class));
        return new ModelPart(List.of(c), Map.of());
    }

    private static ModelPart pivoted(ModelPart child, float y, float xRot, float zRot) {
        ModelPart p = new ModelPart(List.of(), Map.of("child", child));
        p.setInitialPose(PartPose.offsetAndRotation(0.0F, y, 0.0F, xRot, 0.0F, zRot));
        p.resetPose();
        return p;
    }

    /**
     * The pieces of one limb. Arms start 2 pixels above their pivot, legs at it; the hand and the
     * foot are the last 4 pixels; the elbow and the knee are in the middle.
     */
    private static LimbParts limb(int i, int[] uv, float width) {
        boolean arm = i < 2;
        boolean right = i % 2 == 0;
        float x = arm ? (right ? -(width - 1.0F) : -1.0F) : -2.0F;
        float top = arm ? -2.0F : 0.0F;
        float joint = top + 6.0F;
        // broken: the forearm swings backwards and out, the shin forwards and in
        float kinkX = arm ? 1.15F : -1.05F;
        float kinkZ = (right ? 1.0F : -1.0F) * (arm ? 0.45F : -0.3F);
        ModelPart lower = pivoted(box(uv[0], uv[1] + 6, x, 0.0F, -2.0F, width, 6.0F, 4.0F, 0.0F), joint, kinkX, kinkZ);
        ModelPart lowerOver = pivoted(box(uv[2], uv[3] + 6, x, 0.0F, -2.0F, width, 6.0F, 4.0F, 0.25F), joint, kinkX, kinkZ);
        ModelPart shortLower = pivoted(box(uv[0], uv[1] + 6, x, 0.0F, -2.0F, width, 2.0F, 4.0F, 0.0F), joint, kinkX, kinkZ);
        ModelPart shortLowerOver = pivoted(box(uv[2], uv[3] + 6, x, 0.0F, -2.0F, width, 2.0F, 4.0F, 0.25F), joint, kinkX, kinkZ);
        ModelPart brokenCutCap = pivoted(cap(x, 2.0F, -2.0F, width, 1.0F, 4.0F), joint, kinkX, kinkZ);
        return new LimbParts(
                box(uv[0], uv[1], x, top, -2.0F, width, 8.0F, 4.0F, 0.0F),
                box(uv[2], uv[3], x, top, -2.0F, width, 8.0F, 4.0F, 0.25F),
                box(uv[0], uv[1], x, top, -2.0F, width, 6.0F, 4.0F, 0.0F),
                box(uv[2], uv[3], x, top, -2.0F, width, 6.0F, 4.0F, 0.25F),
                lower, lowerOver, shortLower, shortLowerOver,
                cap(x, top + 8.0F, -2.0F, width, 1.0F, 4.0F),
                brokenCutCap,
                cap(x, top, -2.0F, width, 1.0F, 4.0F));
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Visuals v = state.getData(Visuals.KEY);
        if (v == null || state.isInvisible) {
            return;
        }
        PlayerModel model = this.getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        Identifier skin = state.skin.body().texturePath();
        Body b = v.body;
        boolean slim = state.skin.model() == PlayerModelType.SLIM;
        ModelPart[] limbs = {model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        for (int i = 0; i < 4; i++) {
            if (!changed(b, i)) {
                continue;
            }
            poseStack.pushPose();
            model.root().translateAndRotate(poseStack);
            limbs[i].translateAndRotate(poseStack);
            submitLimb(poseStack, collector, light, overlay, skin, i, slim, b);
            poseStack.popPose();
        }
        if (b.headless()) {
            poseStack.pushPose();
            model.root().translateAndRotate(poseStack);
            model.body.translateAndRotate(poseStack);
            collector.submitModelPart(NECK, poseStack, STUMP_TYPE, light, overlay, null);
            poseStack.popPose();
        }
        this.gear(poseStack, collector, light, overlay, v, model);
    }

    private static final Limb[] ENDS = {Limb.RIGHT_HAND, Limb.LEFT_HAND, Limb.RIGHT_FOOT, Limb.LEFT_FOOT};
    private static final int[] BONES = {Body.RIGHT_ARM, Body.LEFT_ARM, Body.RIGHT_LEG, Body.LEFT_LEG};

    /** Whether limb i (right arm, left arm, right leg, left leg) is drawn here instead of by vanilla. */
    public static boolean changed(Body b, int i) {
        return b.lacks(ENDS[i]) || b.brokenBone(BONES[i]) || b.torn();
    }

    /**
     * Limb i as the punishments left it, in the frame of the vanilla limb (its pivot): a stump, a
     * bone broken the wrong way (and maybe cut short too), or the raw socket of a torn limb.
     */
    public static void submitLimb(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, Identifier skinTexture, int i,
                                  boolean slimArms, Body b) {
        LimbParts p = (slimArms ? SLIM : WIDE)[i];
        RenderType skin = RenderTypes.entityTranslucent(skinTexture);
        boolean cut = b.lacks(ENDS[i]);
        boolean broken = b.brokenBone(BONES[i]);
        if (b.torn()) {
            collector.submitModelPart(p.socket, poseStack, STUMP_TYPE, light, overlay, null);
        } else if (broken) {
            collector.submitModelPart(p.upper, poseStack, skin, light, overlay, null);
            collector.submitModelPart(p.upperOver, poseStack, skin, light, overlay, null);
            if (cut) {
                collector.submitModelPart(p.shortLower, poseStack, skin, light, overlay, null);
                collector.submitModelPart(p.shortLowerOver, poseStack, skin, light, overlay, null);
                collector.submitModelPart(p.brokenCutCap, poseStack, STUMP_TYPE, light, overlay, null);
            } else {
                collector.submitModelPart(p.lower, poseStack, skin, light, overlay, null);
                collector.submitModelPart(p.lowerOver, poseStack, skin, light, overlay, null);
            }
        } else if (cut) {
            collector.submitModelPart(p.stump, poseStack, skin, light, overlay, null);
            collector.submitModelPart(p.stumpOver, poseStack, skin, light, overlay, null);
            collector.submitModelPart(p.cutCap, poseStack, STUMP_TYPE, light, overlay, null);
        }
    }

    private void gear(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, Visuals v, PlayerModel model) {
        RenderType type = RenderTypes.entityCutout(GEAR);
        Body b = v.body;
        if (v.hood && !b.headless()) {
            this.on(poseStack, collector, model, model.head, this.hood, type, light, overlay);
        }
        if (v.apron) {
            this.on(poseStack, collector, model, model.body, this.apron, type, light, overlay);
            if (!b.lacks(Limb.RIGHT_HAND) && !b.torn()) {
                this.on(poseStack, collector, model, model.rightArm, this.bracerRight, type, light, overlay);
            }
            if (!b.lacks(Limb.LEFT_HAND) && !b.torn()) {
                this.on(poseStack, collector, model, model.leftArm, this.bracerLeft, type, light, overlay);
            }
        }
        if (v.breeches && !b.torn()) {
            this.on(poseStack, collector, model, model.rightLeg, this.breechesRight, type, light, overlay);
            this.on(poseStack, collector, model, model.leftLeg, this.breechesLeft, type, light, overlay);
        }
        if (v.boots && !b.torn()) {
            if (!b.lacks(Limb.RIGHT_FOOT) && !b.brokenBone(Body.RIGHT_LEG)) {
                this.on(poseStack, collector, model, model.rightLeg, this.bootRight, type, light, overlay);
            }
            if (!b.lacks(Limb.LEFT_FOOT) && !b.brokenBone(Body.LEFT_LEG)) {
                this.on(poseStack, collector, model, model.leftLeg, this.bootLeft, type, light, overlay);
            }
        }
    }

    private void on(PoseStack poseStack, SubmitNodeCollector collector, PlayerModel model, ModelPart attach, ModelPart piece, RenderType type, int light,
                    int overlay) {
        if (piece == null) {
            return;
        }
        poseStack.pushPose();
        model.root().translateAndRotate(poseStack);
        attach.translateAndRotate(poseStack);
        collector.submitModelPart(piece, poseStack, type, light, overlay, null);
        poseStack.popPose();
    }
}
