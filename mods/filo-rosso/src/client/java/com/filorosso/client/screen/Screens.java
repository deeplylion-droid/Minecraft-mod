package com.filorosso.client.screen;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;

/** Small things the screens and the HUD share. */
public final class Screens {
    private Screens() {
    }

    public static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public static Component keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage();
    }

    public static PlayerSkin skin(UUID id) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null || id == null ? null : connection.getPlayerInfo(id);
        return info != null ? info.getSkin() : id != null ? DefaultPlayerSkin.get(id) : DefaultPlayerSkin.getDefaultSkin();
    }

    public static void face(GuiGraphicsExtractor g, UUID id, int x, int y, int size, int color) {
        PlayerFaceExtractor.extractRenderState(g, skin(id), x, y, size, color);
    }
}
