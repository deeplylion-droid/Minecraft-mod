package com.frusta.client.hud;

import com.frusta.FrustaMod;
import com.frusta.item.WhipItem;
import com.frusta.motion.Whip;
import com.frusta.server.Kneel;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** On screen: the charge of the Imperial Crack under the crosshair, and a dark vignette while you kneel. */
public final class WhipHud {
    private static final ResourceLocation VIGNETTE = FrustaMod.id("textures/fx/kneel_vignette.png");

    private WhipHud() {
    }

    /** A bar under the crosshair while the whip spins: grey until it is enough, gold, then white and pulsing. */
    public static void charge(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || !player.isUsingItem() || !(player.getUseItem().getItem() instanceof WhipItem item)) {
            return;
        }
        float held = player.getTicksUsingItem() + partialTick;
        float charge = (float) Whip.charge(held);
        int x = width / 2 - 20;
        int y = height / 2 + 9;
        graphics.fill(x - 1, y - 1, x + 41, y + 4, 0xA0000000);
        int color;
        if (held < Whip.TWIRL_MIN) {
            color = 0xFF9A9A9A;
        } else if (charge < 1.0F) {
            color = 0xFF000000 | item.getMaterial().glow();
        } else {
            float pulse = 0.5F + 0.5F * Mth.sin((player.tickCount + partialTick) * 0.9F);
            int c = (int) Mth.lerp(pulse, 200, 255);
            color = 0xFF000000 | c << 16 | c << 8 | c;
            Component text = Component.translatable("hud.frusta.imperial");
            graphics.drawCenteredString(mc.font, text, width / 2, y + 7, 0xFFFFD44A);
        }
        graphics.fill(x, y, x + Math.round(40 * charge), y + 3, color);
        // the mark where it becomes an Imperial Crack
        int mark = x + Math.round(40.0F * Whip.TWIRL_MIN / Whip.TWIRL_FULL);
        graphics.fill(mark, y - 1, mark + 1, y + 4, 0xFFFFFFFF);
    }

    /** While you kneel the edges of the screen go dark purple, pulsing slowly. */
    public static void kneel(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !Kneel.isKneeling(player)) {
            return;
        }
        float pulse = 0.75F + 0.25F * Mth.sin((player.tickCount + partialTick) * 0.25F);
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, pulse);
        graphics.blit(VIGNETTE, 0, 0, 0, 0.0F, 0.0F, width, height, width, height);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
