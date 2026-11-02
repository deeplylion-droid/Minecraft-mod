package com.punizioni.client.royal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.punizioni.ModParticles;
import com.punizioni.Punizioni;
import com.punizioni.client.render.BbModel;
import com.punizioni.client.render.Visuals;
import com.punizioni.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
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

import java.util.HashMap;
import java.util.Map;

/**
 * The king's regalia on a player: the crown on the head (gold, pearls, velvet, gems that glow and
 * twinkle), the ermine collar and the mantle (cloth, see CapeCloth), the overseer's lash from the
 * whip's handle. In the inventory's little window everything stands still.
 */
public class RoyalLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier TEXTURE = Punizioni.id("textures/entity/royal.png");
    private static final Identifier GLOW = Punizioni.id("textures/entity/royal_glow.png");
    private static final RenderType SOLID = RenderTypes.entityCutout(TEXTURE);
    private static final RenderType GLOWING = RenderTypes.eyes(GLOW);

    /** The crown's gems, in the head's space (pixels): where the twinkles appear. */
    private static final float[][] GEMS = {{0.0F, -10.0F, -5.1F}, {-3.0F, -10.0F, -5.1F}, {3.0F, -10.0F, -5.1F}, {5.1F, -10.0F, 0.0F},
            {-5.1F, -10.0F, 0.0F}, {0.0F, -10.0F, 5.1F}, {0.0F, -18.3F, 0.0F}, {0.0F, -13.2F, -4.4F}, {4.4F, -13.2F, 0.0F}, {-4.4F, -13.2F, 0.0F}};

    private final AvatarRenderer<?> renderer;
    private final ModelPart crown;
    private final ModelPart collar;
    private final Map<Integer, Double> nextSparkle = new HashMap<>();

    public RoyalLayer(AvatarRenderer<?> renderer, ResourceManager manager) {
        super(renderer);
        this.renderer = renderer;
        BbModel model = BbModel.load(manager, Punizioni.id("geometry/royal.bbmodel"));
        this.crown = part(model, "crown");
        this.collar = part(model, "collar");
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
        if (v == null || state.isInvisible) {
            return;
        }
        PlayerModel model = this.getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        boolean world = !v.gui && v.hasEntityPose && level != null;
        Matrix4f toEntity = world ? new Matrix4f(v.entityPose).invert() : null;
        Vec3 origin = world ? new Vec3(state.x, state.y, state.z).add(this.renderer.getRenderOffset(state)) : Vec3.ZERO;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long now = level != null ? level.getGameTime() : 0L;
        double time = now + partial;

        if (v.crown && !v.body.headless() && this.crown != null) {
            poseStack.pushPose();
            model.root().translateAndRotate(poseStack);
            model.head.translateAndRotate(poseStack);
            collector.submitModelPart(this.crown, poseStack, SOLID, light, overlay, null);
            collector.submitModelPart(this.crown, poseStack, GLOWING, light, overlay, null);
            if (world) {
                this.sparkle(level, v, poseStack, toEntity, origin, time);
            }
            poseStack.popPose();
        }

        if (v.cape) {
            poseStack.pushPose();
            model.root().translateAndRotate(poseStack);
            model.body.translateAndRotate(poseStack);
            if (this.collar != null) {
                collector.submitModelPart(this.collar, poseStack, SOLID, light, overlay, null);
            }
            Matrix4f bodyPose = new Matrix4f(poseStack.last().pose());
            if (!world) {
                Capes.submitStill(collector, poseStack, light, overlay);
            }
            poseStack.popPose();
            if (world) {
                Matrix4f[] colliders = {
                        new Matrix4f(toEntity).mul(bodyPose),
                        new Matrix4f(toEntity).mul(this.partPose(poseStack, model.rightLeg)),
                        new Matrix4f(toEntity).mul(this.partPose(poseStack, model.leftLeg))};
                CapeCloth cloth = Capes.of(v.entityId);
                cloth.update(level, time, now, colliders[0], colliders, origin.x, origin.y, origin.z);
                Capes.submit(collector, cloth, v.entityPose, origin.x, origin.y, origin.z, light, overlay);
            }
        }

        HumanoidArm arm = v.whipArm;
        if (arm != null && world && (arm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack).is(ModItems.OVERSEER_WHIP)) {
            poseStack.pushPose();
            WhipHandle.thirdPersonHand(model, state, arm, poseStack);
            Matrix4f hand = new Matrix4f(toEntity).mul(poseStack.last().pose());
            poseStack.popPose();
            Vector3f tip = hand.transformPosition(new Vector3f(WhipHandle.TIP_X / 16.0F, WhipHandle.TIP_Y / 16.0F, WhipHandle.TIP_Z / 16.0F));
            Vector3f dir = hand.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F));
            if (dir.lengthSquared() < 1.0E-8F) {
                dir.set(0.0F, -1.0F, 0.0F);
            }
            dir.normalize();
            Lash lash = Lashes.of(v.entityId);
            lash.update(level, time, now, origin.x + tip.x, origin.y + tip.y, origin.z + tip.z, dir.x, dir.y, dir.z, v.viewYaw, v.viewPitch);
            Lashes.afterUpdate(level, lash, time);
            Lashes.submit(collector, lash, v.entityPose, origin.x, origin.y, origin.z, light);
        }
    }

    private Matrix4f partPose(PoseStack poseStack, ModelPart part) {
        poseStack.pushPose();
        this.getParentModel().root().translateAndRotate(poseStack);
        part.translateAndRotate(poseStack);
        Matrix4f m = new Matrix4f(poseStack.last().pose());
        poseStack.popPose();
        return m;
    }

    /** Now and then a gem of the crown twinkles. */
    private void sparkle(ClientLevel level, Visuals v, PoseStack headPose, Matrix4f toEntity, Vec3 origin, double time) {
        Double next = this.nextSparkle.get(v.entityId);
        if (next != null && time < next && time > next - 40.0) {
            return;
        }
        this.nextSparkle.put(v.entityId, time + 3.0 + level.getRandom().nextFloat() * 7.0);
        if (this.nextSparkle.size() > 256) {
            this.nextSparkle.clear();
        }
        float[] g = GEMS[level.getRandom().nextInt(GEMS.length)];
        Vector3f p = new Matrix4f(toEntity).mul(headPose.last().pose()).transformPosition(new Vector3f(g[0] / 16.0F, g[1] / 16.0F, g[2] / 16.0F));
        level.addParticle(ModParticles.SPARKLE, origin.x + p.x, origin.y + p.y, origin.z + p.z, 0.0, 0.0, 0.0);
    }
}
