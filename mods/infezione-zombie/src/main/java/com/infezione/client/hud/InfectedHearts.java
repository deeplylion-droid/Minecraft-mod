package com.infezione.client.hud;

import com.infezione.InfezioneMod;
import com.infezione.client.ClientInfections;
import com.infezione.config.InfezioneConfig;
import com.infezione.infection.Stages;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * The health bar of an infected player: the same as vanilla's (rows, absorption, blinking, the shaking
 * when low, the wave of Regeneration), but with the hearts of the stage, rotting a little more each time.
 * Poison, wither and freezing keep vanilla's hearts.
 */
public final class InfectedHearts {
    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    private static final ResourceLocation HEARTS = InfezioneMod.id("textures/gui/hearts.png");
    private static final RandomSource RANDOM = RandomSource.create();

    private static int lastHealth;
    private static int displayHealth;
    private static long lastHealthTime;
    private static long healthBlinkTime;

    private InfectedHearts() {
    }

    /** True when the infected hearts are drawn instead of vanilla's. */
    public static boolean replaces(Player player) {
        return ClientInfections.SELF.stage > Stages.HEALTHY && InfezioneConfig.CLIENT.hearts.get()
                && !player.hasEffect(MobEffects.POISON) && !player.hasEffect(MobEffects.WITHER) && !player.isFullyFrozen();
    }

    /** {@code ForgeGui.renderHealth}, with the infected hearts. */
    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = gui.getMinecraft();
        if (mc.options.hideGui || !gui.shouldDrawSurvivalElements() || !(mc.getCameraEntity() instanceof Player player)
                || !replaces(player)) {
            return;
        }
        gui.setupOverlayRenderState(true, false);
        int tickCount = gui.getGuiTicks();
        int health = Mth.ceil(player.getHealth());
        boolean blink = healthBlinkTime > tickCount && (healthBlinkTime - tickCount) / 3L % 2L == 1L;
        if (health < lastHealth && player.invulnerableTime > 0) {
            lastHealthTime = Util.getMillis();
            healthBlinkTime = tickCount + 20;
        } else if (health > lastHealth && player.invulnerableTime > 0) {
            lastHealthTime = Util.getMillis();
            healthBlinkTime = tickCount + 10;
        }
        if (Util.getMillis() - lastHealthTime > 1000L) {
            lastHealth = health;
            displayHealth = health;
            lastHealthTime = Util.getMillis();
        }
        lastHealth = health;
        int shown = displayHealth;
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        float max = Math.max((float) maxHealth.getValue(), (float) Math.max(shown, health));
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        int rows = Mth.ceil((max + absorption) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (rows - 2), 3);
        RANDOM.setSeed(tickCount * 312871L);
        int left = width / 2 - 91;
        int top = height - gui.leftHeight;
        gui.leftHeight += rows * rowHeight;
        if (rowHeight != 10) {
            gui.leftHeight += 10 - rowHeight;
        }
        int regen = -1;
        if (player.hasEffect(MobEffects.REGENERATION)) {
            regen = tickCount % Mth.ceil(max + 5.0F);
        }
        hearts(graphics, player, left, top, rowHeight, regen, max, health, shown, absorption, blink, tickCount);
        RenderSystem.disableBlend();
    }

    /** {@code Gui.renderHearts}: containers, absorption, the blinking of the last change, the hearts. */
    private static void hearts(GuiGraphics graphics, Player player, int x, int y, int rowHeight, int regen, float max,
                               int health, int shown, int absorption, boolean blink, int tickCount) {
        int stage = ClientInfections.SELF.stage;
        int hardcore = player.level().getLevelData().isHardcore() ? 45 : 0;
        int hearts = Mth.ceil(max / 2.0);
        int extra = Mth.ceil(absorption / 2.0);
        int maxHalves = hearts * 2;
        for (int i = hearts + extra - 1; i >= 0; i--) {
            int row = i / 10;
            int column = i % 10;
            int hx = x + column * 8;
            int hy = y - row * rowHeight;
            if (health + absorption <= 4) {
                hy += RANDOM.nextInt(2);
            }
            if (i < hearts && i == regen) {
                hy -= 2;
            }
            // the zombie's heart barely moves: a slow twitch now and then, one heart at a time
            if (stage == Stages.ZOMBIE && (tickCount / 4 + i * 7) % 53 == 0) {
                hy += 1;
            }
            graphics.blit(ICONS, hx, hy, 16 + (blink ? 9 : 0), hardcore, 9, 9);
            int half = i * 2;
            if (i >= hearts) {
                int a = half - maxHalves;
                if (a < absorption) {
                    graphics.blit(ICONS, hx, hy, 16 + 8 * 2 * 9 + (a + 1 == absorption ? 9 : 0), hardcore, 9, 9);
                }
            }
            if (blink && half < shown) {
                heart(graphics, hx, hy, stage, true, half + 1 == shown);
            }
            if (half < health) {
                heart(graphics, hx, hy, stage, false, half + 1 == health);
            }
        }
    }

    /** One heart of {@code hearts.png}: a row per stage; full, half, and the same blinking. */
    private static void heart(GuiGraphics graphics, int x, int y, int stage, boolean blinking, boolean half) {
        int u = (half ? 9 : 0) + (blinking ? 18 : 0);
        int v = (Mth.clamp(stage, 1, Stages.ZOMBIE) - 1) * 9;
        graphics.blit(HEARTS, x, y, u, v, 9, 9, 64, 64);
    }
}
