package com.filorosso.client.screen;

import com.filorosso.client.Me;
import com.filorosso.net.Net;
import com.filorosso.util.Text;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** "{name} wants to be tied to you by the red thread of fate": yes or no. Closing it means "I'll think about it". */
public class ProposalScreen extends Screen {
    private final Net.Proposal proposal;
    private int ticks;

    public ProposalScreen(Net.Proposal proposal) {
        super(Component.translatable("filorosso.proposal.title"));
        this.proposal = proposal;
    }

    @Override
    protected void init() {
        int y = this.height / 2 + 34;
        this.addRenderableWidget(Button.builder(Component.empty().append(Text.icon(Text.HEART, 0xFF3050)).append(" ")
                        .append(Component.translatable("filorosso.proposal.yes")), b -> this.answer(true))
                .bounds(this.width / 2 - 124, y, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("filorosso.proposal.no"), b -> this.answer(false))
                .bounds(this.width / 2 + 4, y, 120, 20).build());
    }

    private void answer(boolean yes) {
        ClientPlayNetworking.send(new Net.Answer(this.proposal.from(), yes));
        this.onClose();
    }

    @Override
    public void tick() {
        this.ticks++;
        if (Me.player() != null && Me.link().bound()) {
            this.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        float t = this.ticks + a;
        int cx = this.width / 2;
        int cy = this.height / 2;
        g.fill(cx - 140, cy - 70, cx + 140, cy + 62, 0xD0180810);
        g.outline(cx - 140, cy - 70, 280, 132, 0xFFFF5070);
        float beat = 1.0F + 0.12F * Math.max(0.0F, Mth.sin(t * 0.45F));
        g.pose().pushMatrix();
        g.pose().translate(cx, cy - 52);
        g.pose().scale(2.5F * beat, 2.5F * beat);
        g.centeredText(this.font, Text.icon(Text.HEART, 0xFF3050), 0, -4, 0xFFFFFFFF);
        g.pose().popMatrix();
        Screens.face(g, this.proposal.from(), cx - 16, cy - 34, 32, 0xFFFFFFFF);
        g.pose().pushMatrix();
        g.pose().translate(cx, cy + 3);
        g.pose().scale(1.3F, 1.3F);
        g.centeredText(this.font, Component.literal(this.proposal.name()), 0, 0, 0xFFFFFFFF);
        g.pose().popMatrix();
        g.centeredText(this.font, Component.translatable("filorosso.proposal.text"), cx, cy + 18, 0xFFFFB0C0);
        super.extractRenderState(g, mouseX, mouseY, a);
    }
}
