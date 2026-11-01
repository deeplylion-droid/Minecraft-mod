package com.punizioni.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.punizioni.Punizioni;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * Big fire: tongues of flame (an 8-frame strip, textures/entity/fire_sheet.png) on crossed planes
 * around a centre, each with its own height, phase and flicker, glowing in the dark.
 */
public final class Flames {
    private Flames() {
    }

    private static final Identifier SHEET = Punizioni.id("textures/entity/fire_sheet.png");
    private static final RenderType FIRE = RenderTypes.entityTranslucentEmissive(SHEET);
    private static final int FRAMES = 8;

    /**
     * Fire around the pose stack's origin: {@code count} tongues on a ring of {@code radius} (the first
     * one in the middle), as tall as {@code height} at full {@code level}.
     */
    public static void ring(SubmitNodeCollector collector, PoseStack poseStack, float time, float level, int count, float radius, float height,
                            float width, long seed) {
        if (level <= 0.01F) {
            return;
        }
        collector.submitCustomGeometry(poseStack, FIRE, (pose, buffer) -> {
            for (int i = 0; i < count; i++) {
                float h = hash(seed, i);
                float a = (i + h * 0.6F) * Mth.TWO_PI / count;
                float r = i == 0 ? 0.0F : radius * (0.55F + 0.45F * hash(seed + 7, i));
                float cx = Mth.cos(a) * r;
                float cz = Mth.sin(a) * r;
                float grow = Mth.clamp(level * 1.3F - (r / Math.max(0.01F, radius)) * 0.3F, 0.0F, 1.0F);
                float flick = 0.85F + 0.15F * Mth.sin(time * (0.35F + h * 0.3F) + h * 20.0F);
                float th = height * grow * flick * (0.6F + 0.4F * hash(seed + 3, i)) * (i == 0 ? 1.25F : 1.0F);
                float tw = width * (0.7F + 0.5F * hash(seed + 5, i)) * (0.5F + 0.5F * grow);
                if (th < 0.05F) {
                    continue;
                }
                int frame = (int) (time * 0.5F + h * FRAMES) % FRAMES;
                float v0 = frame / (float) FRAMES;
                float v1 = (frame + 1) / (float) FRAMES;
                float turn = h * Mth.PI + time * 0.01F;
                for (int plane = 0; plane < 2; plane++) {
                    float ang = turn + plane * Mth.HALF_PI;
                    float dx = Mth.cos(ang) * tw * 0.5F;
                    float dz = Mth.sin(ang) * tw * 0.5F;
                    // the top of the tongue leans and wavers
                    float lean = Mth.sin(time * 0.21F + h * 9.0F) * 0.15F * th;
                    int color = 0xFFFFFFFF;
                    int light = LightCoordsUtil.FULL_BRIGHT;
                    buffer.addVertex(pose, cx - dx, 0.0F, cz - dz).setColor(color).setUv(0.0F, v1).setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
                    buffer.addVertex(pose, cx + dx, 0.0F, cz + dz).setColor(color).setUv(1.0F, v1).setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
                    buffer.addVertex(pose, cx + dx + lean, th, cz + dz).setColor(color).setUv(1.0F, v0).setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
                    buffer.addVertex(pose, cx - dx + lean, th, cz - dz).setColor(color).setUv(0.0F, v0).setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
                }
            }
        });
    }

    private static float hash(long seed, int i) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
