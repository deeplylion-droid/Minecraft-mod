package com.infezione.client.render;

import com.infezione.client.ClientInfections;
import com.infezione.config.InfezioneConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.joml.Vector3f;

/** The boils on infected players, from stage 3: 3D domes on the body parts, throbbing and bursting. */
public class BoilsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final Boils.Shape shape = new Boils.Shape();
    private final Vector3f point = new Vector3f();
    private final Vector3f normal = new Vector3f();

    public BoilsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        int stage = ClientInfections.stage(player);
        if (stage < 3 || player.isInvisible() || !InfezioneConfig.CLIENT.boils.get()) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = this.getParentModel();
        boolean slim = "slim".equals(player.getModelName());
        long seed = seed(player);
        float age = player.tickCount + partialTick;
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(Boils.TEXTURE));
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        for (int i = 0; i < Boils.SPOTS.length; i++) {
            Boils.Spot spot = Boils.SPOTS[i];
            ModelPart part = part(model, spot.part());
            if (!part.visible) {
                continue;
            }
            Boils.shape(i, seed, stage, age, this.shape);
            if (!this.shape.shown) {
                continue;
            }
            poseStack.pushPose();
            part.translateAndRotate(poseStack);
            Boils.anchor(spot, this.shape.a, this.shape.b, slim, this.point, this.normal);
            Boils.draw(poseStack, buffer, this.point, this.normal, this.shape, seed, i, age, light, overlay);
            poseStack.popPose();
        }
    }

    static ModelPart part(PlayerModel<?> model, Boils.Part part) {
        return switch (part) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_LEG -> model.rightLeg;
            case LEFT_LEG -> model.leftLeg;
        };
    }

    /** The same boils for the same player, on every client. */
    public static long seed(AbstractClientPlayer player) {
        return player.getUUID().getMostSignificantBits() * 31 + player.getUUID().getLeastSignificantBits();
    }
}
