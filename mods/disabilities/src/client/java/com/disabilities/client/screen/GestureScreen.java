package com.disabilities.client.screen;

import com.disabilities.Disabilities;
import com.disabilities.client.DisabilitiesClient;
import com.disabilities.gesture.Gesture;
import com.disabilities.net.Net;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Mute players' gesture wheel (G): pick a sign, everyone nearby sees it. Keys 1-8 work too. */
public class GestureScreen extends Screen {
    private static final int RADIUS = 62;

    public GestureScreen() {
        super(Component.translatable("disabilities.gestures.title"));
    }

    private int cx() {
        return this.width / 2;
    }

    private int cy() {
        return this.height / 2;
    }

    private static float angle(int index) {
        return (float) (index * Math.PI * 2.0 / Gesture.values().length - Math.PI / 2.0);
    }

    private Gesture at(double mx, double my) {
        double dx = mx - this.cx();
        double dy = my - this.cy();
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 18 || dist > RADIUS + 34) {
            return null;
        }
        double a = Math.atan2(dy, dx) + Math.PI / 2.0;
        int n = Gesture.values().length;
        int index = Math.floorMod((int) Math.round(a / (Math.PI * 2.0 / n)), n);
        return Gesture.byOrdinal(index);
    }

    private void make(Gesture gesture) {
        ClientPlayNetworking.send(new Net.MakeGesture(gesture.ordinal()));
        this.onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Gesture g = this.at(event.x(), event.y());
        if (g != null) {
            this.make(g);
            return true;
        }
        this.onClose();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key >= InputConstants.KEY_1 && key < InputConstants.KEY_1 + Gesture.values().length) {
            this.make(Gesture.byOrdinal(key - InputConstants.KEY_1));
            return true;
        }
        if (DisabilitiesClient.GESTURES.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
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
        Gesture hovered = this.at(mouseX, mouseY);
        int cx = this.cx();
        int cy = this.cy();
        g.centeredText(this.font, this.title, cx, cy - RADIUS - 42, 0xFFFFFFFF);
        g.pose().pushMatrix();
        g.pose().translate(cx, cy - RADIUS - 31);
        g.pose().scale(0.75F, 0.75F);
        g.centeredText(this.font, Component.translatable("disabilities.gestures.hint"), 0, 0, 0xFFA0A0A0);
        g.pose().popMatrix();
        Gesture[] all = Gesture.values();
        for (int i = 0; i < all.length; i++) {
            Gesture gesture = all[i];
            float ang = angle(i);
            int x = cx + Math.round(Mth.cos(ang) * RADIUS);
            int y = cy + Math.round(Mth.sin(ang) * RADIUS);
            boolean hover = gesture == hovered;
            int half = hover ? 20 : 17;
            g.fill(x - half, y - half, x + half, y + half, hover ? 0xD0405060 : 0xA0101418);
            g.outline(x - half, y - half, half * 2, half * 2, hover ? 0xFFFFFFFF : 0xFF5A6070);
            int size = hover ? 32 : 24;
            g.blit(RenderPipelines.GUI_TEXTURED, Disabilities.id("textures/gui/gestures/" + gesture.id() + ".png"), x - size / 2, y - size / 2 - 3,
                    0, 0, size, size, 16, 16, 16, 16);
            g.text(this.font, Component.literal(String.valueOf(i + 1)), x - half + 2, y - half + 2, 0xFFB0B0B0);
            g.pose().pushMatrix();
            g.pose().translate(x, y + half - 8);
            g.pose().scale(0.75F, 0.75F);
            g.centeredText(this.font, gesture.displayName(), 0, 0, hover ? 0xFFFFFF80 : 0xFFE0E0E0);
            g.pose().popMatrix();
        }
        if (hovered != null) {
            g.centeredText(this.font, hovered.displayName(), cx, cy - 4, 0xFFFFFF80);
        }
        super.extractRenderState(g, mouseX, mouseY, a);
    }
}
