package com.punizioni.client.witch;

import com.punizioni.Punizioni;
import com.punizioni.data.Curse;
import com.punizioni.data.ModData;
import com.punizioni.witch.CurseClock;
import com.punizioni.witch.CurseKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * What the cursed see of themselves, in first person: the head in the butt looks out between two
 * cheeks (that squeeze when it farts in its own face); the toad sees its warty lip under its eyes and
 * its tongue shooting out at every attack.
 */
public final class CurseHud {
    private CurseHud() {
    }

    private static final Identifier BUTT = Punizioni.id("textures/gui/butt_view.png");
    private static final Identifier TOAD = Punizioni.id("textures/gui/toad_view.png");
    private static final Identifier TONGUE = Punizioni.id("textures/gui/toad_tongue.png");

    public static void draw(GuiGraphicsExtractor g, Minecraft mc, float partial, int w, int h) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        Curse c = ModData.curse(p);
        if (c == null || c.type() == null) {
            return;
        }
        long now = mc.level.getGameTime();
        float age = now - c.since() + partial;
        float t = now + partial;
        switch (c.type()) {
            case BUTT -> butt(g, p, c, now, age, t, partial, w, h);
            case TOAD -> toad(g, p, age, partial, w, h);
            default -> {
            }
        }
    }

    private static void butt(GuiGraphicsExtractor g, LocalPlayer p, Curse c, long now, float age, float t, float partial, int w, int h) {
        float in = Mth.clamp(age / 14.0F, 0.0F, 1.0F);
        // the cheeks breathe; a fart squeezes them shut for a moment
        float squeeze = 0.04F * Mth.sin(t * 0.12F);
        int fart = CurseClock.since(p.getUUID(), now, CurseClock.FART, 50);
        if (fart >= 0 && now - c.since() > 60L) {
            float f = (fart + partial) / 50.0F;
            squeeze += 0.32F * (1.0F - f) * (0.6F + 0.4F * Mth.sin((fart + partial) * 1.9F));
        }
        int grunt = CurseClock.since(p.getUUID(), now, CurseClock.GRUNT, 34);
        if (grunt >= 0) {
            squeeze += 0.06F * Mth.sin((grunt + partial) / 34.0F * Mth.PI);
        }
        int alpha = ARGB.white(in);
        g.fill(0, 0, w, h, ARGB.color(0.16F * in, 0x4A2810));
        int mid = w / 2;
        int inward = (int) (w * Math.max(0.0F, squeeze) * 0.5F);
        // the left cheek (the texture's left half) and the right one, their inner edges move in
        g.blit(RenderPipelines.GUI_TEXTURED, BUTT, 0, 0, 0.0F, 0.0F, mid + inward, h, 128, 256, 256, 256, alpha);
        g.blit(RenderPipelines.GUI_TEXTURED, BUTT, mid - inward, 0, 128.0F, 0.0F, w - mid + inward, h, 128, 256, 256, 256, alpha);
    }

    private static void toad(GuiGraphicsExtractor g, LocalPlayer p, float age, float partial, int w, int h) {
        float in = Mth.clamp((age - 6.0F) / 10.0F, 0.0F, 1.0F);
        if (in <= 0.0F) {
            return;
        }
        // the tongue, from the mouth under the view up to the crosshair and back
        if (p.isSwinging()) {
            float out = Mth.sin(Mth.clamp(p.getSwingAnimation(partial), 0.0F, 1.0F) * Mth.PI);
            int len = (int) (h * 0.62F * out);
            int tw = Math.max(6, (int) (w * 0.055F));
            if (len > 2) {
                g.blit(RenderPipelines.GUI_TEXTURED, TONGUE, w / 2 - tw / 2, h - len, 0.0F, 0.0F, tw, len, 32, 256, 32, 256, ARGB.white(in));
            }
        }
        // the warty lip at the bottom of the view
        int lip = (int) (h * 0.24F);
        g.blit(RenderPipelines.GUI_TEXTURED, TOAD, 0, h - lip, 0.0F, 0.0F, w, lip, 256, 64, 256, 64, ARGB.white(in));
    }
}
