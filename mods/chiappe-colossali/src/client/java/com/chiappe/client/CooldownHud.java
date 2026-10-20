package com.chiappe.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A small green-brown bar under the crosshair while the fart is coming back. */
public final class CooldownHud {
    private CooldownHud() {
    }

    static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) {
            return;
        }
        float left = FartFx.cooldown(mc);
        if (left <= 0.0F) {
            return;
        }
        int w = 24;
        int x = g.guiWidth() / 2 - w / 2;
        int y = g.guiHeight() / 2 + 14;
        int filled = Math.round(w * (1.0F - left));
        g.fill(x - 1, y - 1, x + w + 1, y + 3, 0x90000000);
        g.fill(x, y, x + filled, y + 2, 0xFF8C9A2E);
    }
}
