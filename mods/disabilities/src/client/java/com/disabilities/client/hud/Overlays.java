package com.disabilities.client.hud;

import com.disabilities.Disabilities;
import com.disabilities.client.Fx;
import com.disabilities.client.Me;
import com.disabilities.client.screen.ChooserScreen;
import com.disabilities.condition.Condition;
import com.disabilities.condition.Picks;
import com.disabilities.condition.Status;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;

/**
 * Screen effects: the dark half of a missing eye, the sun glare, tears, tunnel vision when out of
 * breath, red pulse when bleeding, the black of sleep, the tics' blinks, the amnesia flash, and the
 * player's own condition icons in the corner.
 */
public final class Overlays {
    private Overlays() {
    }

    private static final Identifier HALF = Disabilities.id("textures/gui/overlay/half.png");
    private static final Identifier GLARE = Disabilities.id("textures/gui/overlay/glare.png");
    private static final Identifier TEARS = Disabilities.id("textures/gui/overlay/tears.png");
    private static final Identifier TUNNEL = Disabilities.id("textures/gui/overlay/tunnel.png");
    private static final Identifier VIGNETTE = Disabilities.id("textures/gui/overlay/vignette.png");

    private static float glare;
    private static float glareO;
    private static long sleepStart = -1;
    private static int ticks;

    // drawn from HudMixin, so they stay on screen with F1 too (no peeking with the HUD hidden)

    /** Every client tick: smooth values that should not jump. */
    public static void tick(Minecraft mc) {
        ticks++;
        LocalPlayer player = mc.player;
        glareO = glare;
        float target = 0.0F;
        if (player != null && Me.has(Condition.ALBINISM) && inSun(player)) {
            target = player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() ? 0.8F : 0.35F;
        } else if (player != null && Me.has(Condition.ACHROMATOPSIA) && inSun(player)) {
            // achromats are very sensitive to light too (1.1.0)
            target = player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() ? 0.55F : 0.25F;
        }
        glare += (target - glare) * 0.1F;
        if (player != null && Me.asleep()) {
            if (sleepStart < 0) {
                sleepStart = ticks;
            }
        } else {
            sleepStart = -1;
        }
    }

    public static boolean inSun(net.minecraft.world.entity.player.Player player) {
        Level level = player.level();
        BlockPos head = BlockPos.containing(player.getEyePosition());
        return level.isBrightOutside() && !level.isRainingAt(head) && level.canSeeSky(head) && !player.isUnderWater();
    }

    private static void full(GuiGraphicsExtractor g, Identifier texture, int texW, int texH, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, g.guiWidth(), g.guiHeight(), texW, texH, texW, texH, color);
    }

    /** Under everything else: what the eyes see. */
    public static void vision(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float t = ticks + partial;
        Picks picks = Me.picks();
        Status status = Me.status();
        long now = Me.now();
        int w = g.guiWidth();
        int h = g.guiHeight();

        if (picks.has(Condition.ONE_EYE)) {
            full(g, HALF, 256, 16, 0xFFFFFFFF);
        }
        float glareNow = Mth.lerp(partial, glareO, glare);
        if (glareNow > 0.01F) {
            float flicker = 0.9F + 0.1F * Mth.sin(t * 0.37F);
            full(g, GLARE, 256, 256, ARGB.color(glareNow * flicker, 0xFFFFF4D8));
        }
        float burn = Fx.burnFlash(partial);
        if (burn > 0.0F) {
            g.fill(0, 0, w, h, ARGB.color(burn * 0.35F, 0xFF4010));
        }
        float tears = Fx.tears(partial);
        if (tears > 0.0F) {
            full(g, TEARS, 256, 256, ARGB.white(tears * 0.85F));
        }
        if (picks.has(Condition.ASTHMA)) {
            float air = player.getAirSupply() / (float) player.getMaxAirSupply();
            if (air < 0.5F && !player.isUnderWater()) {
                float k = (0.5F - air) / 0.5F;
                float breath = 0.5F + 0.5F * Mth.sin(t * 0.35F);
                full(g, TUNNEL, 256, 256, ARGB.black(Mth.clamp(k * (0.7F + 0.25F * breath), 0.0F, 0.95F)));
            }
        }
        if (status.winded(now)) {
            float breath = 0.5F + 0.5F * Mth.sin(t * 0.45F);
            full(g, VIGNETTE, 256, 256, ARGB.color(0.35F + 0.2F * breath, 0xFF000000));
        }
        if (status.bleeding(now)) {
            float beat = heartbeat(t);
            full(g, VIGNETTE, 256, 256, ARGB.color(0.5F + 0.45F * beat, 0xFFC00000));
            full(g, VIGNETTE, 256, 256, ARGB.color(0.25F * beat, 0xFFFF2020));
        }
        float fear = Fx.fear(partial);
        if (fear > 0.02F) {
            full(g, VIGNETTE, 256, 256, ARGB.color(fear * 0.55F, 0xFF102018));
        }
        // 1.1.0
        float aura = Fx.aura(partial);
        if (aura > 0.0F) {
            // slow waves of orange from the edges (slow on purpose: nothing that flashes)
            float wave = 0.5F + 0.5F * Mth.sin(t * 0.25F);
            int orange = ARGB.color(aura * (0.7F + 0.3F * wave), 0xFFFF9A2E);
            full(g, VIGNETTE, 256, 256, orange);
            full(g, VIGNETTE, 256, 256, orange);
            g.fill(0, 0, w, h, ARGB.color(aura * 0.12F * wave, 0xFFFFB050));
        }
        if (status.seizing(now)) {
            g.fill(0, 0, w, h, ARGB.black(0.45F));
            full(g, TUNNEL, 256, 256, ARGB.black(0.85F));
        }
        if (picks.has(Condition.WEAK_HEART) && !status.arrested(now) && status.heartStage() > 0) {
            float beat = pulse(t, Fx.bpm());
            float k = status.heartStage() == 2 ? 0.75F : 0.35F;
            full(g, VIGNETTE, 256, 256, ARGB.color(k * (0.4F + 0.6F * beat), 0xFFB00020));
        }
        if (picks.has(Condition.CLAUSTROPHOBIA)) {
            if (status.panicking(now)) {
                float beat = pulse(t, 150);
                full(g, TUNNEL, 256, 256, ARGB.black(0.8F + 0.15F * beat));
                full(g, VIGNETTE, 256, 256, ARGB.color(0.5F * beat, 0xFF500000));
            } else if (status.anxious()) {
                float beat = pulse(t, 110);
                full(g, TUNNEL, 256, 256, ARGB.black(0.35F + 0.15F * beat));
            }
        }
        float fracture = Fx.fractureFlash(partial);
        if (fracture > 0.0F) {
            g.fill(0, 0, w, h, ARGB.color(fracture * 0.4F, 0xFFFFFFFF));
            full(g, VIGNETTE, 256, 256, ARGB.color(fracture * 0.9F, 0xFFC00000));
        }
        float bonk = Fx.bonkFlash(partial);
        if (bonk > 0.0F) {
            full(g, VIGNETTE, 256, 256, ARGB.color(bonk * 0.6F, 0xFFFFFFFF));
        }
        if (picks.has(Condition.WEAK_HEART) && !mc.gui.hud.isHidden() && !mc.debugEntries.isOverlayVisible() && !status.arrested(now)) {
            heartMonitor(g, picks, status, t);
        }
        if (!mc.debugEntries.isOverlayVisible() && !mc.gui.hud.isHidden()) {
            badges(g, picks);
        }
    }

    /** A heartbeat at {@code bpm}, 0-1. */
    private static float pulse(float t, float bpm) {
        float period = 1200.0F / Math.max(30.0F, bpm);
        float phase = (t % period) / period;
        return Math.max(0.0F, 1.0F - phase * 5.0F);
    }

    /** Weak heart: the beats per minute and a little monitor line, under the condition icons. */
    private static void heartMonitor(GuiGraphicsExtractor g, Picks picks, Status status, float t) {
        Minecraft mc = Minecraft.getInstance();
        int bpm = Fx.bpm();
        int stage = status.heartStage();
        int color = stage == 2 ? 0xFFFF3040 : stage == 1 ? 0xFFFFA040 : 0xFF60E070;
        int x = 4;
        int y = 6 + picks.list().size() * 14;
        g.fill(x - 2, y - 2, x + 66, y + 14, 0x80000000);
        g.text(mc.font, Component.literal("\u2665 " + bpm), x, y + 2, color);
        // the trace: flat, then a spike at every beat
        float period = 1200.0F / Math.max(30.0F, bpm);
        int x0 = x + 32;
        for (int i = 0; i < 30; i++) {
            float time = t - (30 - i) * 0.6F;
            float phase = ((time % period) + period) % period / period;
            int dy = phase < 0.05F ? -5 : phase < 0.1F ? 4 : phase < 0.14F ? -1 : 0;
            g.fill(x0 + i, y + 6 + dy, x0 + i + 1, y + 7 + dy, color);
        }
    }

    /** Two quick beats then a pause, 0-1. */
    private static float heartbeat(float t) {
        float phase = (t % 20.0F) / 20.0F;
        float a = Math.max(0.0F, 1.0F - Math.abs(phase - 0.08F) * 14.0F);
        float b = Math.max(0.0F, 1.0F - Math.abs(phase - 0.24F) * 14.0F);
        return Math.max(a, b * 0.7F);
    }

    /** The player's own conditions, small, top left. */
    private static void badges(GuiGraphicsExtractor g, Picks picks) {
        if (picks.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int y = 4;
        for (Condition c : picks.list()) {
            g.blit(RenderPipelines.GUI_TEXTURED, ChooserScreen.icon(c), 4, y, 0, 0, 12, 12, 16, 16, 16, 16, ARGB.white(0.85F));
            g.pose().pushMatrix();
            g.pose().translate(19, y + 3);
            g.pose().scale(0.75F, 0.75F);
            g.text(mc.font, c.displayName(), 0, 0, ARGB.color(0.85F, 0xFF000000 | c.color));
            g.pose().popMatrix();
            y += 14;
        }
    }

    /** Cardiac arrest: black, a flat line, and how long the friends have to save you. */
    private static void arrest(GuiGraphicsExtractor g, Status status, long now, float partial) {
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth();
        int h = g.guiHeight();
        g.fill(0, 0, w, h, 0xF0000000);
        int y = h / 2 + 4;
        int color = 0xFF30FF60;
        g.fill(w / 6, y, w * 5 / 6, y + 1, color);
        float left = Math.max(0.0F, (status.arrestUntil() - now - partial) / 20.0F);
        g.pose().pushMatrix();
        g.pose().translate(w / 2.0F, h / 2.0F - 40);
        g.pose().scale(2.0F, 2.0F);
        g.centeredText(mc.font, Component.translatable("disabilities.hud.arrest"), 0, 0, 0xFFFF4050);
        g.pose().popMatrix();
        g.centeredText(mc.font, Component.translatable("disabilities.hud.arrest_hint", (int) Math.ceil(left)), w / 2, y + 16, 0xFFD0D0D0);
        g.centeredText(mc.font, Component.literal("0 bpm"), w * 5 / 6 - 20, y - 12, color);
    }

    /** Over everything else: sleep, blinks, the amnesia flash. */
    public static void blackout(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth();
        int h = g.guiHeight();
        if (sleepStart >= 0) {
            float t = ticks - sleepStart + partial;
            float k = Mth.clamp(t / 12.0F, 0.0F, 1.0F);
            g.fill(0, 0, w, h, ARGB.black(k * 0.97F));
            if (k > 0.5F) {
                float bob = Mth.sin(t * 0.12F) * 3.0F;
                String z = t % 60 < 20 ? "Z" : t % 60 < 40 ? "Zz" : "Zzz";
                g.pose().pushMatrix();
                g.pose().translate(w / 2.0F, h / 2.0F - 20 + bob);
                g.pose().scale(3.0F, 3.0F);
                g.centeredText(mc.font, Component.literal(z), 0, 0, ARGB.color(k, 0xFFAAC8FF));
                g.pose().popMatrix();
                g.centeredText(mc.font, Component.translatable("disabilities.hud.asleep"), w / 2, h / 2 + 18, ARGB.color(k * 0.8F, 0xFFB0B0B0));
            }
        }
        if (Fx.blinking()) {
            g.fill(0, 0, w, h, 0xFF000000);
        }
        float nod = Fx.nod(partial);
        if (nod > 0.0F) {
            g.fill(0, 0, w, h, ARGB.black(nod * 0.92F));
        }
        Status status = Me.status();
        long now = Me.now();
        if (status.arrested(now)) {
            arrest(g, status, now, partial);
        }
        float revived = Fx.revived(partial);
        if (revived > 0.0F) {
            g.fill(0, 0, w, h, ARGB.color(revived * 0.5F, 0xFFFFFFFF));
        }
        float forgot = Fx.forgotFlash(partial);
        if (forgot > 0.0F) {
            g.fill(0, 0, w, h, ARGB.color(forgot * 0.45F, 0xFF7A3AB0));
            g.pose().pushMatrix();
            g.pose().translate(w / 2.0F, h / 2.0F - 30);
            float s = 4.0F + (1.0F - forgot) * 3.0F;
            g.pose().scale(s, s);
            g.centeredText(mc.font, Component.literal("?"), 0, 0, ARGB.color(forgot, 0xFFE8D0FF));
            g.pose().popMatrix();
        }
    }
}
