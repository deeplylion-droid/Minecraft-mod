package com.filorosso.client.hud;

import com.filorosso.entity.Recisione;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.Random;

/**
 * Near Recisione the picture breaks up like a bad signal: static, torn lines, dark edges. Stronger
 * the closer it is, in its later forms, while its bell rings and when it blinks away.
 */
public final class BossFx {
    private BossFx() {
    }

    private static final Random RANDOM = new Random();
    private static float strength;
    private static float target;

    public static void tick(Minecraft mc) {
        target = 0.0F;
        if (mc.player == null || mc.level == null) {
            strength = 0.0F;
            return;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Recisione boss)) {
                continue;
            }
            double d = boss.distanceTo(mc.player);
            float k = (float) Mth.clamp(1.0 - (d - 8.0) / 40.0, 0.0, 1.0) * (0.2F + 0.15F * boss.phase());
            float t = boss.actionTime(0.0F);
            if (boss.action() == Recisione.BELL && t > 8.0F && t < 40.0F && d < 48.0) {
                k = Math.max(k, 0.9F);
            } else if (boss.action() == Recisione.BLINK && d < 48.0) {
                k = Math.max(k, 0.7F);
            } else if (boss.action() == Recisione.RISE && d < 64.0) {
                k = Math.max(k, 0.5F);
            }
            if (boss.deathTime > 0) {
                k = Math.max(0.0F, 0.8F - boss.deathTime / 120.0F);
            }
            target = Math.max(target, k);
        }
        strength += (target - strength) * 0.15F;
        if (strength < 0.005F) {
            strength = 0.0F;
        }
    }

    public static void reset() {
        strength = 0.0F;
        target = 0.0F;
    }

    public static void draw(GuiGraphicsExtractor g) {
        float s = strength;
        if (s <= 0.0F) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        // static: short grey and black dashes all over the screen
        int dashes = (int) (s * 340.0F);
        for (int i = 0; i < dashes; i++) {
            int x = RANDOM.nextInt(w);
            int y = RANDOM.nextInt(h);
            int len = 1 + RANDOM.nextInt(7);
            int c = RANDOM.nextInt(5) == 0 ? 0xB0B0B0 : RANDOM.nextInt(3) == 0 ? 0x5A0A10 : 0x000000;
            g.fill(x, y, x + len, y + 1, ARGB.color((0.25F + RANDOM.nextFloat() * 0.45F) * Math.min(1.0F, s * 1.4F), c));
        }
        // torn lines: dark bands across the screen with a red edge
        if (RANDOM.nextFloat() < s * 0.6F) {
            int bands = 1 + RANDOM.nextInt(1 + (int) (s * 4.0F));
            for (int i = 0; i < bands; i++) {
                int y = RANDOM.nextInt(h);
                int bh = 1 + RANDOM.nextInt(2 + (int) (s * 10.0F));
                int x0 = RANDOM.nextInt(Math.max(1, w / 3));
                g.fill(x0, y, w, y + bh, ARGB.color(0.35F + 0.4F * s * RANDOM.nextFloat(), 0x000000));
                g.fill(x0, y + bh, w, y + bh + 1, ARGB.color(0.5F * s, 0x8A0A14));
            }
        }
        // the edges go dark
        int edge = Math.max(24, Math.min(w, h) / 3);
        float a = 0.55F * s;
        g.fillGradient(0, 0, w, edge, ARGB.color(a, 0x000000), ARGB.color(0.0F, 0x000000));
        g.fillGradient(0, h - edge, w, h, ARGB.color(0.0F, 0x000000), ARGB.color(a, 0x000000));
        for (int i = 0; i < edge; i += 2) {
            float k = 1.0F - i / (float) edge;
            int col = ARGB.color(a * k * k, 0x000000);
            g.fill(i, 0, i + 2, h, col);
            g.fill(w - i - 2, 0, w - i, h, col);
        }
    }
}
