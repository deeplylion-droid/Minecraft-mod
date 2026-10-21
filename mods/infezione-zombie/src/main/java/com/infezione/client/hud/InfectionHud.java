package com.infezione.client.hud;

import com.infezione.InfezioneMod;
import com.infezione.client.ClientInfections;
import com.infezione.client.fx.ScreenShake;
import com.infezione.client.fx.SymptomFx;
import com.infezione.config.InfezioneConfig;
import com.infezione.infection.Change;
import com.infezione.infection.Stages;
import com.infezione.infection.Symptom;
import com.infezione.registry.ModSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * The infection on the infected player's screen: a panel at the top left with the stage, its name, the
 * five steps and the time left; veins and darkness creeping in from the edges, beating with the heart;
 * flashes, the blackouts, the zombie's grey sight; and big titles when the stage changes.
 */
public final class InfectionHud {
    private static final ResourceLocation VEINS = InfezioneMod.id("textures/gui/veins.png");
    /** The colour of each stage (0 unused): bone, fever, blood, dark blood, rot. */
    public static final int[] COLORS = {0xFFFFFF, 0xC9B8A0, 0xC98A5A, 0xB8323C, 0xA32035, 0x8A9A74};
    /** How strong the veins at the edges are at each stage, and how far in they reach (1 = all of them). */
    private static final float[] VEINS_ALPHA = {0.0F, 0.45F, 0.55F, 0.65F, 0.8F, 0.5F};
    private static final float[] VEINS_REACH = {0.0F, 0.45F, 0.62F, 0.8F, 1.0F, 0.9F};

    private static int flashColor;
    private static float flashAlpha;
    private static int flashTicks;
    private static int flashTotal = 1;

    private InfectionHud() {
    }

    /** The whole screen goes {@code color} for a moment. */
    public static void flash(int color, float alpha, int ticks) {
        flashColor = color;
        flashAlpha = alpha;
        flashTicks = ticks;
        flashTotal = ticks;
    }

    public static void tick() {
        if (flashTicks > 0) {
            flashTicks--;
        }
        GenomeBar.tick();
    }

    /** The local player's infection changed: say it big. */
    public static void onChange(int before, int stage, Change change) {
        Minecraft mc = Minecraft.getInstance();
        GenomeBar.glitch(20);
        if (change == Change.DIED) {
            return;
        }
        Component title;
        Component subtitle;
        if (stage == Stages.HEALTHY) {
            title = Component.translatable("infezione.title.cured").withStyle(style(0x9CC79C, true));
            subtitle = Component.translatable("infezione.subtitle.cured").withStyle(style(0xC8D8C8, false));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.CURE.get(), 1.0F, 1.0F));
            flash(0xD8FFD8, 0.45F, 24);
        } else if (stage == Stages.ZOMBIE) {
            title = Component.translatable("infezione.title.zombie").withStyle(style(COLORS[5], true));
            subtitle = Component.translatable("infezione.subtitle.zombie").withStyle(style(0xB8C8B0, false));
            flash(0x000000, 0.9F, 40);
            ScreenShake.add(6.0F, 40);
        } else {
            boolean bitten = before == Stages.HEALTHY;
            title = bitten ? Component.translatable("infezione.title.infected").withStyle(style(0x9E1010, true))
                    : Component.translatable("infezione.title.stage", stage).withStyle(style(COLORS[stage], true));
            subtitle = Component.translatable(bitten ? "infezione.subtitle.infected" : "infezione.subtitle." + stage)
                    .withStyle(style(0xC8B8B4, false));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.STAGE_UP.get(), 1.0F, 1.0F));
            flash(0x7A0000, 0.55F, 22);
            ScreenShake.add(2.0F + stage, 16);
        }
        mc.gui.setTimes(8, 60, 20);
        mc.gui.setSubtitle(subtitle);
        mc.gui.setTitle(title);
    }

    private static Style style(int color, boolean bold) {
        return Style.EMPTY.withColor(TextColor.fromRgb(color)).withBold(bold);
    }

    // =====================================================================================
    // Overlays
    // =====================================================================================

    /** Under everything: the veins at the edges, beating, and the zombie's grey sight. */
    public static void vignette(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        int stage = ClientInfections.SELF.stage;
        Minecraft mc = gui.getMinecraft();
        if (stage == Stages.HEALTHY || mc.player == null || !InfezioneConfig.CLIENT.vignette.get()) {
            return;
        }
        gui.setupOverlayRenderState(true, false);
        if (stage == Stages.ZOMBIE) {
            graphics.fill(0, 0, width, height, 0x2A1C3020);
        }
        float sinceBeat = ClientInfections.clock() - SymptomFx.lastBeat() + partialTick;
        float alpha;
        if (stage <= 2) {
            // the first two stages: the veins show only for the instant the heart beats (lub... dub), then they are gone
            float lub = (float) Math.exp(-Math.max(0.0F, sinceBeat) / 2.5F);
            float dub = sinceBeat >= 5.0F ? 0.6F * (float) Math.exp(-(sinceBeat - 5.0F) / 2.5F) : 0.0F;
            alpha = Mth.clamp(VEINS_ALPHA[stage] * 1.6F * Math.max(lub, dub), 0.0F, 1.0F);
            if (alpha < 0.01F) {
                return;
            }
        } else {
            float pulse = (float) Math.exp(-sinceBeat / 4.0F);
            alpha = Mth.clamp(VEINS_ALPHA[stage] * (0.8F + 0.45F * pulse), 0.0F, 1.0F);
        }
        if (stage == Stages.ZOMBIE) {
            RenderSystem.setShaderColor(0.55F, 0.7F, 0.5F, alpha);
        } else {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        }
        // the veins creep in from the edges, stage by stage: early on only their ends show, from outside the screen
        float scale = 1.0F / Mth.lerp(VEINS_REACH[stage], 0.62F, 1.0F);
        int w = Math.round(width * scale);
        int h = Math.round(height * scale);
        graphics.blit(VEINS, (width - w) / 2, (height - h) / 2, -90, 0.0F, 0.0F, w, h, w, h);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Over everything: flashes, the blackouts of the agony, the dark of the transformation. */
    public static void effects(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = gui.getMinecraft();
        if (mc.player == null) {
            return;
        }
        gui.setupOverlayRenderState(true, false);
        float dark = 0.0F;
        ClientInfections.Episode episode = ClientInfections.episode(mc.player);
        if (episode != null) {
            float t = episode.time(partialTick);
            float total = episode.symptom().ticks;
            if (episode.symptom() == Symptom.BLACKOUT) {
                dark = 0.94F * Math.min(Mth.clamp(t / 10.0F, 0.0F, 1.0F), Mth.clamp((total - t) / 16.0F, 0.0F, 1.0F));
            } else if (episode.symptom() == Symptom.CONVULSION) {
                dark = 0.25F * Math.abs(Mth.sin(t * 0.9F)) * Mth.clamp((total - t) / 8.0F, 0.0F, 1.0F);
            }
        }
        if (dark > 0.0F) {
            graphics.fill(0, 0, width, height, ((int) (dark * 255) << 24));
        }
        if (flashTicks > 0) {
            float k = (flashTicks - partialTick) / flashTotal;
            int a = (int) (Mth.clamp(flashAlpha * k, 0.0F, 1.0F) * 255);
            if (a > 0) {
                graphics.fill(0, 0, width, height, (a << 24) | flashColor);
            }
        }
    }

    /** The genome at the top left. */
    public static void panel(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        GenomeBar.render(gui, graphics, partialTick, width, height);
    }
}
