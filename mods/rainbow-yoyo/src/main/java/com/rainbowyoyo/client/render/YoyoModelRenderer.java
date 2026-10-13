package com.rainbowyoyo.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rainbowyoyo.RainbowYoyoMod;
import com.rainbowyoyo.client.model.YoyoMesh;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Draws the 3D yo-yo (centred on the origin, axle along X): the lit rainbow body plus its glowing rim. */
public final class YoyoModelRenderer {
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(RainbowYoyoMod.MODID, "textures/entity/rainbow_yoyo.png");
    public static final ResourceLocation GLOW_TEXTURE =
            new ResourceLocation(RainbowYoyoMod.MODID, "textures/entity/rainbow_yoyo_glow.png");
    public static final ResourceLocation WHITE =
            new ResourceLocation(RainbowYoyoMod.MODID, "textures/entity/white.png");

    private YoyoModelRenderer() {
    }

    /**
     * @param glow 0 to 1: how bright the rim and the star shine (always a little, even in the dark)
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, float glow) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();

        VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        YoyoMesh.INSTANCE.emit((x, y, z, nx, ny, nz, u, v) -> body.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(normal, nx, ny, nz)
                .endVertex());

        int shine = Math.max(0, Math.min(255, (int) (255 * (0.5F + 0.5F * glow))));
        VertexConsumer rim = buffers.getBuffer(RenderType.eyes(GLOW_TEXTURE));
        YoyoMesh.INSTANCE.emit((x, y, z, nx, ny, nz, u, v) -> rim.vertex(matrix, x, y, z)
                .color(shine, shine, shine, 255)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, nx, ny, nz)
                .endVertex());
    }
}
