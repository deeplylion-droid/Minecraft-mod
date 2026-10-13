package com.wurstel.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.wurstel.client.anim.WurstelAnimManager;
import com.wurstel.client.anim.WurstelAnimState;
import com.wurstel.client.geom.WurstelGeometry;
import com.wurstel.config.WurstelConfig;
import com.wurstel.item.WurstelArmorMaterial;
import com.wurstel.item.WurstelBeltItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Draws the giant würstel, both on entity models (third person) and for yourself in first person. */
public final class WurstelRenderer {
    /** Belt buckle on the body, in model pixels from the body pivot (the neck); -Z is the front. */
    private static final float BUCKLE_X = 0.0F;
    private static final float BUCKLE_Y = 10.5F;
    private static final float BUCKLE_Z = -2.3F;
    /** Players are drawn at 15/16 scale; undo it so the würstel is really 4 blocks long. */
    private static final float PLAYER_SCALE = 0.9375F;

    private static final WurstelGeometry GEOMETRY = new WurstelGeometry();

    private WurstelRenderer() {
    }

    public static RenderType renderType(ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
    }

    /** Texture of the würstel carried by this belt: plain, diamond or netherite. */
    public static ResourceLocation texture(ItemStack belt) {
        WurstelArmorMaterial material = belt.getItem() instanceof WurstelBeltItem item
                ? item.getWurstelMaterial() : WurstelArmorMaterial.WURSTEL;
        return material.wurstelTexture();
    }

    /** True while an entity is drawn inside a screen (the inventory preview uses an orthographic projection). */
    public static boolean isGuiRender() {
        return RenderSystem.getProjectionMatrix().m33() > 0.5F;
    }

    /** Draws the würstel of a humanoid model; the pose stack must be in the model's root space. */
    public static void renderOnModel(PoseStack poseStack, MultiBufferSource buffers, int light, int overlay,
                                     LivingEntity entity, ModelPart body, ModelPart head, float partialTick,
                                     float lengthScale) {
        render(poseStack, buffers, light, overlay, entity,
                body.x, body.y, body.z, body.xRot, body.yRot, body.zRot,
                head.yRot, head.xRot, partialTick, lengthScale);
    }

    /**
     * Draws the würstel hanging from the belt buckle and pointing where the head looks.
     *
     * @param headYRot head yaw relative to the body, radians (model convention)
     * @param headXRot head pitch, radians (model convention, positive = down)
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, int light, int overlay,
                              LivingEntity entity,
                              float bodyX, float bodyY, float bodyZ,
                              float bodyXRot, float bodyYRot, float bodyZRot,
                              float headYRot, float headXRot, float partialTick, float lengthScale) {
        poseStack.pushPose();

        // Ride along with the body to the buckle...
        Vector3f buckle = new Quaternionf().rotationZYX(bodyZRot, bodyYRot, bodyXRot)
                .transform(new Vector3f(BUCKLE_X, BUCKLE_Y, BUCKLE_Z));
        poseStack.translate((bodyX + buckle.x()) / 16.0F, (bodyY + buckle.y()) / 16.0F, (bodyZ + buckle.z()) / 16.0F);
        // ...but stick out straight where the head is looking.
        poseStack.mulPose(new Quaternionf().rotationZYX(0.0F, headYRot, headXRot));
        if (entity instanceof Player) {
            float s = 1.0F / PLAYER_SCALE;
            poseStack.scale(s, s, s);
        }

        WurstelAnimState state = WurstelAnimManager.state(entity);
        ItemStack belt = entity.getItemBySlot(EquipmentSlot.LEGS);
        // An enchanted belt makes its würstel shimmer, like enchanted armor.
        VertexConsumer consumer = ItemRenderer.getArmorFoilBuffer(buffers, renderType(texture(belt)), false, belt.hasFoil());
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();

        GEOMETRY.build((u, out) -> state.sample(partialTick, u, out), lengthScale);
        GEOMETRY.emitQuads((x, y, z, u, v, nx, ny, nz) -> consumer.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(normal, nx, ny, nz)
                .endVertex());

        poseStack.popPose();
    }

    /**
     * In first person your own body is not drawn, so the würstel is drawn here, in the world, with the
     * same transforms the player renderer would use. It is depth tested like any other entity.
     */
    public static void renderFirstPerson(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !WurstelConfig.CLIENT.showInFirstPerson.get()) {
            return;
        }
        if (!mc.options.getCameraType().isFirstPerson() || mc.getCameraEntity() != player
                || event.getCamera().isDetached()) {
            return;
        }
        float partialTick = event.getPartialTick();
        if (!WurstelBeltItem.isWearing(player) || player.isSpectator() || player.isSleeping()
                || player.isFallFlying() || player.isAutoSpinAttack() || player.isVisuallySwimming()
                || player.getSwimAmount(partialTick) > 0.0F) {
            return;
        }

        boolean crouching = player.isCrouching();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(
                Mth.lerp(partialTick, player.xo, player.getX()) - camera.x,
                Mth.lerp(partialTick, player.yo, player.getY()) - camera.y + (crouching ? -0.125 : 0.0),
                Mth.lerp(partialTick, player.zo, player.getZ()) - camera.z);
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        // Aim exactly where the camera looks.
        float headYaw = Mth.wrapDegrees(player.getViewYRot(partialTick) - bodyYaw) * Mth.DEG_TO_RAD;
        float pitch = player.getViewXRot(partialTick) * Mth.DEG_TO_RAD;

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int light = mc.getEntityRenderDispatcher().getPackedLightCoords(player, partialTick);
        render(poseStack, buffers, light, OverlayTexture.NO_OVERLAY, player,
                0.0F, crouching ? 3.2F : 0.0F, 0.0F, crouching ? 0.5F : 0.0F, 0.0F, 0.0F,
                headYaw, pitch, partialTick, 1.0F);
        ItemStack belt = player.getItemBySlot(EquipmentSlot.LEGS);
        buffers.endBatch(renderType(texture(belt)));
        if (belt.hasFoil()) {
            // After the würstel: the glint only draws where the würstel already is.
            buffers.endBatch(RenderType.armorEntityGlint());
        }
        poseStack.popPose();
    }
}
