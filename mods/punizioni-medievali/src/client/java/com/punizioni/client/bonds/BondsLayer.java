package com.punizioni.client.bonds;

import com.mojang.blaze3d.vertex.PoseStack;
import com.punizioni.Punizioni;
import com.punizioni.client.render.BbModel;
import com.punizioni.client.render.Visuals;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The prisoner's rope on the body, tied like a salami: rings of rope from the shoulders to the
 * ankles, arms pressed to the sides (Poses), strands down the front and the back, and a knot on the
 * belly where the lead starts. Also the exact ends of the lead where the models are drawn: that knot
 * and the captor's hand (Captives draws the rope between them).
 */
public class BondsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public static final Identifier TEXTURE = Punizioni.id("textures/entity/bonds.png");
    private static final RenderType TYPE = RenderTypes.entityCutout(TEXTURE);

    private final AvatarRenderer<?> renderer;
    private final ModelPart wrapBody;
    private final ModelPart wrapLegs;

    public BondsLayer(AvatarRenderer<?> renderer, ResourceManager manager) {
        super(renderer);
        this.renderer = renderer;
        BbModel model = BbModel.load(manager, Punizioni.id("geometry/bonds.bbmodel"));
        this.wrapBody = part(model, "wrap_body");
        this.wrapLegs = part(model, "wrap_legs");
        ModelPart k = part(model, "knot");
        if (k != null) {
            Captives.knot = k;
        }
    }

    private static ModelPart part(BbModel bb, String name) {
        ModelPart p = bb.find(name);
        if (p != null) {
            p.setInitialPose(PartPose.ZERO);
            p.resetPose();
        }
        return p;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Visuals v = state.getData(Visuals.KEY);
        if (v == null || state.isInvisible || (!v.bound && !v.captor)) {
            return;
        }
        PlayerModel model = this.getParentModel();
        boolean world = !v.gui && v.hasEntityPose;
        Matrix4f toEntity = world ? new Matrix4f(v.entityPose).invert() : null;
        Vec3 origin = world ? new Vec3(state.x, state.y, state.z).add(this.renderer.getRenderOffset(state)) : Vec3.ZERO;
        if (v.bound && v.kind == null) {
            int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
            if (this.wrapLegs != null && !v.body.torn()) {
                poseStack.pushPose();
                model.root().translateAndRotate(poseStack);
                model.rightLeg.translateAndRotate(poseStack);
                collector.submitModelPart(this.wrapLegs, poseStack, TYPE, light, overlay, null);
                poseStack.popPose();
            }
            poseStack.pushPose();
            model.root().translateAndRotate(poseStack);
            model.body.translateAndRotate(poseStack);
            if (this.wrapBody != null) {
                collector.submitModelPart(this.wrapBody, poseStack, TYPE, light, overlay, null);
            }
            if (world) {
                // the knot on the belly, where the lead starts
                Vector3f k = new Matrix4f(toEntity).mul(poseStack.last().pose()).transformPosition(new Vector3f(0.0F, 6.2F / 16.0F, -3.0F / 16.0F));
                Captives.wrists(v.entityId, origin.add(k.x, k.y, k.z));
            }
            poseStack.popPose();
        }
        if (v.captor && world) {
            HumanoidArm arm = state.mainArm;
            poseStack.pushPose();
            model.translateToHand(state, arm, poseStack);
            Vector3f h = new Matrix4f(toEntity).mul(poseStack.last().pose())
                    .transformPosition(new Vector3f((arm == HumanoidArm.RIGHT ? -1.0F : 1.0F) / 16.0F, 10.0F / 16.0F, -1.0F / 16.0F));
            poseStack.popPose();
            Captives.hand(v.entityId, origin.add(h.x, h.y, h.z));
        }
    }
}
