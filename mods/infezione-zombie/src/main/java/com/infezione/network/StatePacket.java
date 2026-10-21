package com.infezione.network;

import com.infezione.client.ClientInfections;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: a player's infection. Everyone who can see the player gets it when it changes; the
 * player also gets it every second, with the time left in the stage.
 */
public record StatePacket(int entity, UUID player, int stage, boolean paused, int ticksLeft, int stageTicks, int change) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entity);
        buffer.writeUUID(player);
        buffer.writeByte(stage);
        buffer.writeBoolean(paused);
        buffer.writeVarInt(ticksLeft);
        buffer.writeVarInt(stageTicks);
        buffer.writeByte(change);
    }

    public static StatePacket decode(FriendlyByteBuf buffer) {
        return new StatePacket(buffer.readVarInt(), buffer.readUUID(), buffer.readByte(), buffer.readBoolean(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientInfections.onState(this));
    }
}
