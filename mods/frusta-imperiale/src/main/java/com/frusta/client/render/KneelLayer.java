package com.frusta.client.render;

import com.frusta.server.Kneel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * The legs of a kneeling player (Sottomissione): vanilla legs are one stiff box each, so while
 * kneeling they are hidden ({@code ClientEvents}) and drawn here in two pieces from the same skin:
 * the thigh straight down from the hip to the knee on the ground, the shin lying flat behind it.
 */
public class KneelLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** How far the whole body goes down while kneeling, in blocks (the knees reach the ground). */
    public static final float DROP = 4.0F / 16.0F * 0.9375F;

    private final ModelPart root;

    public KneelLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
        this.root = createLegs().bakeRoot();
    }

    private static LayerDefinition createLegs() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation pants = new CubeDeformation(0.25F);
        // Upper halves of the legs (thighs) hang from the hips; the lower halves (shins) from the knees.
        leg(root, "right", -1.9F, 0, 16, 0, 32, pants);
        leg(root, "left", 1.9F, 16, 48, 0, 48, pants);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void leg(PartDefinition root, String name, float x, int u, int v, int pu, int pv, CubeDeformation pants) {
        root.addOrReplaceChild(name + "_thigh", CubeListBuilder.create().texOffs(u, v).addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offsetAndRotation(x, 12, 0, -0.08F, 0, 0));
        root.addOrReplaceChild(name + "_thigh_pants", CubeListBuilder.create().texOffs(pu, pv).addBox(-2, 0, -2, 4, 6, 4, pants),
                PartPose.offsetAndRotation(x, 12, 0, -0.08F, 0, 0));
        root.addOrReplaceChild(name + "_shin", CubeListBuilder.create().texOffs(u, v + 6).addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offsetAndRotation(x, 17.9F, 0.5F, 1.52F, 0, 0));
        root.addOrReplaceChild(name + "_shin_pants", CubeListBuilder.create().texOffs(pu, pv + 6).addBox(-2, 0, -2, 4, 6, 4, pants),
                PartPose.offsetAndRotation(x, 17.9F, 0.5F, 1.52F, 0, 0));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        if (!Kneel.isKneeling(player) || player.isInvisible()) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(player.getSkinTextureLocation()));
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        for (String side : new String[]{"right", "left"}) {
            boolean pantsShown = player.isModelPartShown(side.equals("right") ? PlayerModelPart.RIGHT_PANTS_LEG : PlayerModelPart.LEFT_PANTS_LEG);
            root.getChild(side + "_thigh").render(poseStack, consumer, light, overlay);
            root.getChild(side + "_shin").render(poseStack, consumer, light, overlay);
            if (pantsShown) {
                root.getChild(side + "_thigh_pants").render(poseStack, consumer, light, overlay);
                root.getChild(side + "_shin_pants").render(poseStack, consumer, light, overlay);
            }
        }
    }
}
