package com.punizioni.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.punizioni.Punizioni;
import com.punizioni.machine.BrazenBull;
import com.punizioni.machine.Gallows;
import com.punizioni.machine.Guillotine;
import com.punizioni.machine.IronMaiden;
import com.punizioni.machine.Machine;
import com.punizioni.machine.Pyre;
import com.punizioni.machine.QuarteringRack;
import com.punizioni.machine.RatTable;
import com.punizioni.machine.Wheel;
import com.punizioni.net.Net;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * What the victim sees on their own screen: a flash and a jolt at every blow (the server says when),
 * blood that splashes on the screen and runs down, and what the machine does all the time: the
 * darkness inside the maiden and the bull, the heat of the fire, the tunnel of the noose, the pull
 * of the horses (the view widens), the beating heart under the blade, the black after it.
 */
public final class ScreenFx {
    private ScreenFx() {
    }

    private static final Identifier SPLATTER = Punizioni.id("textures/gui/blood_splatter.png");
    private static final Identifier POOP_SPLATTER = Punizioni.id("textures/gui/poop_splatter.png");
    private static final Identifier VIGNETTE = Punizioni.id("textures/gui/vignette.png");
    private static final RandomSource RANDOM = RandomSource.create();

    private record Splat(float x, float y, float size, int variant, float rot, long born, int life) {
    }

    private static final List<Splat> SPLATS = new ArrayList<>();
    /** Dung on the screen: it hits, slides down slowly and is wiped off bit by bit. */
    private static final List<Splat> DUNG = new ArrayList<>();

    /** The last blow: when, what colour, how strong. */
    private static long flashTime = -1000L;
    private static int flashColor;
    private static float flashStrength;
    private static float flashTicks = 8.0F;
    /** Jolts add up and die away. */
    private static long shakeTime = -1000L;
    private static float shakeStrength;
    private static float shakeTicks = 10.0F;
    /** Heartbeat (client sound) while waiting under the blade or hanging. */
    private static int heartIn;

    public static void reset() {
        SPLATS.clear();
        DUNG.clear();
        flashTime = -1000L;
        shakeTime = -1000L;
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0L;
    }

    /** A blow, as the server tells it. */
    public static void hit(int kind, float strength) {
        long now = now();
        switch (kind) {
            case Net.VictimFx.LASH -> {
                flash(now, 0xFFB01010, 0.45F * strength, 7.0F);
                shake(now, 2.2F * strength, 8.0F);
                splats(now, 1 + RANDOM.nextInt(2), 0.5F, 70);
            }
            case Net.VictimFx.SPIKES -> {
                flash(now, 0xFF900000, 0.75F * strength, 14.0F);
                shake(now, 4.5F * strength, 16.0F);
                splats(now, 3 + RANDOM.nextInt(3), 0.9F, 140);
            }
            case Net.VictimFx.STRETCH -> {
                flash(now, 0xFF700000, 0.25F * strength, 10.0F);
                shake(now, 1.5F + 2.5F * strength, 14.0F);
            }
            case Net.VictimFx.CHOP -> {
                flash(now, 0xFFFFFFFF, 0.85F, 6.0F);
                shake(now, 6.0F, 18.0F);
                splats(now, 5, 1.1F, 160);
            }
            case Net.VictimFx.BITE -> {
                flash(now, 0xFF800000, 0.18F + 0.25F * strength, 5.0F);
                shake(now, 0.8F + 1.2F * strength, 6.0F);
                if (RANDOM.nextFloat() < 0.4F) {
                    splats(now, 1, 0.35F, 60);
                }
            }
            case Net.VictimFx.BURN -> {
                flash(now, 0xFFFF6010, 0.15F + 0.25F * strength, 8.0F);
                shake(now, 0.8F + 1.5F * strength, 10.0F);
            }
            case Net.VictimFx.BONE -> {
                flash(now, 0xFFFFFFFF, 0.6F, 5.0F);
                shake(now, 5.5F, 14.0F);
                splats(now, 2, 0.6F, 90);
            }
            case Net.VictimFx.PECK -> {
                flash(now, 0xFF600000, 0.3F, 5.0F);
                shake(now, 1.2F, 6.0F);
                splats(now, 1, 0.4F, 70);
            }
            case Net.VictimFx.HANG -> {
                flash(now, 0xFF000000, 0.6F * strength, 12.0F);
                shake(now, 5.0F * strength, 20.0F);
            }
            case Net.VictimFx.GRUEL -> {
                flash(now, 0xFF6E8A2A, 0.6F, 34.0F);
                shake(now, 1.0F, 10.0F);
            }
            case Net.VictimFx.CURSE -> {
                com.punizioni.witch.CurseKind curse = com.punizioni.witch.CurseKind.byIndex((int) strength);
                flash(now, 0xFF000000 | (curse != null ? curse.color() : 0x8040A0), 0.85F, 30.0F);
                shake(now, 3.0F, 18.0F);
            }
            case Net.VictimFx.FART -> {
                flash(now, 0xFF6E7A2A, 0.7F, 40.0F);
                shake(now, 2.5F, 14.0F);
            }
            case Net.VictimFx.POOP -> {
                flash(now, 0xFF4A2E12, 0.3F + 0.25F * strength, 8.0F);
                shake(now, 1.2F + 2.4F * strength, 8.0F);
                dung(now, strength);
            }
            default -> {
            }
        }
    }

    /** The head comes off: a white flash, then the fall (VictimCamera) and the dark. */
    public static void beheaded() {
        long now = now();
        flash(now, 0xFFFFFFFF, 0.6F, 5.0F);
        shake(now, 6.0F, 10.0F);
        splats(now, 4, 1.0F, 400);
    }

    private static void flash(long now, int color, float strength, float ticks) {
        float left = flashLeft(now, 0.0F);
        if (strength >= left) {
            flashTime = now;
            flashColor = color;
            flashStrength = Math.min(1.0F, strength);
            flashTicks = ticks;
        }
    }

    private static float flashLeft(long now, float partial) {
        float t = now - flashTime + partial;
        return t < 0.0F || t > flashTicks ? 0.0F : flashStrength * (1.0F - t / flashTicks);
    }

    private static void shake(long now, float strength, float ticks) {
        float left = shakeLeft(now, 0.0F);
        shakeTime = now;
        shakeStrength = Math.min(9.0F, left + strength);
        shakeTicks = ticks;
    }

    private static float shakeLeft(long now, float partial) {
        float t = now - shakeTime + partial;
        if (t < 0.0F || t > shakeTicks) {
            return 0.0F;
        }
        float k = 1.0F - t / shakeTicks;
        return shakeStrength * k * k;
    }

    /** In the face (strength 1): one big splat over the middle of the view. Elsewhere: at an edge. */
    private static void dung(long now, float strength) {
        if (strength >= 0.9F) {
            DUNG.add(new Splat(0.38F + RANDOM.nextFloat() * 0.24F, 0.34F + RANDOM.nextFloat() * 0.24F, 1.45F + RANDOM.nextFloat() * 0.4F, RANDOM.nextInt(4),
                    0.0F, now, 150));
            int bits = 2 + RANDOM.nextInt(3);
            for (int i = 0; i < bits; i++) {
                DUNG.add(new Splat(0.08F + RANDOM.nextFloat() * 0.84F, 0.08F + RANDOM.nextFloat() * 0.8F, 0.35F + RANDOM.nextFloat() * 0.35F,
                        RANDOM.nextInt(4), 0.0F, now, 120));
            }
        } else {
            int n = strength >= 0.5F ? 2 : 1;
            for (int i = 0; i < n; i++) {
                boolean side = RANDOM.nextBoolean();
                float x = side ? (RANDOM.nextBoolean() ? 0.06F : 0.94F) + (RANDOM.nextFloat() - 0.5F) * 0.1F : 0.15F + RANDOM.nextFloat() * 0.7F;
                float y = side ? 0.2F + RANDOM.nextFloat() * 0.65F : (strength >= 0.5F ? 0.05F : 0.95F) + (RANDOM.nextFloat() - 0.5F) * 0.08F;
                DUNG.add(new Splat(x, y, 0.5F + RANDOM.nextFloat() * 0.4F + 0.3F * strength, RANDOM.nextInt(4), 0.0F, now, 90));
            }
        }
        while (DUNG.size() > 14) {
            DUNG.remove(0);
        }
    }

    private static void splats(long now, int n, float size, int life) {
        for (int i = 0; i < n; i++) {
            float x = 0.1F + RANDOM.nextFloat() * 0.8F;
            float y = 0.08F + RANDOM.nextFloat() * 0.75F;
            SPLATS.add(new Splat(x, y, size * (0.5F + RANDOM.nextFloat() * 0.7F), RANDOM.nextInt(4), RANDOM.nextFloat() * 360.0F, now, life));
        }
        while (SPLATS.size() > 24) {
            SPLATS.remove(0);
        }
    }

    // ------------------------------------------------------------------ every tick

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        SPLATS.removeIf(s -> now - s.born > s.life);
        DUNG.removeIf(s -> now - s.born > s.life);
        boolean beating = false;
        float rate = 1.0F;
        if (mc.player.getVehicle() instanceof Machine m && mc.player.isAlive()) {
            if (m instanceof Guillotine && m.state() == Guillotine.LOCKED) {
                beating = true;
                rate = 1.0F + Math.min(1.0F, m.stateTicks() / 200.0F);
            } else if (m instanceof Gallows && m.state() == Gallows.HANGING) {
                beating = true;
                // slower and slower
                rate = Math.max(0.35F, 1.4F - m.stateTicks() / 160.0F);
            } else if (m instanceof QuarteringRack && (m.state() == QuarteringRack.PULLING || m.state() == QuarteringRack.TENSION)) {
                beating = true;
                rate = 1.8F;
            }
        }
        if (beating) {
            if (--heartIn <= 0) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.9F + 0.15F * rate, 0.9F));
                heartIn = Math.max(8, (int) (24.0F / rate));
            }
        } else {
            heartIn = 0;
        }
    }

    // ------------------------------------------------------------------ the camera

    /** Extra field of view: the horses pull the view wide, the blade and the fire close it in. */
    public static float fov(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof Machine m)) {
            return 1.0F;
        }
        if (m instanceof QuarteringRack r) {
            float s = r.stretch(partial);
            return 1.0F + 0.32F * s + (m.state() == QuarteringRack.TENSION ? 0.03F * Mth.sin(m.stateAge(partial) * 0.9F) : 0.0F);
        }
        if (m instanceof Guillotine && m.state() == Guillotine.LOCKED) {
            return 1.0F - 0.12F * Math.min(1.0F, m.stateAge(partial) / 120.0F);
        }
        if (m instanceof Gallows && m.state() == Gallows.HANGING) {
            return 1.0F - 0.2F * Math.min(1.0F, m.stateAge(partial) / 140.0F);
        }
        return 1.0F;
    }

    /** The jolts, where vanilla tilts the view when you get hurt. */
    public static void shake(PoseStack poseStack, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        float amount = shakeLeft(now, partial);
        if (mc.player != null && mc.player.getVehicle() instanceof Machine m && mc.player.isAlive()) {
            // the machines that never stop hurting shake a little all the time
            float base = switch (m) {
                case QuarteringRack r when m.state() == QuarteringRack.TENSION -> 1.6F;
                case QuarteringRack r when m.state() == QuarteringRack.PULLING -> 0.6F * r.stretch(partial);
                case Pyre p when m.state() == Pyre.BURNING -> 0.5F * p.fire(partial);
                case BrazenBull b when m.state() == BrazenBull.HEATING -> 0.9F * b.heat(partial);
                case RatTable t when m.state() == RatTable.SWARM -> 0.5F;
                case IronMaiden im when m.state() == IronMaiden.CLOSED -> 0.4F;
                case Guillotine g when m.state() == Guillotine.LOCKED -> 0.25F;
                default -> 0.0F;
            };
            amount += base;
        }
        amount *= mc.options.screenEffectScale().get().floatValue();
        if (amount <= 0.001F) {
            return;
        }
        float t = now + partial;
        poseStack.rotateDegrees(Axis.ZP, amount * (Mth.sin(t * 2.3F) + 0.5F * Mth.sin(t * 5.9F + 1.0F)));
        poseStack.rotateDegrees(Axis.XP, amount * 0.6F * (Mth.sin(t * 3.1F + 2.0F) + 0.4F * Mth.sin(t * 7.3F)));
    }

    // ------------------------------------------------------------------ the HUD

    public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        int w = g.guiWidth();
        int h = g.guiHeight();
        float t = now + partial;

        // what the machine does all the time
        if (mc.player.getVehicle() instanceof Machine m) {
            float age = m.stateAge(partial);
            switch (m) {
                case IronMaiden im -> {
                    if (m.state() == IronMaiden.CLOSING) {
                        vignette(g, w, h, 0xFF000000, 0.6F * (1.0F - im.doors(partial)));
                    } else if (m.state() == IronMaiden.CLOSED) {
                        // shut in the iron: almost black, red at the edges, light through the cracks
                        g.fill(0, 0, w, h, ARGB.black(0.55F));
                        vignette(g, w, h, 0xFF600000, 0.75F + 0.15F * Mth.sin(t * 0.25F));
                    }
                }
                case QuarteringRack r -> {
                    float s = r.stretch(partial);
                    if (s > 0.01F) {
                        vignette(g, w, h, 0xFF8A0000, Math.min(0.85F, 0.25F + 0.6F * s) * (0.8F + 0.2F * Mth.sin(t * 0.5F)));
                    }
                }
                case Pyre p -> {
                    if (m.state() == Pyre.BURNING) {
                        float f = p.fire(partial);
                        // the heat from below, flickering, and smoke closing in
                        g.fillGradient(0, h / 3, w, h, ARGB.color(0.0F, 0xFF4000), ARGB.color(Math.min(0.7F, 0.45F * f), 0xFF6A10));
                        vignette(g, w, h, 0xFFFF5000, Math.min(0.8F, 0.3F + 0.4F * f) * (0.85F + 0.15F * Mth.sin(t * 1.7F)));
                        g.fill(0, 0, w, h, ARGB.color(Math.min(0.6F, age / 400.0F), 0x2A2420));
                    } else if (m.state() == Pyre.TIED) {
                        vignette(g, w, h, 0xFF000000, 0.35F);
                    }
                }
                case BrazenBull b -> {
                    if (m.state() != BrazenBull.IDLE) {
                        // inside the bronze: dark, the walls glowing red as they heat up
                        float heat = b.heat(partial);
                        g.fill(0, 0, w, h, ARGB.black(Math.max(0.0F, 0.82F - 0.45F * heat)));
                        g.fill(0, 0, w, h, ARGB.color(Math.min(0.55F, 0.6F * heat) * (0.8F + 0.2F * Mth.sin(t * 0.9F)), 0xC02000));
                        vignette(g, w, h, 0xFF000000, 0.9F);
                    }
                }
                case Gallows gl -> {
                    if (m.state() == Gallows.HANGING) {
                        // the tunnel closes in, the blood beats in the eyes
                        float k = Math.min(1.0F, age / 160.0F);
                        vignette(g, w, h, 0xFF000000, 0.4F + 0.6F * k);
                        vignette(g, w, h, 0xFF500010, 0.35F * (0.5F + 0.5F * Mth.sin(t * 0.6F)));
                        g.fill(0, 0, w, h, ARGB.black(Math.max(0.0F, (age - 100.0F) / 120.0F) * 0.85F));
                    } else if (m.state() == Gallows.NOOSED) {
                        vignette(g, w, h, 0xFF000000, 0.3F);
                    }
                }
                case Guillotine gu -> {
                    if (m.state() == Guillotine.LOCKED) {
                        vignette(g, w, h, 0xFF300000, 0.35F + 0.25F * Math.abs(Mth.sin(t * 0.35F)));
                    }
                }
                case RatTable rt -> {
                    if (m.state() == RatTable.SWARM) {
                        vignette(g, w, h, 0xFF500000, 0.45F + 0.1F * Mth.sin(t * 1.3F));
                    }
                }
                case Wheel wh -> {
                    if (m.state() == Wheel.RAISED) {
                        vignette(g, w, h, 0xFF000000, 0.3F + Math.min(0.4F, age / 600.0F));
                    }
                }
                default -> {
                }
            }
        }
        float head = VictimCamera.sinceHead(partial);
        if (head >= 0.0F) {
            // the last seconds of a head in a basket: it all goes dark
            vignette(g, w, h, 0xFF000000, Math.min(1.0F, 0.3F + head / 50.0F));
            g.fill(0, 0, w, h, ARGB.black(Mth.clamp((head - 30.0F) / 50.0F, 0.0F, 0.92F)));
        }

        // blood on the screen: it splashes, runs down a little and dries
        for (Splat s : SPLATS) {
            float age = now - s.born + partial;
            float k = age / s.life;
            float alpha = k < 0.7F ? 0.92F : 0.92F * (1.0F - (k - 0.7F) / 0.3F);
            float size = Math.min(w, h) * 0.45F * s.size * (age < 2.0F ? 0.6F + 0.2F * age : 1.0F);
            float run = Math.min(1.0F, age / 120.0F) * size * 0.25F;
            int x = (int) (s.x * w - size / 2.0F);
            int y = (int) (s.y * h - size / 2.0F + run);
            int u = (s.variant & 1) * 128;
            int v = (s.variant >> 1) * 128;
            g.blit(RenderPipelines.GUI_TEXTURED, SPLATTER, x, y, u, v, (int) size, (int) size, 128, 128, 256, 256, ARGB.white(Mth.clamp(alpha, 0.0F, 1.0F)));
        }

        // dung on the screen: it slides down slowly and gets wiped off
        for (Splat s : DUNG) {
            float age = now - s.born + partial;
            float k = age / s.life;
            float alpha = k < 0.6F ? 0.97F : 0.97F * (1.0F - (k - 0.6F) / 0.4F);
            float size = Math.min(w, h) * 0.5F * s.size * (age < 2.0F ? 0.75F + 0.125F * age : 1.0F);
            float run = Math.min(1.0F, age / 160.0F) * size * 0.18F;
            int x = (int) (s.x * w - size / 2.0F);
            int y = (int) (s.y * h - size / 2.0F + run);
            int u = (s.variant & 1) * 128;
            int v = (s.variant >> 1) * 128;
            g.blit(RenderPipelines.GUI_TEXTURED, POOP_SPLATTER, x, y, u, v, (int) size, (int) size, 128, 128, 256, 256,
                    ARGB.white(Mth.clamp(alpha, 0.0F, 1.0F)));
        }

        com.punizioni.client.witch.CurseHud.draw(g, mc, partial, w, h);

        float flash = flashLeft(now, partial);
        if (flash > 0.0F) {
            if ((flashColor & 0xFFFFFF) == 0xFFFFFF) {
                g.fill(0, 0, w, h, ARGB.color(flash, 0xFFFFFF));
            } else {
                vignette(g, w, h, flashColor, Math.min(1.0F, flash * 1.4F));
                g.fill(0, 0, w, h, ARGB.color(flash * 0.45F, flashColor & 0xFFFFFF));
            }
        }
    }

    private static void vignette(GuiGraphicsExtractor g, int w, int h, int color, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0, 0, w, h, 256, 256, 256, 256, ARGB.color(Mth.clamp(alpha, 0.0F, 1.0F), color & 0xFFFFFF));
    }

    /** Remove splats that are not needed (called while the world is not loaded). */
    public static void clear() {
        Iterator<Splat> it = SPLATS.iterator();
        while (it.hasNext()) {
            it.next();
            it.remove();
        }
    }
}
