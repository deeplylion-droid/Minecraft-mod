package com.jumpscares.client.scare;

import com.jumpscares.Jumpscares;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/** Full-screen effects of a scare, drawn instead of the HUD while it lasts (also with F1). */
public final class ScareHud {
    private static final ResourceLocation VIGNETTE = Jumpscares.id("textures/misc/vignette.png");
    private static final ResourceLocation STATIC = Jumpscares.id("textures/misc/static.png");
    private static final RandomSource RANDOM = RandomSource.create();

    private ScareHud() {
    }

    /** Draws the overlays; true if a scare is running (the HUD is hidden meanwhile). */
    public static boolean render(GuiGraphics graphics, DeltaTracker delta) {
        ScareClient.Scare scare = ScareClient.active();
        if (scare == null) {
            return false;
        }
        ScarePose p = ScareClient.pose(delta.getGameTimeDeltaPartialTick(true));
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int color = scare.kind.color;
        float r = (color >> 16 & 255) / 255.0F, g = (color >> 8 & 255) / 255.0F, b = (color & 255) / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (p.vignette > 0.0F) {
            graphics.setColor(r * 0.25F, g * 0.05F, b * 0.05F, Math.min(1.0F, p.vignette));
            graphics.blit(VIGNETTE, 0, 0, w, h, 0.0F, 0.0F, 256, 256, 256, 256);
        }
        if (p.noise > 0.0F) {
            graphics.setColor(1.0F, 1.0F, 1.0F, Math.min(1.0F, p.noise));
            graphics.pose().pushPose();
            graphics.pose().scale(2.0F, 2.0F, 1.0F);
            graphics.blit(STATIC, 0, 0, RANDOM.nextInt(128), RANDOM.nextInt(128), w / 2 + 1, h / 2 + 1, 128, 128);
            graphics.pose().popPose();
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (p.flash > 0.0F) {
            // gentler for players who turned off lightning flashes
            float max = Minecraft.getInstance().options.hideLightningFlash().get() ? 0.15F : 0.8F;
            graphics.fill(0, 0, w, h, argb(Math.min(1.0F, p.flash) * max, color));
        }
        if (p.black > 0.0F) {
            graphics.fill(0, 0, w, h, argb(Math.min(1.0F, p.black), 0));
        }
        graphics.flush();
        RenderSystem.disableBlend();
        return true;
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (alpha * 255.0F) << 24) | (rgb & 0xFFFFFF);
    }
}
