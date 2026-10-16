package com.filorosso.client.hud;

import com.filorosso.client.ClientState;
import com.filorosso.client.FiloRossoClient;
import com.filorosso.client.Me;
import com.filorosso.client.ThreadPhysics;
import com.filorosso.client.screen.Screens;
import com.filorosso.data.BondLevel;
import com.filorosso.data.Link;
import com.filorosso.data.ModData;
import com.filorosso.data.Palette;
import com.filorosso.data.Status;
import com.filorosso.net.Net;
import com.filorosso.server.Abilities;
import com.filorosso.server.Events;
import com.filorosso.util.Text;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * The couple's corner of the screen: your partner (face, health, how far, which way), the bond
 * level, the abilities with their keys, the challenges, the event going on, and what to do when one
 * of you is on the ground or hanging from the thread.
 */
public final class CoupleHud {
    private CoupleHud() {
    }

    private static int ticks;
    private static int singleHint;

    public static void reset() {
        singleHint = 0;
    }

    public static void tick(Minecraft mc) {
        ticks++;
        if (singleHint > 0) {
            singleHint--;
        }
    }

    /** An ability key was pressed without a partner. */
    public static void hintSingle() {
        singleHint = 60;
    }

    private static void small(GuiGraphicsExtractor g, Font font, Component text, float x, float y, int color, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, color);
        g.pose().popMatrix();
    }

    private static void centered(GuiGraphicsExtractor g, Font font, Component text, float x, float y, int color, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.centeredText(font, text, 0, 0, color);
        g.pose().popMatrix();
    }

    private static String time(long ticks) {
        long s = Math.max(0, (ticks + 19) / 20);
        return s >= 60 ? String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60) : s + "s";
    }

    /** Drawn after the vanilla HUD (and also with F1, for the things that matter: being on the ground). */
    public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float t = ticks + partial;
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        long now = me.level().getGameTime();
        Link link = Me.link();
        Status status = Me.status();
        Player partner = Me.partner();

        if (status.downed()) {
            downed(g, font, w, h, t, now, status, link);
        } else if (partner != null && ModData.downed(partner)) {
            partnerDowned(g, font, w, h, t, now, me, partner, partial);
        }

        if (mc.gui.hud.isHidden() || mc.debugEntries.isOverlayVisible() || me.isSpectator()) {
            return;
        }
        int y = 4;
        if (link.bound()) {
            y = panel(g, font, me, link, partner, now, t, partial);
            y = abilities(g, font, link, now, y + 2);
            y = challenges(g, font, y + 3);
        } else {
            Component hint = Component.translatable("filorosso.hud.single", Screens.keyName(FiloRossoClient.MENU));
            float blink = singleHint > 0 ? 0.5F + 0.5F * Mth.sin(t * 0.6F) : 0.0F;
            g.fill(2, 2, 10 + font.width(hint) + 12, 16, 0x90000000);
            g.text(font, Text.icon(Text.HEART, 0xFF3050), 5, 5, 0xFFFFFFFF);
            g.text(font, hint, 17, 5, ARGB.srgbLerp(blink, 0xFFFFC0D0, 0xFFFFFFFF));
        }
        event(g, font, w, now);
        hints(g, font, w, h, link, t);
    }

    // ------------------------------------------------------------------ partner panel

    private static int panel(GuiGraphicsExtractor g, Font font, LocalPlayer me, Link link, Player partner, long now, float t, float partial) {
        int x = 4;
        int y = 4;
        int width = 132;
        int color = 0xFF000000 | Palette.rgb(link.color());
        g.fill(x - 2, y - 2, x + width, y + 34, 0x90000000);
        g.fill(x - 2, y - 2, x - 1, y + 34, color);
        Screens.face(g, link.partner().get(), x + 1, y + 1, 16, 0xFFFFFFFF);
        Component name = Component.literal(link.partnerName()).append(" ").append(Text.heart(link.color()));
        g.text(font, name, x + 21, y + 1, 0xFFFFFFFF);

        // health
        if (partner != null) {
            float hp = partner.getHealth();
            float max = Math.max(1.0F, partner.getMaxHealth());
            int barW = width - 24;
            g.fill(x + 21, y + 11, x + 21 + barW, y + 15, 0xFF401010);
            g.fill(x + 21, y + 11, x + 21 + (int) (barW * Mth.clamp(hp / max, 0.0F, 1.0F)), y + 15, ModData.downed(partner) ? 0xFFFF2020 : 0xFFE03040);
            small(g, font, Component.literal(String.format(Locale.ROOT, "%.0f/%.0f", hp, max)), x + 21 + barW - 22, y + 10, 0xFFFFFFFF, 0.6F);
        } else {
            small(g, font, Component.translatable("filorosso.hud.far"), x + 21, y + 10, 0xFF909090, 0.75F);
        }

        // level and affinity
        BondLevel level = link.level();
        BondLevel next = level.next();
        Component lv = Component.translatable("filorosso.hud.level", level.number(), level.displayName());
        small(g, font, lv, x + 1, y + 20, 0xFF000000 | level.color, 0.75F);
        int barX = x + 1 + (int) (font.width(lv) * 0.75F) + 4;
        int barW = x + width - 4 - barX;
        if (barW > 8) {
            g.fill(barX, y + 22, barX + barW, y + 24, 0xFF301820);
            float k = next == null ? 1.0F : Mth.clamp((link.xp() - level.xp) / (float) (next.xp - level.xp), 0.0F, 1.0F);
            g.fill(barX, y + 22, barX + (int) (barW * k), y + 24, 0xFF000000 | level.color);
        }

        // where they are
        Component where;
        int whereColor = 0xFFC0C0C0;
        if (link.cut(now)) {
            where = Component.empty().append(Text.icon(Text.SCISSORS, 0xFF6060)).append(" ")
                    .append(Component.translatable("filorosso.hud.cut", time(link.cutUntil() - now)));
            whereColor = 0xFFFF8080;
        } else if (partner == null) {
            where = Component.translatable("filorosso.hud.elsewhere");
        } else {
            double d = me.position().distanceTo(partner.position());
            where = Component.literal(String.format(Locale.ROOT, "%.1f m", d));
        }
        small(g, font, where, x + 1, y + 27, whereColor, 0.75F);
        if (partner != null && !link.cut(now)) {
            arrow(g, font, me, partner, x + width - 10, y + 29, partial);
        }
        return y + 34;
    }

    /** A small arrow pointing to the partner, relative to where you look. */
    private static void arrow(GuiGraphicsExtractor g, Font font, LocalPlayer me, Player partner, int x, int y, float partial) {
        Vec3 to = partner.getPosition(partial).subtract(me.getPosition(partial));
        if (to.horizontalDistanceSqr() < 0.25) {
            return;
        }
        float yaw = me.getViewYRot(partial);
        double angle = Math.atan2(-to.x, to.z) * Mth.RAD_TO_DEG - yaw;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().rotate((float) Math.toRadians(angle));
        g.centeredText(font, Component.literal("▲"), 0, -4, 0xFFFFC0D0);
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ abilities

    private static int abilities(GuiGraphicsExtractor g, Font font, Link link, long now, int y) {
        int x = 2;
        for (Abilities.Ability ability : Abilities.Ability.ALL) {
            int i = ability.ordinal();
            boolean unlocked = link.level().atLeast(ability.level);
            long ready = ClientState.readyAt(i);
            int boxW = 43;
            int bx = x + i * (boxW + 1);
            g.fill(bx, y, bx + boxW, y + 12, unlocked ? 0x90000000 : 0x60000000);
            if (unlocked && now < ready) {
                float k = Mth.clamp((ready - now) / (float) ability.cooldown, 0.0F, 1.0F);
                g.fill(bx, y + 12 - (int) (12 * k), bx + boxW, y + 12, 0x60C08040);
            } else if (unlocked) {
                g.outline(bx, y, boxW, 12, 0xFF60B060);
            }
            Component key = Screens.keyName(FiloRossoClient.ABILITY_KEYS[i]);
            String id = ability.name().toLowerCase(Locale.ROOT);
            Component label = unlocked && now < ready ? Component.literal(time(ready - now))
                    : unlocked ? Component.translatable("filorosso.ability." + id + ".short")
                    : Component.translatable("filorosso.hud.locked", ability.level.number());
            small(g, font, Component.literal("[").append(key).append("]"), bx + 2, y + 3, unlocked ? 0xFFFFE080 : 0xFF707070, 0.6F);
            small(g, font, label, bx + 14, y + 3, unlocked ? 0xFFFFFFFF : 0xFF707070, 0.6F);
        }
        return y + 12;
    }

    // ------------------------------------------------------------------ challenges

    private static int challenges(GuiGraphicsExtractor g, Font font, int y) {
        Net.CoupleInfo info = ClientState.info;
        if (info.ids().isEmpty()) {
            return y;
        }
        int x = 2;
        int rows = info.ids().size();
        g.fill(x, y, x + 132, y + 9 + rows * 7, 0x70000000);
        Component header = Component.empty().append(Text.icon(Text.STAR, 0xFFD030)).append(" ")
                .append(Component.translatable("filorosso.hud.challenges", info.points(), info.rank(), info.couples()));
        small(g, font, header, x + 2, y + 1, 0xFFFFD070, 0.65F);
        int ry = y + 8;
        for (int i = 0; i < rows; i++) {
            String id = info.ids().get(i);
            int goal = i < info.goals().size() ? info.goals().get(i) : 1;
            int progress = i < info.progress().size() ? info.progress().get(i) : 0;
            int done = i < info.done().size() ? info.done().get(i) : 0;
            Component name = Component.translatable("filorosso.challenge." + id, goal);
            int color = done > 0 ? 0xFF80E080 : 0xFFE0E0E0;
            small(g, font, name, x + 3, ry, color, 0.6F);
            Component right = done > 0 ? Component.literal("✔") : Component.literal(Math.min(progress, goal) + "/" + goal);
            small(g, font, right, x + 130 - font.width(right) * 0.6F, ry, color, 0.6F);
            ry += 7;
        }
        return ry + 1;
    }

    // ------------------------------------------------------------------ event

    private static void event(GuiGraphicsExtractor g, Font font, int w, long now) {
        Events.Kind kind = ClientState.event();
        if (kind == null) {
            return;
        }
        Component text = Component.empty().append(Text.icon(Text.STAR, 0xFFD030)).append(" ")
                .append(Component.translatable(kind.key())).append("  ")
                .append(Component.literal(time(ClientState.world.eventEnd() - now)).withColor(0xFFFFFF));
        int tw = font.width(text);
        int bars = ((com.filorosso.client.mixin.BossHealthOverlayAccessor) Minecraft.getInstance().gui.hud.getBossOverlay()).filorosso$events().size();
        int y = bars > 0 ? 14 + Math.min(bars, 3) * 19 : 22;
        g.fill(w / 2 - tw / 2 - 5, y - 3, w / 2 + tw / 2 + 5, y + 10, 0x90200830);
        g.centeredText(font, text, w / 2, y, 0xFFE0A0FF);
    }

    // ------------------------------------------------------------------ hints

    private static void hints(GuiGraphicsExtractor g, Font font, int w, int h, Link link, float t) {
        Component hint = null;
        int color = 0xFFFFC0D0;
        Minecraft mc = Minecraft.getInstance();
        if (ThreadPhysics.hanging) {
            hint = Component.translatable("filorosso.hud.hanging", mc.options.keyJump.getTranslatedKeyMessage());
        } else if (ThreadPhysics.holding) {
            hint = Component.translatable("filorosso.hud.holding", mc.options.keyShift.getTranslatedKeyMessage(), link.partnerName());
            color = ARGB.srgbLerp(0.5F + 0.5F * Mth.sin(t * 0.5F), 0xFFFFE070, 0xFFFFFFFF);
        } else if (ThreadPhysics.jealous) {
            hint = Component.translatable("filorosso.hud.jealous", link.partnerName());
            color = 0xFF90E040;
        } else if (singleHint > 0 && !link.bound()) {
            hint = Component.translatable("filorosso.ability.single");
        }
        if (hint != null) {
            int tw = font.width(hint);
            g.fill(w / 2 - tw / 2 - 4, h - 84, w / 2 + tw / 2 + 4, h - 72, 0x80000000);
            g.centeredText(font, hint, w / 2, h - 82, color);
        }
    }

    // ------------------------------------------------------------------ on the ground

    private static void vignette(GuiGraphicsExtractor g, int w, int h, int color, float alpha) {
        int edge = Math.max(24, Math.min(w, h) / 4);
        int c = ARGB.color(alpha, color);
        int clear = ARGB.color(0.0F, color);
        g.fillGradient(0, 0, w, edge, c, clear);
        g.fillGradient(0, h - edge, w, h, clear, c);
        // sides: thin strips of decreasing alpha
        for (int i = 0; i < edge; i += 2) {
            float k = 1.0F - i / (float) edge;
            int col = ARGB.color(alpha * k * k, color);
            g.fill(i, 0, i + 2, h, col);
            g.fill(w - i - 2, 0, w - i, h, col);
        }
    }

    private static void downed(GuiGraphicsExtractor g, Font font, int w, int h, float t, long now, Status status, Link link) {
        float beat = heartbeat(t);
        vignette(g, w, h, 0xB00000, 0.55F + 0.35F * beat);
        g.fill(0, 0, w, h, ARGB.color(0.18F, 0x300000));
        centered(g, font, Component.translatable("filorosso.hud.downed"), w / 2.0F, h / 2.0F - 40, 0xFFFF4040, 2.0F);
        long left = status.downedUntil() - now;
        centered(g, font, Component.translatable("filorosso.hud.downed.wait", link.partnerName(), time(left)), w / 2.0F, h / 2.0F - 16,
                0xFFFFFFFF, 1.0F);
        int barW = 120;
        int bx = w / 2 - barW / 2;
        int by = h / 2 - 4;
        g.fill(bx, by, bx + barW, by + 5, 0xFF300810);
        g.fill(bx, by, bx + (int) (barW * status.revive() / (float) Status.REVIVE_TICKS), by + 5, 0xFFFF70A0);
        if (status.revive() > 0) {
            centered(g, font, Component.translatable("filorosso.hud.being_revived"), w / 2.0F, by + 8, 0xFFFFB0C8, 0.75F);
        }
    }

    private static void partnerDowned(GuiGraphicsExtractor g, Font font, int w, int h, float t, long now, LocalPlayer me, Player partner, float partial) {
        float pulse = 0.5F + 0.5F * Mth.sin(t * 0.5F);
        vignette(g, w, h, 0xA00000, 0.18F + 0.12F * pulse);
        Status s = ModData.status(partner);
        Component text = Component.translatable("filorosso.hud.partner_downed", partner.getGameProfile().name(), time(s.downedUntil() - now));
        centered(g, font, text, w / 2.0F, 44, ARGB.srgbLerp(pulse, 0xFFFF4040, 0xFFFFFFFF), 1.25F);
        double d = me.position().distanceTo(partner.position());
        Component how = d < 2.2 ? Component.translatable("filorosso.hud.revive_how", Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage(),
                Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage())
                : Component.literal(String.format(Locale.ROOT, "%.1f m", d));
        centered(g, font, how, w / 2.0F, 58, 0xFFFFD0D8, 0.9F);
        if (d >= 2.2) {
            arrow(g, font, me, partner, w / 2, 72, partial);
        }
        if (s.revive() > 0) {
            int barW = 100;
            g.fill(w / 2 - barW / 2, 80, w / 2 + barW / 2, 84, 0xFF300810);
            g.fill(w / 2 - barW / 2, 80, w / 2 - barW / 2 + (int) (barW * s.revive() / (float) Status.REVIVE_TICKS), 84, 0xFFFF70A0);
        }
    }

    /** Two quick beats then a pause, 0-1. */
    private static float heartbeat(float t) {
        float phase = (t % 20.0F) / 20.0F;
        float a = Math.max(0.0F, 1.0F - Math.abs(phase - 0.08F) * 14.0F);
        float b = Math.max(0.0F, 1.0F - Math.abs(phase - 0.24F) * 14.0F);
        return Math.max(a, b * 0.7F);
    }
}
