package com.filorosso.client.screen;

import com.filorosso.client.ClientState;
import com.filorosso.client.Me;
import com.filorosso.net.Net;
import com.filorosso.util.Text;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * "Il filo rosso del destino": everybody online, choose who to tie yourself to. If they choose you
 * too (or say yes) the thread ties you at once. It can be closed and opened again with the menu key.
 */
public class ChoiceScreen extends Screen {
    private static final int CARD_W = 92;
    private static final int CARD_H = 66;

    private int columns;
    private int gridX;
    private int gridY;
    private int ticks;

    public ChoiceScreen() {
        super(Component.translatable("filorosso.choice.title"));
    }

    public void refresh() {
        this.init(this.width, this.height);
    }

    private List<Net.Candidate> list() {
        return ClientState.candidates;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int n = Math.max(1, this.list().size());
        this.columns = Mth.clamp((this.width - 20) / CARD_W, 1, 4);
        this.columns = Math.min(this.columns, n);
        int rows = (n + this.columns - 1) / this.columns;
        this.gridX = (this.width - this.columns * CARD_W) / 2;
        int gridH = rows * CARD_H;
        this.gridY = Math.max(44, (this.height - gridH) / 2 - 6);
        this.addRenderableWidget(Button.builder(Component.translatable("filorosso.choice.later"), b -> this.onClose())
                .bounds(this.width / 2 - 60, this.height - 28, 120, 20).build());
    }

    @Override
    public void tick() {
        this.ticks++;
        if (Me.player() != null && Me.link().bound()) {
            this.minecraft.gui.setScreen(null);
        }
    }

    private int cardAt(double mx, double my) {
        if (mx < this.gridX || my < this.gridY) {
            return -1;
        }
        int col = (int) ((mx - this.gridX) / CARD_W);
        int row = (int) ((my - this.gridY) / CARD_H);
        if (col >= this.columns) {
            return -1;
        }
        int i = row * this.columns + col;
        return i < this.list().size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int i = this.cardAt(event.x(), event.y());
        if (i >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            Net.Candidate c = this.list().get(i);
            if (c.status() == Net.Candidate.FREE || c.status() == Net.Candidate.PROPOSED_TO_YOU) {
                ClientPlayNetworking.send(new Net.Propose(c.id()));
                Screens.click();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        float t = this.ticks + a;
        Component heart = Text.icon(Text.HEART, 0xFF3050);
        Component title = Component.empty().append(heart).append("  ").append(this.title).append("  ").append(heart);
        g.pose().pushMatrix();
        g.pose().translate(this.width / 2.0F, 10.0F);
        g.pose().scale(1.5F, 1.5F);
        g.centeredText(this.font, title, 0, 0, 0xFFFF6080);
        g.pose().popMatrix();
        g.centeredText(this.font, Component.translatable("filorosso.choice.subtitle"), this.width / 2, 27, 0xFFD0D0D0);

        List<Net.Candidate> list = this.list();
        if (list.isEmpty()) {
            g.centeredText(this.font, Component.translatable("filorosso.choice.empty"), this.width / 2, this.height / 2 - 4, 0xFFB0B0B0);
        }
        int hovered = this.cardAt(mouseX, mouseY);
        for (int i = 0; i < list.size(); i++) {
            int x = this.gridX + (i % this.columns) * CARD_W;
            int y = this.gridY + (i / this.columns) * CARD_H;
            this.drawCard(g, list.get(i), x + 2, y + 2, CARD_W - 4, CARD_H - 4, i == hovered, t);
        }
        g.centeredText(this.font, Component.translatable("filorosso.choice.hint", Screens.keyName(com.filorosso.client.FiloRossoClient.MENU)),
                this.width / 2, this.height - 40, 0xFF808080);
        if (hovered >= 0) {
            Net.Candidate c = list.get(hovered);
            Component tip = switch (c.status()) {
                case Net.Candidate.FREE -> Component.translatable("filorosso.choice.tip.free", c.name());
                case Net.Candidate.PROPOSED_TO_YOU -> Component.translatable("filorosso.choice.tip.accept", c.name());
                case Net.Candidate.YOU_PROPOSED -> Component.translatable("filorosso.choice.tip.waiting", c.name());
                default -> Component.translatable("filorosso.choice.tip.bound", c.name(), c.other());
            };
            g.setTooltipForNextFrame(this.font, tip, mouseX, mouseY);
        }
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    private void drawCard(GuiGraphicsExtractor g, Net.Candidate c, int x, int y, int w, int h, boolean hover, float t) {
        boolean clickable = c.status() == Net.Candidate.FREE || c.status() == Net.Candidate.PROPOSED_TO_YOU;
        float pulse = 0.5F + 0.5F * Mth.sin(t * 0.25F);
        int border;
        int bg;
        switch (c.status()) {
            case Net.Candidate.PROPOSED_TO_YOU -> {
                border = ARGB.srgbLerp(pulse, 0xFFFF3050, 0xFFFFA0C0);
                bg = 0xC0401020;
            }
            case Net.Candidate.YOU_PROPOSED -> {
                border = 0xFFFF80A0;
                bg = 0xB0301018;
            }
            case Net.Candidate.BOUND -> {
                border = 0xFF303034;
                bg = 0x90101012;
            }
            default -> {
                border = hover ? 0xFFFF6080 : 0xFF505058;
                bg = hover ? 0xC0281820 : 0xA0141418;
            }
        }
        g.fill(x, y, x + w, y + h, bg);
        g.outline(x, y, w, h, border);
        if (hover && clickable) {
            g.outline(x + 1, y + 1, w - 2, h - 2, border);
        }
        int face = 28;
        int color = c.status() == Net.Candidate.BOUND ? ARGB.white(0.35F) : 0xFFFFFFFF;
        Screens.face(g, c.id(), x + (w - face) / 2, y + 5, face, color);
        Component name = Component.literal(c.name());
        float scale = this.font.width(name) > w - 6 ? (w - 6) / (float) this.font.width(name) : 1.0F;
        g.pose().pushMatrix();
        g.pose().translate(x + w / 2.0F, y + 37);
        g.pose().scale(scale, scale);
        g.centeredText(this.font, name, 0, 0, c.status() == Net.Candidate.BOUND ? 0xFF707070 : 0xFFFFFFFF);
        g.pose().popMatrix();
        Component status = switch (c.status()) {
            case Net.Candidate.PROPOSED_TO_YOU -> Component.empty().append(Text.icon(Text.HEART, 0xFF3050)).append(" ")
                    .append(Component.translatable("filorosso.choice.status.chose_you"));
            case Net.Candidate.YOU_PROPOSED -> Component.translatable("filorosso.choice.status.waiting");
            case Net.Candidate.BOUND -> Component.translatable("filorosso.choice.status.bound", c.other());
            default -> Component.translatable("filorosso.choice.status.free");
        };
        int statusColor = switch (c.status()) {
            case Net.Candidate.PROPOSED_TO_YOU -> 0xFFFF90B0;
            case Net.Candidate.YOU_PROPOSED -> 0xFFFFC0D0;
            case Net.Candidate.BOUND -> 0xFF606060;
            default -> 0xFF90E090;
        };
        float s2 = this.font.width(status) * 0.75F > w - 4 ? (w - 4) / (float) this.font.width(status) : 0.75F;
        g.pose().pushMatrix();
        g.pose().translate(x + w / 2.0F, y + 50);
        g.pose().scale(s2, s2);
        g.centeredText(this.font, status, 0, 0, statusColor);
        g.pose().popMatrix();
    }
}
