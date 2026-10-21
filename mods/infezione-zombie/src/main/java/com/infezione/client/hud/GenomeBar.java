package com.infezione.client.hud;

import com.infezione.client.ClientInfections;
import com.infezione.client.fx.SymptomFx;
import com.infezione.config.InfezioneConfig;
import com.infezione.infection.Stages;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * The infection at the top left of the screen: a DNA double helix that turns slowly, and rots from the
 * left as the infection goes on. Its healthy part is pale and cold; the corrupted part is blood and black,
 * the strands broken and jittering, rungs missing, drops of blood falling from it, a flickering edge where
 * the corruption spreads, and glitches that tear the whole bar apart now and then (and at every episode).
 * Above it the stage and how much of the genome is gone; under it the time left.
 */
public final class GenomeBar {
    private static final int WIDTH = 170;
    private static final int BAR_HEIGHT = 20;
    private static final float WAVELENGTH = 23.0F;
    private static final float AMPLITUDE = 6.0F;

    /** Pale, cold strands of a healthy genome; blood and black once corrupted. */
    private static final int HEALTHY_FRONT = 0xD9DED8;
    private static final int HEALTHY_BACK = 0x6C7A78;
    private static final int[] HEALTHY_BASES = {0x6FA3A0, 0xA89E72, 0x8F82AA, 0x6F92A8};
    private static final int ROTTEN_FRONT = 0x9A161B;
    private static final int ROTTEN_BACK = 0x420708;
    private static final int[] ROTTEN_BASES = {0x5A0A0C, 0x2A0606, 0x56603A, 0x120606};
    private static final int EDGE = 0xFF3A2E;

    /** Stage names in Roman numerals: I to V. */
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    /** Ticks of a big glitch left (an episode tears the bar apart). */
    private static int glitch;

    private GenomeBar() {
    }

    /** The bar is torn apart for a moment. */
    public static void glitch(int ticks) {
        glitch = Math.max(glitch, ticks);
    }

    public static void tick() {
        if (glitch > 0) {
            glitch--;
        }
    }

    /** How much of the genome is gone, 0 to 1: a quarter for every stage up to the fourth, all of it for the zombie. */
    public static float corruption(int stage, float progress) {
        if (stage >= Stages.ZOMBIE) {
            return 1.0F;
        }
        if (stage <= Stages.HEALTHY) {
            return 0.0F;
        }
        return Mth.clamp((stage - 1 + progress) / 4.0F, 0.0F, 1.0F);
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = gui.getMinecraft();
        ClientInfections.Self self = ClientInfections.SELF;
        int stage = self.stage;
        if (stage == Stages.HEALTHY || mc.options.hideGui || mc.options.renderDebug || !InfezioneConfig.CLIENT.panel.get()
                || mc.player == null || mc.player.isSpectator()) {
            return;
        }
        gui.setupOverlayRenderState(true, false);
        Font font = mc.font;
        float time = ClientInfections.clock() + partialTick;
        boolean zombie = stage == Stages.ZOMBIE;
        float rot = corruption(stage, self.progress(partialTick));
        int color = InfectionHud.COLORS[stage];
        int x0 = 6;
        int y0 = 5;

        // header: the stage on the left, how much is corrupted on the right
        Component title = zombie ? Component.translatable("infezione.hud.zombie")
                : Component.translatable("infezione.hud.stage_name", ROMAN[stage], I18n.get("infezione.stage." + stage).toUpperCase(Locale.ROOT));
        String percent = Math.round(rot * 100) + "%";
        int shake = glitch > 0 ? (int) (Mth.sin(time * 9.0F) * 2) : 0;
        graphics.drawString(font, title.copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))), x0 + shake, y0, 0xFFFFFF, true);
        graphics.drawString(font, percent, x0 + WIDTH - font.width(percent), y0, color, true);

        // the bar
        int bx = x0;
        int by = y0 + 11;
        frame(graphics, bx, by, color, stage, time);
        int ix = bx + 1;
        int iy = by + 1;
        int iw = WIDTH - 2;
        int ih = BAR_HEIGHT - 2;
        graphics.enableScissor(ix, iy, ix + iw, iy + ih);
        helix(graphics, ix, iy, iw, ih, stage, rot, time);
        graphics.disableScissor();

        // footer: the time left, or why there is none
        Component footer;
        if (zombie) {
            footer = Component.translatable("infezione.hud.zombie_line");
        } else if (self.paused) {
            footer = Component.translatable("infezione.hud.paused");
        } else if (InfezioneConfig.CLIENT.timer.get()) {
            int seconds = (self.ticksLeft + 19) / 20;
            footer = Component.translatable("infezione.hud.next", String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
        } else {
            footer = null;
        }
        if (footer != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(x0, by + BAR_HEIGHT + 3, 0);
            graphics.pose().scale(0.75F, 0.75F, 1.0F);
            graphics.drawString(font, footer, 0, 0, self.paused ? 0x8A8480 : 0xB89A94, true);
            graphics.pose().popPose();
        }
    }

    /** A dark frame; its edge beats with the heart, and marks where each stage ends. */
    private static void frame(GuiGraphics graphics, int x, int y, int color, int stage, float time) {
        float sinceBeat = ClientInfections.clock() - SymptomFx.lastBeat();
        float beat = stage == Stages.ZOMBIE ? 0.0F : (float) Math.exp(-Math.max(0.0F, sinceBeat) / 3.0F);
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + BAR_HEIGHT + 1, 0xC0000000);
        int edge = mix(0x2A1515, color, 0.25F + 0.45F * beat);
        graphics.fill(x, y, x + WIDTH, y + BAR_HEIGHT, 0xFF000000 | edge);
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + BAR_HEIGHT - 1, 0xF0090606);
        // the stage boundaries, ticks outside the frame
        for (int k = 1; k < 4; k++) {
            int mx = x + 1 + Math.round((WIDTH - 2) * k / 4.0F);
            graphics.fill(mx, y - 2, mx + 1, y, 0xFF5A3A36);
            graphics.fill(mx, y + BAR_HEIGHT, mx + 1, y + BAR_HEIGHT + 2, 0xFF5A3A36);
        }
    }

    /** The double helix, healthy on the right of {@code rot}, rotten on its left. */
    private static void helix(GuiGraphics graphics, int x, int y, int w, int h, int stage, float rot, float time) {
        float speed = stage == Stages.ZOMBIE ? 0.025F : 0.07F;
        float phase = time * speed;
        float yc = y + h / 2.0F - 0.5F;
        int front = Math.round(rot * w);
        int frameTick = (int) time;
        // glitches: a slice of the bar slides sideways for a moment (more often the worse it gets, always on an episode)
        boolean glitching = glitch > 0 || hash(frameTick / 3, 7) < 0.04F * stage;
        int sliceY = y + (int) (hash(frameTick, 11) * h);
        int sliceH = 2 + (int) (hash(frameTick, 13) * (glitch > 0 ? 8 : 4));
        int sliceDx = (int) ((hash(frameTick, 17) - 0.5F) * (glitch > 0 ? 16 : 8));
        // the stage boundaries inside the bar
        for (int k = 1; k < 4; k++) {
            int mx = x + Math.round(w * k / 4.0F);
            graphics.fill(mx, y, mx + 1, y + h, 0x40806060);
        }
        // two passes: what is behind (back strand), then the rungs and what is in front
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < w; i++) {
                boolean rotten = i < front;
                float theta = (float) (Math.PI * 2 * i / WAVELENGTH) + phase;
                float s = Mth.sin(theta);
                float c = Mth.cos(theta);
                float jitter = 0.0F;
                if (rotten) {
                    float amp = 0.3F + 0.2F * stage;
                    jitter = Mth.sin(i * 1.7F + time * 0.9F) * amp * hash(i, 3);
                }
                float y1 = yc + AMPLITUDE * s + jitter;
                float y2 = yc - AMPLITUDE * s - jitter * 0.6F;
                // strand 1 is in front when c > 0
                boolean oneFront = c > 0;
                if (pass == 0) {
                    float backY = oneFront ? y2 : y1;
                    strand(graphics, x, i, backY, rotten, false, stage, front, glitching, sliceY, sliceH, sliceDx, time);
                } else {
                    if (i % 4 == 0) {
                        rung(graphics, x, i, y1, y2, rotten, stage, Math.abs(c), glitching, sliceY, sliceH, sliceDx, time);
                    }
                    float frontY = oneFront ? y1 : y2;
                    strand(graphics, x, i, frontY, rotten, true, stage, front, glitching, sliceY, sliceH, sliceDx, time);
                }
            }
        }
        if (rot > 0.0F) {
            corruptionDetails(graphics, x, y, w, h, front, stage, time);
        }
        if (glitch > 0 && (int) time % 3 == 0) {
            // the worst of an episode: the colours of the bar flash inverted for a frame
            graphics.fill(x, y, x + w, y + h, 0x30FF2020);
        }
    }

    private static void strand(GuiGraphics graphics, int x, int i, float sy, boolean rotten, boolean inFront, int stage,
                               int front, boolean glitching, int sliceY, int sliceH, int sliceDx, float time) {
        if (rotten && hash(i, 5 + stage) < 0.03F * stage) {
            // a break in the strand
            return;
        }
        int py = Math.round(sy);
        int px = x + i;
        if (glitching && py >= sliceY && py < sliceY + sliceH) {
            px += sliceDx;
        }
        int c;
        if (rotten) {
            // fresh blood where the rot has just arrived, black where it has been for a long time
            float decay = stage == Stages.ZOMBIE ? 0.35F + 0.35F * hash(i / 9, 3) : (front - i) / (float) Math.max(1, front);
            c = mix(inFront ? ROTTEN_FRONT : ROTTEN_BACK, inFront ? 0x1E0606 : 0x0E0303, Math.min(1.0F, decay * 0.85F));
            if (hash(i + (int) (time / 4), 23) < 0.04F) {
                c = 0x1A0A06;
            }
        } else {
            c = inFront ? HEALTHY_FRONT : HEALTHY_BACK;
        }
        graphics.fill(px, py, px + 1, py + (inFront ? 2 : 1), 0xFF000000 | c);
    }

    private static void rung(GuiGraphics graphics, int x, int i, float y1, float y2, boolean rotten, int stage, float depth,
                             boolean glitching, int sliceY, int sliceH, int sliceDx, float time) {
        int top = Math.round(Math.min(y1, y2)) + 1;
        int bottom = Math.round(Math.max(y1, y2));
        if (bottom - top < 1) {
            return;
        }
        int pair = (int) (hash(i, 31) * 4);
        int half = (top + bottom) / 2;
        float shade = 0.45F + 0.55F * depth;
        int px = x + i;
        if (glitching && top >= sliceY && top < sliceY + sliceH) {
            px += sliceDx;
        }
        if (rotten) {
            // corrupted rungs: missing, broken in half, or mutated into the wrong colours
            float r = hash(i, 37 + stage);
            if (r < 0.08F * stage) {
                return;
            }
            int a = ROTTEN_BASES[pair];
            int b = ROTTEN_BASES[(pair + 1 + (int) (hash(i, 41) * 3)) % 4];
            if (r < 0.14F * stage) {
                graphics.fill(px, top, px + 1, half - 1, 0xFF000000 | scale(a, shade));
            } else {
                graphics.fill(px, top, px + 1, half, 0xFF000000 | scale(a, shade));
                graphics.fill(px, half, px + 1, bottom, 0xFF000000 | scale(b, shade));
            }
        } else {
            int a = HEALTHY_BASES[pair];
            int b = HEALTHY_BASES[3 - pair];
            graphics.fill(px, top, px + 1, half, 0xFF000000 | scale(a, shade));
            graphics.fill(px, half, px + 1, bottom, 0xFF000000 | scale(b, shade));
        }
    }

    /** Necrotic specks, drops of blood falling, and the flickering edge where it spreads. */
    private static void corruptionDetails(GuiGraphics graphics, int x, int y, int w, int h, int front, int stage, float time) {
        int frameTick = (int) time;
        // specks of rot all over the corrupted part
        int specks = Math.min(front / 4, 6 + stage * 6);
        for (int k = 0; k < specks; k++) {
            int sx = x + (int) (hash(k, 53) * front);
            int sy = y + (int) (hash(k, 59) * h);
            int c = hash(k + frameTick / 10, 61) < 0.5F ? 0x2A0A08 : 0x5A1010;
            graphics.fill(sx, sy, sx + 1, sy + 1, 0xC0000000 | c);
        }
        // drops of blood running down from the strands
        for (int k = 0; k < 3 + stage * 2; k++) {
            int dx = (int) (hash(k, 67) * front);
            float cycle = 40.0F + hash(k, 71) * 40.0F;
            float t = ((time + hash(k, 73) * cycle) % cycle) / cycle;
            int length = (int) (t * 7);
            int top = y + h / 2 + (int) ((hash(k, 79) - 0.5F) * 6);
            graphics.fill(x + dx, top, x + dx + 1, Math.min(y + h, top + length), 0xE0880C10);
        }
        // the edge: where the rot reaches the healthy genome, it flickers and throws sparks
        if (front < w) {
            for (int row = 0; row < h; row++) {
                float n = hash(row + frameTick * 31, 83);
                int ex = x + front + (int) ((n - 0.5F) * 4);
                int c = n > 0.8F ? 0xFFFFFF : EDGE;
                int a = 0x90 + (int) (0x6F * hash(row, frameTick));
                graphics.fill(ex, y + row, ex + 1, y + row + 1, (a << 24) | c);
            }
            for (int k = 0; k < 3; k++) {
                if (hash(frameTick, 89 + k) < 0.5F) {
                    int sx = x + front + (int) (hash(frameTick, 97 + k) * 6) - 1;
                    int sy = y + (int) (hash(frameTick, 101 + k) * h);
                    graphics.fill(sx, sy, sx + 1, sy + 1, 0xFFFF6A50);
                }
            }
        }
    }

    /** A repeatable pseudo-random number between 0 and 1. */
    private static float hash(int a, int b) {
        int h = a * 0x27D4EB2D ^ b * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65536.0F;
    }

    private static int scale(int rgb, float k) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * k));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * k));
        int b = Math.min(255, (int) ((rgb & 0xFF) * k));
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float k) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - k) + ((b >> 16) & 0xFF) * k);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - k) + ((b >> 8) & 0xFF) * k);
        int bl = Math.round((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
        return (r << 16) | (g << 8) | bl;
    }
}
