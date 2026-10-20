package com.frusta.client;

import com.frusta.motion.Whip;
import com.frusta.motion.WhipShape;
import com.frusta.network.MovePacket;
import com.frusta.server.Kneel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/** Client-only calls made from common code (through {@code DistExecutor}, or only ever on the client). */
public final class ClientHooks {

    /** Only called from tooltips, which only exist on the client. */
    public static boolean hasShiftDown() {
        return Screen.hasShiftDown();
    }

    /** Sneak + use: show the grab at once (the server does the pulling). */
    public static void predictGrab() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            WhipAnims.startLocal(player, WhipShape.Move.GRAB, 0, 0.0F, 0);
        }
    }

    /** The use key let go after spinning the whip: the Imperial Crack (or a plain lash if too short). */
    public static void predictRelease(int held) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem())) {
            return;
        }
        if (held < Whip.TWIRL_MIN) {
            WhipAnims.startLocal(player, WhipShape.Move.LASH, player.getRandom().nextInt(WhipShape.STYLES), 0.0F, 0);
        } else {
            WhipAnims.startLocal(player, WhipShape.Move.IMPERIAL, 0, (float) Whip.charge(held), held);
        }
    }

    public static void onMove(MovePacket packet) {
        WhipAnims.onMove(packet);
    }

    /** An entity falls to its knees (or stands up, ticks = 0): remember it and resize it like the server did. */
    public static void onKneel(int entityId, int ticks) {
        if (ticks > 0) {
            Kneel.CLIENT.put(entityId, Kneel.clientTicks + ticks);
        } else {
            Kneel.CLIENT.remove(entityId);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Entity entity = mc.level.getEntity(entityId);
            if (entity != null) {
                entity.refreshDimensions();
            }
        }
    }

    private ClientHooks() {
    }
}
