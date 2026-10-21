package com.infezione.network;

import com.infezione.client.ClientInfections;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server to client: an infected player has an episode (a cough, a convulsion...), to animate. */
public record SymptomPacket(int entity, int symptom) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entity);
        buffer.writeByte(symptom);
    }

    public static SymptomPacket decode(FriendlyByteBuf buffer) {
        return new SymptomPacket(buffer.readVarInt(), buffer.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientInfections.onSymptom(this));
    }
}
