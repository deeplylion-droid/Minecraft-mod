package com.belowbedrock.client;

import com.belowbedrock.BelowBedrock;
import com.belowbedrock.net.ScreenFxPayload.Fx;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** His face over the HUD: a subliminal flash, or the full jumpscare. And black over everything in a blackout. */
public final class HudFx {
    private static final ResourceLocation FACE = BelowBedrock.id("textures/gui/jumpscare.png");
    private static final RandomSource RANDOM = RandomSource.create();

    private HudFx() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        ScreenFx.Pulse scare = ScreenFx.active(Fx.JUMPSCARE);
        if (scare != null) {
            float p = scare.progress(partialTick);
            float alpha = p < 0.75F ? 1.0F : 1.0F - (p - 0.75F) / 0.25F;
            float scale = 1.05F + 0.6F * Mth.sqrt(p);
            int shake = (int) (10 * (1.0F - p));
            // a red flash first, then just his face
            face(graphics, width, height, scale, alpha, RANDOM.nextInt(shake * 2 + 1) - shake, RANDOM.nextInt(shake * 2 + 1) - shake,
                    p < 0.12F ? 0.5F : 1.0F);
        } else if (ScreenFx.active(Fx.FACE) != null) {
            face(graphics, width, height, 0.9F + RANDOM.nextFloat() * 0.3F, 0.85F, RANDOM.nextInt(31) - 15, RANDOM.nextInt(21) - 10, 1.0F);
        }
        float black = ScreenFx.black(partialTick);
        if (black > 0.0F) {
            graphics.fill(0, 0, width, height, ((int) (black * 255.0F) << 24));
        }
    }

    /** The 32x32 face, square, centred, {@code scale} times the screen height. */
    private static void face(GuiGraphics graphics, int width, int height, float scale, float alpha, int dx, int dy, float red) {
        int size = (int) (height * scale);
        int x = (width - size) / 2 + dx;
        int y = (height - size) / 2 + dy;
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, red, red, alpha);
        graphics.blit(FACE, x, y, size, size, 0.0F, 0.0F, 32, 32, 32, 32);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
