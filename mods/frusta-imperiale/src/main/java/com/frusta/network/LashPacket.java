package com.frusta.network;

import com.frusta.server.WhipMoves;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: the attack key was clicked with a whip in hand. {@code style} is how the client
 * swung it (it already shows the lash), so the other players see the same one. The server checks
 * everything else itself.
 */
public record LashPacket(byte style) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(style);
    }

    public static LashPacket decode(FriendlyByteBuf buffer) {
        return new LashPacket(buffer.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            WhipMoves.lash(player, style);
        }
    }
}
