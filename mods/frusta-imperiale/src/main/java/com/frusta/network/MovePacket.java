package com.frusta.network;

import com.frusta.client.ClientHooks;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: a player started a move with the whip (lash, Imperial Crack or grab). Every
 * client draws it from here, the same way: where it aims, how far it reaches and what it grabs.
 *
 * @param move    {@code WhipShape.Move} ordinal
 * @param aimX    unit look direction when the move started
 * @param reach   distance from the eyes to where it cracks (or to what it grabs)
 * @param contact the lash runs into something before it is fully stretched
 * @param target  entity id of what the grab wraps around, -1 for none
 * @param charge  how charged the Imperial Crack was, 0 to 1
 */
public record MovePacket(int player, byte move, byte style, float aimX, float aimY, float aimZ, float reach,
                         boolean contact, int target, float charge) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(player);
        buffer.writeByte(move);
        buffer.writeByte(style);
        buffer.writeFloat(aimX);
        buffer.writeFloat(aimY);
        buffer.writeFloat(aimZ);
        buffer.writeFloat(reach);
        buffer.writeBoolean(contact);
        buffer.writeVarInt(target);
        buffer.writeFloat(charge);
    }

    public static MovePacket decode(FriendlyByteBuf buffer) {
        return new MovePacket(buffer.readVarInt(), buffer.readByte(), buffer.readByte(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(), buffer.readVarInt(),
                buffer.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onMove(this));
    }
}
