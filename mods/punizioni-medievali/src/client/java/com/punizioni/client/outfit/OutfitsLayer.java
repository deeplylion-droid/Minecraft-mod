package com.punizioni.client.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.punizioni.Punizioni;
import com.punizioni.client.render.BbModel;
import com.punizioni.client.render.Visuals;
import com.punizioni.data.Body;
import com.punizioni.data.Limb;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The jester's motley and the prisoner's rags in 3D, on the parts of the player model: the
 * jester's three-pointed hat (the points flop and swing, a bell at each tip), the dagged collar
 * with bells, the quartered tunic with its dagged hem, the sleeves, the parti-coloured hose and the
 * curled shoes with bells; the prisoner's burlap hood with its long tail, the sackcloth tunic with
 * a rope belt and hanging rags, the torn sleeves with rusty manacles and their chains, the torn
 * breeches, the foot rags with the ankle irons. The swinging comes from Sway.
 *
 * <p>Drawing is deferred and the models are shared by every player: each piece is a little Model
 * whose swinging bones get their turn from the state submitted with it (Piece.setupAnim).
 */
public class OutfitsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier JESTER_TEXTURE = Punizioni.id("textures/entity/jester.png");
    private static final Identifier PEASANT_TEXTURE = Punizioni.id("textures/entity/peasant.png");
    private static final RenderType JESTER = RenderTypes.entityCutout(JESTER_TEXTURE);
    private static final RenderType PEASANT = RenderTypes.entityCutout(PEASANT_TEXTURE);

    private static final String[] POINTS = {"left", "right", "back"};
    private static final float[] NONE = new float[0];

    /**
     * One piece of costume as a model of its own. Its swinging bones are turned (radians, x y z for
     * each bone in order) by the state it is drawn with.
     */
    static final class Piece extends Model<float[]> {
        private final @Nullable ModelPart[] bones;

        Piece(ModelPart root, @Nullable ModelPart... bones) {
            super(root, RenderTypes::entityCutout);
            this.bones = bones;
        }

        @Override
        public void setupAnim(float[] turns) {
            this.resetPose();
            for (int i = 0; i < this.bones.length && 3 * i + 2 < turns.length; i++) {
                ModelPart b = this.bones[i];
                if (b != null) {
                    b.xRot += turns[3 * i];
                    b.yRot += turns[3 * i + 1];
                    b.zRot += turns[3 * i + 2];
                }
            }
        }
    }

    private final @Nullable Piece hat, collar, tunic, jSleeveRight, jSleeveLeft, hoseRight, hoseLeft, shoeRight, shoeLeft;
    private final @Nullable Piece hood, pTunic, pSleeveRight, pSleeveLeft, breechesRight, breechesLeft, wrapRight, wrapLeft;

    public OutfitsLayer(AvatarRenderer<?> renderer, ResourceManager manager) {
        super(renderer);
        BbModel j = BbModel.load(manager, Punizioni.id("geometry/jester.bbmodel"));
        ModelPart[] points = new ModelPart[9];
        for (int p = 0; p < 3; p++) {
            for (int i = 0; i < 3; i++) {
                points[p * 3 + i] = j.find("hat_" + POINTS[p] + "_" + (i + 1));
            }
        }
        this.hat = piece(j, "jester_hat", points);
        this.collar = piece(j, "jester_collar");
        this.tunic = piece(j, "jester_tunic", j.find("hem_front"), j.find("hem_back"));
        this.jSleeveRight = piece(j, "jester_sleeve_right");
        this.jSleeveLeft = piece(j, "jester_sleeve_left");
        this.hoseRight = piece(j, "jester_hose_right");
        this.hoseLeft = piece(j, "jester_hose_left");
        this.shoeRight = piece(j, "jester_shoe_right", j.find("shoe_tip_right"));
        this.shoeLeft = piece(j, "jester_shoe_left", j.find("shoe_tip_left"));

        BbModel p = BbModel.load(manager, Punizioni.id("geometry/peasant.bbmodel"));
        this.hood = piece(p, "peasant_hood", p.find("hood_tail"));
        this.pTunic = piece(p, "peasant_tunic", p.find("rag_front"), p.find("rag_back"), p.find("rag_side"), p.find("belt_end"));
        this.pSleeveRight = piece(p, "peasant_sleeve_right", p.find("chain_right"));
        this.pSleeveLeft = piece(p, "peasant_sleeve_left", p.find("chain_left"));
        this.breechesRight = piece(p, "peasant_breeches_right");
        this.breechesLeft = piece(p, "peasant_breeches_left");
        this.wrapRight = piece(p, "peasant_wrap_right", p.find("ankle_chain_right"));
        this.wrapLeft = piece(p, "peasant_wrap_left", p.find("ankle_chain_left"));
    }

    /** A piece drawn from the vanilla part's pivot (the bone's own offset is already there). */
    private static @Nullable Piece piece(BbModel bb, String name, @Nullable ModelPart... bones) {
        ModelPart p = bb.find(name);
        if (p == null) {
            return null;
        }
        p.setInitialPose(PartPose.ZERO);
        p.resetPose();
        return new Piece(p, bones);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Visuals v = state.getData(Visuals.KEY);
        if (v == null || state.isInvisible || (v.jester == 0 && v.peasant == 0)) {
            return;
        }
        PlayerModel model = this.getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        Sway sway = null;
        if (!v.gui) {
            sway = Sway.of(v.entityId);
            sway.update(state.ageInTicks, state.x, state.y, state.z, state.bodyRot);
        }
        float pitch = sway != null ? sway.pitch : 0.0F;
        float roll = sway != null ? sway.roll : 0.0F;
        float lift = sway != null ? sway.lift : 0.0F;
        float walk = state.walkAnimationSpeed;
        float pos = state.walkAnimationPos;
        float t = state.ageInTicks;
        // every step makes the loose bits bounce
        float bounce = walk * Mth.cos(pos * 1.3324F) * 0.22F + Mth.sin(t * 0.07F + v.seed) * 0.03F;
        Body b = v.body;
        boolean noRightArm = b.lacks(Limb.RIGHT_HAND) || b.brokenBone(Body.RIGHT_ARM) || b.torn();
        boolean noLeftArm = b.lacks(Limb.LEFT_HAND) || b.brokenBone(Body.LEFT_ARM) || b.torn();
        boolean noRightLeg = b.lacks(Limb.RIGHT_FOOT) || b.brokenBone(Body.RIGHT_LEG) || b.torn();
        boolean noLeftLeg = b.lacks(Limb.LEFT_FOOT) || b.brokenBone(Body.LEFT_LEG) || b.torn();
        float legFront = Math.min(0.0F, Math.min(model.rightLeg.xRot, model.leftLeg.xRot));
        float legBack = Math.max(0.0F, Math.max(model.rightLeg.xRot, model.leftLeg.xRot));

        if (v.jester != 0) {
            if ((v.jester & 1) != 0 && !b.headless()) {
                this.on(poseStack, collector, model, model.head, this.hat, points(pitch, roll, lift + bounce, model.head.xRot), JESTER, light, overlay);
            }
            if ((v.jester & 2) != 0) {
                float[] hem = {hang(legFront * 0.75F + pitch * 0.25F), 0.0F, 0.0F, hang(legBack * 0.75F + pitch * 0.25F), 0.0F, 0.0F};
                this.on(poseStack, collector, model, model.body, this.tunic, hem, JESTER, light, overlay);
                this.on(poseStack, collector, model, model.body, this.collar, NONE, JESTER, light, overlay);
                if (!noRightArm) {
                    this.on(poseStack, collector, model, model.rightArm, this.jSleeveRight, NONE, JESTER, light, overlay);
                }
                if (!noLeftArm) {
                    this.on(poseStack, collector, model, model.leftArm, this.jSleeveLeft, NONE, JESTER, light, overlay);
                }
            }
            if ((v.jester & 4) != 0) {
                if (!noRightLeg) {
                    this.on(poseStack, collector, model, model.rightLeg, this.hoseRight, NONE, JESTER, light, overlay);
                }
                if (!noLeftLeg) {
                    this.on(poseStack, collector, model, model.leftLeg, this.hoseLeft, NONE, JESTER, light, overlay);
                }
            }
            if ((v.jester & 8) != 0) {
                // the bells on the curled toes bob at every step
                float bob = walk * Mth.sin(pos * 1.3324F) * 0.35F + lift * 0.2F;
                if (!noRightLeg) {
                    this.on(poseStack, collector, model, model.rightLeg, this.shoeRight, new float[]{hang(bob), 0.0F, 0.0F}, JESTER, light, overlay);
                }
                if (!noLeftLeg) {
                    this.on(poseStack, collector, model, model.leftLeg, this.shoeLeft, new float[]{hang(-bob), 0.0F, 0.0F}, JESTER, light, overlay);
                }
            }
        }

        if (v.peasant != 0) {
            if ((v.peasant & 1) != 0 && !b.headless()) {
                // the long tail of the hood hangs down the back whatever the head does
                float[] tail = {hang(-model.head.xRot * 0.85F + pitch * 0.5F + (lift + bounce) * 0.3F), 0.0F, side(roll * 0.5F)};
                this.on(poseStack, collector, model, model.head, this.hood, tail, PEASANT, light, overlay);
            }
            if ((v.peasant & 2) != 0) {
                float[] rags = {
                        hang(legFront * 0.7F + pitch * 0.35F + bounce * 0.2F), 0.0F, side(roll * 0.25F),
                        hang(legBack * 0.7F + pitch * 0.35F - bounce * 0.2F), 0.0F, side(roll * 0.25F),
                        hang(pitch * 0.3F), 0.0F, side(roll * 0.4F + bounce * 0.15F),
                        hang(legFront * 0.5F + pitch * 0.4F + bounce * 0.3F), 0.0F, side(roll * 0.3F)};
                this.on(poseStack, collector, model, model.body, this.pTunic, rags, PEASANT, light, overlay);
                // the chains of the manacles hang down from the wrists, whatever the arms do
                if (!noRightArm) {
                    float[] chain = {hang(-model.rightArm.xRot * 0.75F + pitch * 0.4F + bounce * 0.3F), 0.0F, side(-model.rightArm.zRot * 0.75F + roll * 0.4F)};
                    this.on(poseStack, collector, model, model.rightArm, this.pSleeveRight, chain, PEASANT, light, overlay);
                }
                if (!noLeftArm) {
                    float[] chain = {hang(-model.leftArm.xRot * 0.75F + pitch * 0.4F + bounce * 0.3F), 0.0F, side(-model.leftArm.zRot * 0.75F + roll * 0.4F)};
                    this.on(poseStack, collector, model, model.leftArm, this.pSleeveLeft, chain, PEASANT, light, overlay);
                }
            }
            if ((v.peasant & 4) != 0) {
                if (!noRightLeg) {
                    this.on(poseStack, collector, model, model.rightLeg, this.breechesRight, NONE, PEASANT, light, overlay);
                }
                if (!noLeftLeg) {
                    this.on(poseStack, collector, model, model.leftLeg, this.breechesLeft, NONE, PEASANT, light, overlay);
                }
            }
            if ((v.peasant & 8) != 0) {
                if (!noRightLeg) {
                    float[] chain = {hang(-model.rightLeg.xRot * 0.8F + pitch * 0.3F + bounce * 0.4F), 0.0F, side(roll * 0.3F)};
                    this.on(poseStack, collector, model, model.rightLeg, this.wrapRight, chain, PEASANT, light, overlay);
                }
                if (!noLeftLeg) {
                    float[] chain = {hang(-model.leftLeg.xRot * 0.8F + pitch * 0.3F - bounce * 0.4F), 0.0F, side(roll * 0.3F)};
                    this.on(poseStack, collector, model, model.leftLeg, this.wrapLeft, chain, PEASANT, light, overlay);
                }
            }
        }
    }

    /**
     * The hat's three points: each bone of a point turns a little more than the one before, so the
     * tip swings the most. Left and right flop down and out, the back one up and down; all of them
     * trail behind the motion; the head's nod is partly taken back by gravity.
     */
    private static float[] points(float pitch, float roll, float lift, float headPitch) {
        float[] weight = {0.3F, 0.55F, 0.8F};
        float[] out = new float[27];
        for (int i = 0; i < 3; i++) {
            float k = weight[i];
            // left: out to the body's left, drooping
            out[i * 3] = -(pitch + headPitch * 0.5F) * k;
            out[i * 3 + 2] = -(lift + roll) * k;
            // right
            out[9 + i * 3] = -(pitch + headPitch * 0.5F) * k;
            out[9 + i * 3 + 2] = (lift - roll) * k;
            // back: drooping down the back of the head
            out[18 + i * 3] = (lift + pitch * 0.6F - headPitch * 0.4F) * k;
            out[18 + i * 3 + 2] = roll * k;
        }
        return out;
    }

    private static float hang(float x) {
        return Mth.clamp(x, -1.3F, 1.3F);
    }

    private static float side(float z) {
        return Mth.clamp(z, -1.0F, 1.0F);
    }

    private void on(PoseStack poseStack, SubmitNodeCollector collector, PlayerModel model, ModelPart attach, @Nullable Piece piece, float[] turns,
                    RenderType type, int light, int overlay) {
        if (piece == null) {
            return;
        }
        poseStack.pushPose();
        model.root().translateAndRotate(poseStack);
        attach.translateAndRotate(poseStack);
        collector.submitModel(piece, turns, poseStack, type, light, overlay, EntityRenderState.NO_OUTLINE);
        poseStack.popPose();
    }
}
