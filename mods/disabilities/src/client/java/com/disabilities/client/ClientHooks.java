package com.disabilities.client;

import com.disabilities.client.render.PaleSkins;
import com.disabilities.client.render.Visuals;
import com.disabilities.condition.Condition;
import com.disabilities.condition.ModData;
import com.disabilities.condition.Picks;
import com.disabilities.condition.Status;
import com.disabilities.server.Ailments;
import com.disabilities.server.Chooser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Glue called from the client mixins. */
public final class ClientHooks {
    private ClientHooks() {
    }

    /** Everyone's conditions, from the server (players far away have no entity to read them from). */
    private static Map<UUID, Integer> roster = new HashMap<>();
    private static long lastHint;

    public static void setRoster(Map<UUID, Integer> masks) {
        roster = new HashMap<>(masks);
    }

    public static int rosterMask(UUID id) {
        return roster.getOrDefault(id, 0);
    }

    /** Fills the visuals of a player about to be drawn. */
    public static void extract(Avatar entity, AvatarRenderState state, float partial) {
        if (!(entity instanceof Player player)) {
            state.setData(Visuals.KEY, null);
            return;
        }
        Picks picks = ModData.picks(player);
        Status status = ModData.status(player);
        long now = player.level().getGameTime();
        Visuals v = new Visuals();
        v.mask = picks.mask();
        v.asleep = status.asleep(now);
        v.frozen = status.frozen(now);
        v.winded = status.winded(now);
        v.bleeding = status.bleeding(now);
        v.confused = status.confused(now);
        v.breathless = picks.has(Condition.ASTHMA) && !player.isUnderWater() && player.getAirSupply() < player.getMaxAirSupply() * 0.2F;
        v.passenger = player.isPassenger();
        v.time = state.ageInTicks;
        v.seed = player.getUUID().getMostSignificantBits();
        v.missingLeftArm = player.getMainArm() == HumanoidArm.RIGHT;
        Bubbles.Gesturing gesturing = Bubbles.gesturing(player.getId());
        if (gesturing != null) {
            v.gesture = gesturing.gesture();
            v.gestureTime = now - gesturing.start() + partial;
        }
        Component bubble = Bubbles.bubble(player.getId());
        if (bubble == null && v.asleep) {
            bubble = Component.literal("Zzz").withStyle(ChatFormatting.AQUA);
        }
        if (bubble == null && v.confused) {
            bubble = Component.literal("?").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        }
        v.bubble = bubble;
        state.setData(Visuals.KEY, v);

        if (v.has(Condition.WHEELCHAIR)) {
            state.isCrouching = false;
        }
        if (v.has(Condition.ALBINISM)) {
            state.skin = PaleSkins.pale(state.skin);
        }
        LocalPlayer me = Minecraft.getInstance().player;
        if (me != null && player != me) {
            if (Me.has(Condition.BLIND) && Math.sqrt(state.distanceToCameraSq) > Fx.blindRadius(partial) + 1.0) {
                state.nameTag = null;
                state.scoreText = null;
                v.bubble = null;
            } else if (Me.has(Condition.AMNESIA) && state.nameTag != null) {
                state.nameTag = forgotten(picks.mask());
            }
        }
    }

    /** What an amnesic player sees instead of a name. */
    public static Component forgotten(int mask) {
        StringBuilder glyphs = new StringBuilder();
        for (Condition c : Picks.list(mask)) {
            glyphs.append(c.glyph());
        }
        Component name = Component.literal("???");
        return glyphs.isEmpty() ? name : Component.empty().append(name).append(Component.literal(" ")).append(Chooser.badge(glyphs.toString()));
    }

    /** Tab list: badges after the names, "???" for an amnesic player. */
    public static Component tabName(Component name, UUID id) {
        int mask = rosterMask(id);
        LocalPlayer me = Me.player();
        if (me != null && !me.getUUID().equals(id) && Me.has(Condition.AMNESIA)) {
            return forgotten(mask);
        }
        if (mask == 0) {
            return name;
        }
        StringBuilder glyphs = new StringBuilder();
        for (Condition c : Picks.list(mask)) {
            glyphs.append(c.glyph());
        }
        return Component.empty().append(name).append(Component.literal(" ")).append(Chooser.badge(glyphs.toString()));
    }

    /** Can the local player use their hands right now? Shows why not. */
    public static boolean handsBlocked(boolean use) {
        LocalPlayer player = Me.player();
        if (player == null) {
            return false;
        }
        if (Me.asleep()) {
            return true;
        }
        if (Me.has(Condition.ONE_LEG) && moving(player)) {
            hint("disabilities.msg.crutches");
            return true;
        }
        if (use && Me.has(Condition.ONE_ARM) && (Ailments.twoHanded(player.getMainHandItem()) || Ailments.twoHanded(player.getOffhandItem()))) {
            hint("disabilities.msg.two_hands");
            return true;
        }
        return false;
    }

    private static boolean moving(LocalPlayer player) {
        return player.input.getMoveVector().lengthSquared() > 0.01F || player.getDeltaMovement().horizontalDistanceSqr() > 0.0025;
    }

    private static void hint(String key) {
        long now = System.currentTimeMillis();
        if (now - lastHint > 1500) {
            lastHint = now;
            Minecraft.getInstance().gui.hud.setOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.YELLOW), false);
        }
    }

    /** Can the local player sprint? */
    public static boolean canSprint(LocalPlayer player) {
        if (Me.frozen() || Me.has(Condition.ONE_LEG)) {
            return false;
        }
        if (Me.status().winded(Me.now())) {
            return false;
        }
        return !Me.has(Condition.ASTHMA) || player.getAirSupply() >= player.getMaxAirSupply() * 0.25F;
    }
}
