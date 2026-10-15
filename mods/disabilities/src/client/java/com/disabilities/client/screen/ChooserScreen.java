package com.disabilities.client.screen;

import com.disabilities.Disabilities;
import com.disabilities.client.Me;
import com.disabilities.condition.Condition;
import com.disabilities.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The mandatory choice: 21 cards, pick two that go together and that nobody else has. Cannot be
 * closed: it comes back until the server confirms.
 */
public class ChooserScreen extends Screen {
    private static final int COLUMNS = 7;

    private int taken;
    private final List<Condition> selected = new ArrayList<>();
    private boolean waiting;
    private Button confirm;
    private Button random;
    private Condition shown;

    // layout
    private int cardW;
    private int cardH;
    private int gridX;
    private int gridY;
    private int infoY;

    public ChooserScreen(int taken) {
        super(Component.translatable("disabilities.chooser.title"));
        this.taken = taken;
    }

    /** The server refused (someone took one of them meanwhile): new list, choose again. */
    public void update(int taken) {
        this.taken = taken;
        this.waiting = false;
        this.selected.removeIf(c -> (taken & c.bit()) != 0);
        this.updateButtons();
    }

    public static Identifier icon(Condition c) {
        return Disabilities.id("textures/gui/icons/" + c.id() + ".png");
    }

    @Override
    protected void init() {
        this.cardW = Math.max(46, Math.min(72, (this.width - 16) / COLUMNS));
        this.cardH = Math.max(40, Math.min(50, (this.height - 128) / 3));
        int gridW = this.cardW * COLUMNS;
        this.gridX = (this.width - gridW) / 2;
        this.gridY = 34;
        this.infoY = this.gridY + this.cardH * 3 + 6;
        int buttonY = this.height - 26;
        this.random = this.addRenderableWidget(Button.builder(Component.translatable("disabilities.chooser.random"), b -> this.pickRandom())
                .bounds(this.width / 2 - 154, buttonY, 100, 20).build());
        this.confirm = this.addRenderableWidget(Button.builder(Component.empty(), b -> this.confirm())
                .bounds(this.width / 2 - 50, buttonY, 204, 20).build());
        this.updateButtons();
    }

    private void updateButtons() {
        if (this.confirm == null) {
            return;
        }
        boolean ready = this.selected.size() == Condition.PER_PLAYER;
        this.confirm.active = ready && !this.waiting;
        this.random.active = !this.waiting;
        this.confirm.setMessage(this.waiting ? Component.translatable("disabilities.chooser.waiting")
                : Component.translatable("disabilities.chooser.confirm", this.selected.size(), Condition.PER_PLAYER));
    }

    private boolean blockedBySelection(Condition c) {
        for (Condition s : this.selected) {
            if (s != c && !s.compatibleWith(c)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTaken(Condition c) {
        return (this.taken & c.bit()) != 0;
    }

    private void toggle(Condition c) {
        if (this.waiting || this.isTaken(c)) {
            return;
        }
        if (this.selected.remove(c)) {
            this.updateButtons();
            return;
        }
        this.selected.removeIf(s -> !s.compatibleWith(c));
        while (this.selected.size() >= Condition.PER_PLAYER) {
            this.selected.remove(0);
        }
        this.selected.add(c);
        this.shown = c;
        this.updateButtons();
    }

    private void pickRandom() {
        List<Integer> pairs = new ArrayList<>();
        for (int pair : Condition.validPairs()) {
            if ((pair & this.taken) == 0) {
                pairs.add(pair);
            }
        }
        if (pairs.isEmpty()) {
            return;
        }
        int pair = pairs.get(new Random().nextInt(pairs.size()));
        this.selected.clear();
        for (Condition c : Condition.values()) {
            if ((pair & c.bit()) != 0) {
                this.selected.add(c);
            }
        }
        this.shown = this.selected.get(0);
        this.updateButtons();
    }

    private void confirm() {
        if (this.selected.size() != Condition.PER_PLAYER || this.waiting) {
            return;
        }
        int mask = 0;
        for (Condition c : this.selected) {
            mask |= c.bit();
        }
        this.waiting = true;
        this.updateButtons();
        ClientPlayNetworking.send(new Net.Choose(mask));
    }

    @Override
    public void tick() {
        if (Me.player() != null && Me.picks().settled()) {
            this.minecraft.gui.setScreen(null);
        }
    }

    private Condition cardAt(double mx, double my) {
        if (mx < this.gridX || my < this.gridY) {
            return null;
        }
        int col = (int) ((mx - this.gridX) / this.cardW);
        int row = (int) ((my - this.gridY) / this.cardH);
        if (col >= COLUMNS || row >= 3) {
            return null;
        }
        return Condition.byOrdinal(row * COLUMNS + col);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Condition c = this.cardAt(event.x(), event.y());
        if (c != null && event.button() == 0) {
            this.toggle(c);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            return true;
        }
        if (event.isConfirmation() && this.confirm.active) {
            this.confirm();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        g.centeredText(this.font, Component.translatable("disabilities.chooser.subtitle"), this.width / 2, 20, 0xFFB0B0B0);

        Condition hovered = this.cardAt(mouseX, mouseY);
        for (Condition c : Condition.values()) {
            int col = c.ordinal() % COLUMNS;
            int row = c.ordinal() / COLUMNS;
            int x = this.gridX + col * this.cardW;
            int y = this.gridY + row * this.cardH;
            this.drawCard(g, c, x + 1, y + 1, this.cardW - 2, this.cardH - 2, c == hovered);
        }

        Condition info = hovered != null ? hovered : this.shown;
        if (info != null) {
            this.drawInfo(g, info);
        } else {
            g.centeredText(this.font, Component.translatable("disabilities.chooser.hint"), this.width / 2, this.infoY + 14, 0xFF909090);
        }
        if (hovered != null && this.isTaken(hovered)) {
            g.setTooltipForNextFrame(this.font, Component.translatable("disabilities.chooser.taken_tip"), mouseX, mouseY);
        } else if (hovered != null && this.blockedBySelection(hovered) && !this.selected.contains(hovered)) {
            Condition other = this.selected.stream().filter(s -> !s.compatibleWith(hovered)).findFirst().orElse(null);
            if (other != null) {
                g.setTooltipForNextFrame(this.font, Component.translatable("disabilities.chooser.incompatible", other.displayName()), mouseX, mouseY);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    private void drawCard(GuiGraphicsExtractor g, Condition c, int x, int y, int w, int h, boolean hover) {
        boolean selected = this.selected.contains(c);
        boolean taken = this.isTaken(c);
        boolean blocked = !selected && this.blockedBySelection(c);
        int color = 0xFF000000 | c.color;
        int bg = selected ? ARGB.color(150, ARGB.scaleRGB(color, 0.35F)) : hover && !taken ? 0xB0303038 : 0x90101014;
        g.fill(x, y, x + w, y + h, bg);
        if (selected) {
            g.outline(x, y, w, h, color);
            g.outline(x + 1, y + 1, w - 2, h - 2, color);
        } else if (hover && !taken) {
            g.outline(x, y, w, h, 0xFFA0A0A0);
        } else {
            g.outline(x, y, w, h, 0xFF2A2A30);
        }
        float alpha = taken ? 0.25F : blocked ? 0.45F : 1.0F;
        int iconSize = h >= 46 ? 32 : 24;
        int ix = x + (w - iconSize) / 2;
        int iy = y + 3;
        g.blit(RenderPipelines.GUI_TEXTURED, icon(c), ix, iy, 0, 0, iconSize, iconSize, 16, 16, 16, 16, ARGB.white(alpha));
        Component name = Component.translatable("disabilities.condition." + c.id() + ".short");
        int textColor = taken ? 0xFF606060 : blocked ? 0xFF808080 : selected ? 0xFFFFFFFF : 0xFFD8D8D8;
        float scale = this.font.width(name) * 0.75F > w - 4 ? (w - 4) / (float) this.font.width(name) : 0.75F;
        g.pose().pushMatrix();
        g.pose().translate(x + w / 2.0F, y + h - 4 - 9 * scale);
        g.pose().scale(scale, scale);
        g.centeredText(this.font, name, 0, 0, textColor);
        g.pose().popMatrix();
        if (taken) {
            Component label = Component.translatable("disabilities.chooser.taken_label");
            g.pose().pushMatrix();
            g.pose().translate(x + w / 2.0F, y + iy - y + iconSize / 2.0F - 3);
            g.pose().scale(0.75F, 0.75F);
            g.centeredText(this.font, label, 0, 0, 0xFFFF5050);
            g.pose().popMatrix();
        }
        if (selected) {
            int index = this.selected.indexOf(c) + 1;
            g.text(this.font, Component.literal(String.valueOf(index)), x + 3, y + 3, color);
        }
    }

    private void drawInfo(GuiGraphicsExtractor g, Condition c) {
        int x = Math.max(8, this.gridX);
        int w = Math.min(this.width - 16, this.cardW * COLUMNS);
        int bottom = this.height - 32;
        g.fill(x, this.infoY, x + w, bottom, 0xA0000000);
        g.outline(x, this.infoY, w, bottom - this.infoY, 0xFF000000 | c.color);
        g.blit(RenderPipelines.GUI_TEXTURED, icon(c), x + 4, this.infoY + 4, 0, 0, 16, 16, 16, 16);
        g.text(this.font, c.displayName(), x + 24, this.infoY + 8, 0xFF000000 | c.color);
        int y = this.infoY + 24;
        int textW = w - 12;
        Component you = Component.translatable("disabilities.chooser.you").append(c.forYou());
        Component others = Component.translatable("disabilities.chooser.others").append(c.forOthers());
        for (FormattedCharSequence line : this.font.split(you, textW)) {
            if (y + 9 > bottom - 2) {
                return;
            }
            g.text(this.font, line, x + 6, y, 0xFFE0E0E0);
            y += 10;
        }
        for (FormattedCharSequence line : this.font.split(others, textW)) {
            if (y + 9 > bottom - 2) {
                return;
            }
            g.text(this.font, line, x + 6, y, 0xFF9AA0A8);
            y += 10;
        }
    }
}
