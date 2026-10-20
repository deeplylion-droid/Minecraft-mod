package com.filorosso.client.screen;

import com.filorosso.client.ClientState;
import com.filorosso.client.FiloRossoClient;
import com.filorosso.client.Me;
import com.filorosso.data.BondLevel;
import com.filorosso.data.BondType;
import com.filorosso.data.Link;
import com.filorosso.data.Palette;
import com.filorosso.net.Net;
import com.filorosso.server.Abilities;
import com.filorosso.util.Text;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

/** Your couple: level, fate of this live, abilities, challenges, score; and breaking up. */
public class CoupleScreen extends Screen {
    private static final int W = 300;
    private Button breakUp;
    private boolean confirming;
    private int top;

    public CoupleScreen() {
        super(Component.translatable("filorosso.couple.title"));
    }

    @Override
    protected void init() {
        int h = Math.min(this.height - 8, 236);
        this.top = Math.max(4, (this.height - h) / 2);
        int bottom = this.top + h - 24;
        this.breakUp = this.addRenderableWidget(Button.builder(Component.empty(), b -> this.breakUp())
                .bounds(this.width / 2 - W / 2 + 8, bottom, 140, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("filorosso.couple.close"), b -> this.onClose())
                .bounds(this.width / 2 + W / 2 - 108, bottom, 100, 20).build());
        this.updateButton();
    }

    private void updateButton() {
        this.breakUp.setMessage(Component.empty().append(Text.icon(Text.BROKEN, 0xB04050)).append(" ")
                .append(Component.translatable(this.confirming ? "filorosso.couple.break_up_sure" : "filorosso.couple.break_up")));
    }

    private void breakUp() {
        if (!this.confirming) {
            this.confirming = true;
            this.updateButton();
            return;
        }
        ClientPlayNetworking.send(Net.BreakUp.INSTANCE);
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
        Link link = Me.link();
        if (!link.bound() || Me.player() == null) {
            super.extractRenderState(g, mouseX, mouseY, a);
            return;
        }
        int x0 = this.width / 2 - W / 2;
        int h = Math.min(this.height - 8, 236);
        g.fill(x0, this.top, x0 + W, this.top + h, 0xD0100C12);
        g.outline(x0, this.top, W, h, 0xFF000000 | Palette.rgb(link.color()));
        int cx = this.width / 2;
        int y = this.top + 6;

        // the two of you
        Screens.face(g, Me.player().getUUID(), cx - 46, y, 24, 0xFFFFFFFF);
        Screens.face(g, link.partner().get(), cx + 22, y, 24, 0xFFFFFFFF);
        g.pose().pushMatrix();
        g.pose().translate(cx, y + 8);
        g.pose().scale(2.0F, 2.0F);
        g.centeredText(this.font, Text.heart(link.color()), 0, -4, 0xFFFFFFFF);
        g.pose().popMatrix();
        y += 28;
        g.centeredText(this.font, Text.names(Me.player().getGameProfile().name(), link.partnerName(), link.color()), cx, y, 0xFFFFFFFF);
        y += 14;

        // level
        BondLevel level = link.level();
        BondLevel next = level.next();
        g.text(this.font, Component.translatable("filorosso.couple.level", level.number(), level.displayName()), x0 + 8, y, 0xFF000000 | level.color);
        int barX = x0 + 150;
        int barW = W - 158;
        g.fill(barX, y + 1, barX + barW, y + 7, 0xFF301820);
        if (next != null) {
            float k = Mth.clamp((link.xp() - level.xp) / (float) (next.xp - level.xp), 0.0F, 1.0F);
            g.fill(barX, y + 1, barX + (int) (barW * k), y + 7, 0xFF000000 | level.color);
            small(g, Component.literal(link.xp() + " / " + next.xp), barX + barW / 2, y + 9, 0xFFA09098, true);
        } else {
            g.fill(barX, y + 1, barX + barW, y + 7, 0xFF000000 | level.color);
            small(g, Component.translatable("filorosso.couple.max"), barX + barW / 2, y + 9, 0xFFFFD070, true);
        }
        y += 18;
        if (next != null) {
            small(g, Component.translatable("filorosso.couple.next", next.number(), Component.translatable("filorosso.level.unlock." + next.number())),
                    x0 + 8, y, 0xFFB0A8B0, false);
            y += 10;
        }

        // the fate of this live
        BondType type = link.bondType();
        g.text(this.font, Component.translatable("filorosso.couple.fate").append(type.displayName().copy().withColor(type.color)), x0 + 8, y + 2, 0xFFD0D0D0);
        y += 13;
        for (FormattedCharSequence line : this.font.split(type.description(), (int) ((W - 16) / 0.75F))) {
            small(g, line, x0 + 8, y, 0xFFA0A0A0);
            y += 7;
        }
        y += 4;

        // abilities
        long now = Me.now();
        for (Abilities.Ability ability : Abilities.Ability.ALL) {
            int i = ability.ordinal();
            boolean unlocked = level.atLeast(ability.level);
            long ready = ClientState.readyAt(i);
            Component state;
            int color;
            if (!unlocked) {
                state = Component.translatable("filorosso.couple.ability.locked", ability.level.number());
                color = 0xFF606060;
            } else if (now < ready) {
                state = Component.translatable("filorosso.couple.ability.cooldown", (ready - now + 19) / 20);
                color = 0xFFC0A060;
            } else {
                state = Component.translatable("filorosso.couple.ability.ready");
                color = 0xFF70E070;
            }
            String id = ability.name().toLowerCase(Locale.ROOT);
            Component key = Component.literal("[").append(Screens.keyName(FiloRossoClient.ABILITY_KEYS[i])).append("] ");
            g.text(this.font, Component.empty().append(key).append(Component.translatable("filorosso.ability." + id)), x0 + 8, y,
                    unlocked ? 0xFFFFFFFF : 0xFF707070);
            small(g, state, x0 + W - 8 - (int) (this.font.width(state) * 0.75F), y + 1, color, false);
            y += 10;
        }
        y += 4;

        // challenges and score
        Net.CoupleInfo info = ClientState.info;
        g.text(this.font, Component.translatable("filorosso.couple.challenges"), x0 + 8, y, 0xFFFFD070);
        if (info.couples() > 0) {
            Component score = Component.translatable("filorosso.couple.score", info.points(), info.rank(), info.couples());
            g.text(this.font, score, x0 + W - 8 - this.font.width(score), y, 0xFFFFD070);
        }
        y += 11;
        for (int i = 0; i < info.ids().size(); i++) {
            String id = info.ids().get(i);
            int goal = i < info.goals().size() ? info.goals().get(i) : 1;
            int progress = i < info.progress().size() ? info.progress().get(i) : 0;
            int done = i < info.done().size() ? info.done().get(i) : 0;
            Component name = Component.translatable("filorosso.challenge." + id, goal);
            small(g, name, x0 + 10, y, done > 0 ? 0xFF80E080 : 0xFFE0E0E0, false);
            Component right = done > 0 ? Component.translatable("filorosso.couple.done", done) : Component.literal(Math.min(progress, goal) + "/" + goal);
            small(g, right, x0 + W - 8 - (int) (this.font.width(right) * 0.75F), y, done > 0 ? 0xFF80E080 : 0xFFB0B0B0, false);
            y += 9;
        }
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    private void small(GuiGraphicsExtractor g, Component text, int x, int y, int color, boolean centered) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(0.75F, 0.75F);
        if (centered) {
            g.centeredText(this.font, text, 0, 0, color);
        } else {
            g.text(this.font, text, 0, 0, color);
        }
        g.pose().popMatrix();
    }

    private void small(GuiGraphicsExtractor g, FormattedCharSequence text, int x, int y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(0.75F, 0.75F);
        g.text(this.font, text, 0, 0, color);
        g.pose().popMatrix();
    }

    static List<FormattedCharSequence> split(net.minecraft.client.gui.Font font, Component text, int width) {
        return font.split(text, width);
    }
}
