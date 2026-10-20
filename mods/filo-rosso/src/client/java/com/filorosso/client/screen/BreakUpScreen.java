package com.filorosso.client.screen;

import com.filorosso.client.Me;
import com.filorosso.net.Net;
import com.filorosso.util.Text;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The partner wants to break up: only if both agree the thread snaps. */
public class BreakUpScreen extends Screen {
    private final String name;

    public BreakUpScreen(String name) {
        super(Component.translatable("filorosso.breakup.title"));
        this.name = name;
    }

    @Override
    protected void init() {
        int y = this.height / 2 + 26;
        this.addRenderableWidget(Button.builder(Component.empty().append(Text.icon(Text.BROKEN, 0xB04050)).append(" ")
                        .append(Component.translatable("filorosso.breakup.yes")), b -> this.answer(true))
                .bounds(this.width / 2 - 124, y, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.empty().append(Text.icon(Text.HEART, 0xFF3050)).append(" ")
                        .append(Component.translatable("filorosso.breakup.no")), b -> this.answer(false))
                .bounds(this.width / 2 + 4, y, 120, 20).build());
    }

    private void answer(boolean yes) {
        ClientPlayNetworking.send(new Net.BreakUpAnswer(yes));
        this.onClose();
    }

    @Override
    public void tick() {
        if (Me.player() != null && !Me.link().bound()) {
            this.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        g.fill(cx - 140, cy - 50, cx + 140, cy + 54, 0xD0100810);
        g.outline(cx - 140, cy - 50, 280, 104, 0xFF904050);
        g.pose().pushMatrix();
        g.pose().translate(cx, cy - 36);
        g.pose().scale(2.0F, 2.0F);
        g.centeredText(this.font, Text.icon(Text.BROKEN, 0xB04050), 0, -4, 0xFFFFFFFF);
        g.pose().popMatrix();
        g.centeredText(this.font, Component.translatable("filorosso.breakup.text", this.name), cx, cy - 16, 0xFFFFFFFF);
        g.centeredText(this.font, Component.translatable("filorosso.breakup.text2"), cx, cy, 0xFFB0A0A8);
        super.extractRenderState(g, mouseX, mouseY, a);
    }
}
