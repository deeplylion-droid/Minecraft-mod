package com.filorosso.client.screen;

import com.filorosso.FiloRosso;
import com.filorosso.client.ClientState;
import com.filorosso.client.FiloRossoClient;
import com.filorosso.client.Me;
import com.filorosso.data.BondLevel;
import com.filorosso.data.Link;
import com.filorosso.data.Palette;
import com.filorosso.server.Abilities;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * The wheel of the bond (B): every power of the couple in a circle. Hold B, point at one and let go;
 * or click it; or press its number. Locked powers show the level they need.
 */
public class WheelScreen extends Screen {
    private static final int RADIUS = 70;
    private final long openedAt = System.currentTimeMillis();
    private int ticks;
    /**
     * B was let go since the wheel opened. While it is held the system repeats the key press: those
     * repeats must not close the wheel (they did, and it flickered open and shut under the finger).
     */
    private boolean letGo;

    public WheelScreen() {
        super(Component.translatable("filorosso.wheel.title"));
        // opened by a quick tap, B is already up when the wheel appears
        this.letGo = !FiloRossoClient.WHEEL.isDown();
    }

    private int cx() {
        return this.width / 2;
    }

    private int cy() {
        return this.height / 2;
    }

    private static int count() {
        return Abilities.Ability.ALL.length;
    }

    private static float angle(int index) {
        return (float) (index * Math.PI * 2.0 / count() - Math.PI / 2.0);
    }

    private Abilities.Ability at(double mx, double my) {
        double dx = mx - this.cx();
        double dy = my - this.cy();
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 22 || dist > RADIUS + 40) {
            return null;
        }
        double a = Math.atan2(dy, dx) + Math.PI / 2.0;
        int index = Math.floorMod((int) Math.round(a / (Math.PI * 2.0 / count())), count());
        return Abilities.Ability.ALL[index];
    }

    private void use(Abilities.Ability ability) {
        FiloRossoClient.useAbility(ability.ordinal());
        this.onClose();
    }

    @Override
    public void tick() {
        this.ticks++;
        if (Me.player() != null && !Me.link().bound()) {
            this.onClose();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Abilities.Ability ability = this.at(event.x(), event.y());
        if (ability != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.use(ability);
            return true;
        }
        this.onClose();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key >= InputConstants.KEY_1 && key < InputConstants.KEY_1 + count()) {
            this.use(Abilities.Ability.ALL[key - InputConstants.KEY_1]);
            return true;
        }
        if (FiloRossoClient.WHEEL.matches(event)) {
            // B again closes it; B held down (repeating) does nothing
            if (this.letGo) {
                this.onClose();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        // held B and let go on a power: use it; a quick tap leaves the wheel open for a click.
        // Never "consumed": vanilla must still see B go up, or the key would stay down.
        if (FiloRossoClient.WHEEL.matches(event) && this.minecraft != null && !this.letGo) {
            this.letGo = true;
            double mx = this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
            double my = this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
            Abilities.Ability ability = this.at(mx, my);
            if (ability != null) {
                this.use(ability);
            } else if (System.currentTimeMillis() - this.openedAt > 350) {
                this.onClose();
            }
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        Link link = Me.link();
        BondLevel level = link.level();
        Abilities.Ability hovered = this.at(mouseX, mouseY);
        int cx = this.cx();
        int cy = this.cy();
        long now = Me.now();
        float t = this.ticks + a;
        int couple = 0xFF000000 | Palette.rgb(link.color());

        // the thread that goes round the wheel
        int seg = 72;
        for (int i = 0; i < seg; i++) {
            float ang = (float) (i * Math.PI * 2.0 / seg);
            int x = cx + Math.round(Mth.cos(ang) * (RADIUS + 1));
            int y = cy + Math.round(Mth.sin(ang) * (RADIUS + 1));
            float glow = 0.5F + 0.5F * Mth.sin(t * 0.15F - i * 0.35F);
            g.fill(x - 1, y - 1, x + 1, y + 1, ARGB.srgbLerp(glow * 0.6F, couple, 0xFFFFFFFF));
        }
        g.centeredText(this.font, this.title, cx, cy - RADIUS - 46, 0xFFFFFFFF);
        g.pose().pushMatrix();
        g.pose().translate(cx, cy - RADIUS - 35);
        g.pose().scale(0.75F, 0.75F);
        g.centeredText(this.font, Component.translatable("filorosso.wheel.hint", FiloRossoClient.WHEEL.getTranslatedKeyMessage()), 0, 0, 0xFFA0A0A0);
        g.pose().popMatrix();

        for (Abilities.Ability ability : Abilities.Ability.ALL) {
            int i = ability.ordinal();
            float ang = angle(i);
            int x = cx + Math.round(Mth.cos(ang) * RADIUS);
            int y = cy + Math.round(Mth.sin(ang) * RADIUS);
            boolean unlocked = level.atLeast(ability.level);
            long ready = ClientState.readyAt(i);
            boolean cooling = unlocked && now < ready;
            boolean hover = ability == hovered;
            int half = hover ? 21 : 18;
            g.fill(x - half, y - half, x + half, y + half, hover ? 0xD0402030 : 0xB0100810);
            g.outline(x - half, y - half, half * 2, half * 2, hover ? 0xFFFFFFFF : unlocked ? couple : 0xFF404040);
            int size = hover ? 28 : 22;
            int tint = !unlocked ? 0xFF505050 : cooling ? 0xFF909090 : 0xFFFFFFFF;
            g.blit(RenderPipelines.GUI_TEXTURED, FiloRosso.id("textures/gui/ability/" + ability.name().toLowerCase(Locale.ROOT) + ".png"),
                    x - size / 2, y - size / 2 - 3, 0, 0, size, size, 16, 16, 16, 16, tint);
            if (cooling) {
                float k = Mth.clamp((ready - now) / (float) ability.cooldown, 0.0F, 1.0F);
                g.fill(x - half + 1, y + half - 1 - (int) ((half * 2 - 2) * k), x + half - 1, y + half - 1, 0x80000000);
            }
            g.text(this.font, Component.literal(String.valueOf(i + 1)), x - half + 2, y - half + 2, 0xFFB0B0B0);
            Component label = !unlocked ? Component.translatable("filorosso.hud.locked", ability.level.number())
                    : cooling ? Component.literal(((ready - now + 19) / 20) + "s")
                    : Component.translatable("filorosso.ability." + ability.name().toLowerCase(Locale.ROOT) + ".short");
            g.pose().pushMatrix();
            g.pose().translate(x, y + half - 8);
            g.pose().scale(0.7F, 0.7F);
            g.centeredText(this.font, label, 0, 0, !unlocked ? 0xFF707070 : cooling ? 0xFFC0A060 : hover ? 0xFFFFFF80 : 0xFFE0E0E0);
            g.pose().popMatrix();
        }
        if (hovered != null) {
            String id = hovered.name().toLowerCase(Locale.ROOT);
            boolean unlocked = level.atLeast(hovered.level);
            g.centeredText(this.font, Component.translatable("filorosso.ability." + id), cx, cy - 10, unlocked ? 0xFFFFFF80 : 0xFF909090);
            int y = cy + 2;
            for (var line : this.font.split(Component.translatable("filorosso.ability." + id + ".desc"), 150)) {
                g.pose().pushMatrix();
                g.pose().translate(cx, y);
                g.pose().scale(0.7F, 0.7F);
                g.centeredText(this.font, line, 0, 0, 0xFFC8C0C8);
                g.pose().popMatrix();
                y += 7;
            }
        } else {
            // two short lines: the ring is narrow in the middle
            g.centeredText(this.font, Component.translatable("filorosso.wheel.level", level.number()), cx, cy - 10, 0xFF000000 | level.color);
            g.centeredText(this.font, level.displayName(), cx, cy + 1, 0xFF000000 | level.color);
        }
        super.extractRenderState(g, mouseX, mouseY, a);
    }
}
