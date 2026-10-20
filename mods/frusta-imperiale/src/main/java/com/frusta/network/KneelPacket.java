package com.frusta.network;

import com.frusta.client.ClientHooks;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server to client: an entity falls to its knees for {@code ticks} ticks (0 = it stands up again). */
public record KneelPacket(int entity, int ticks) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entity);
        buffer.writeVarInt(ticks);
    }

    public static KneelPacket decode(FriendlyByteBuf buffer) {
        return new KneelPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onKneel(entity, ticks));
    }
}
