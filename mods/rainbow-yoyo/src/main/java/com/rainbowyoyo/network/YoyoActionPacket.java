package com.rainbowyoyo.network;

import com.rainbowyoyo.entity.YoyoEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: the attack key was clicked while holding the yo-yo (quick shot, or a trick if
 * the yo-yo is out), or the use key was released (the yo-yo comes back). The server checks
 * everything itself: it only trusts that a key was pressed.
 */
public record YoyoActionPacket(byte action) {
    public static final byte ATTACK = 0;
    public static final byte RELEASE = 1;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(action);
    }

    public static YoyoActionPacket decode(FriendlyByteBuf buffer) {
        return new YoyoActionPacket(buffer.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            YoyoEntity.onAction(player, action);
        }
    }
}
