package com.rainbowyoyo.client;

import com.rainbowyoyo.client.sound.YoyoSpinSound;
import com.rainbowyoyo.entity.YoyoEntity;
import com.rainbowyoyo.motion.YoyoPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Client-only calls made from common code (through {@code DistExecutor}, or only ever on the client). */
public final class ClientHooks {

    public static void startSpinSound(YoyoEntity yoyo) {
        Minecraft.getInstance().getSoundManager().play(new YoyoSpinSound(yoyo));
    }

    /** Shows the trick's name above the hotbar, cycling through the rainbow. */
    public static void announceTrick(YoyoPhase trick, int combo) {
        MutableComponent text = Component.literal("✦ ")
                .append(Component.translatable("trick.rainbowyoyo." + trick.key))
                .append(Component.literal(" ✦"));
        if (combo > 1) {
            text.append(Component.literal("   ×" + combo));
        }
        Minecraft.getInstance().gui.setOverlayMessage(text, true);
    }

    public static void announceNovaReady() {
        Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("message.rainbowyoyo.nova_ready"), true);
    }

    /** Only called from tooltips, which only exist on the client. */
    public static boolean hasShiftDown() {
        return Screen.hasShiftDown();
    }

    private ClientHooks() {
    }
}
