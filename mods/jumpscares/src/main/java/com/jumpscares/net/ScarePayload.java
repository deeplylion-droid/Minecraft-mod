package com.jumpscares.net;

import com.jumpscares.Jumpscares;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent to the victim: play this scare now. The seed makes every client animate it the same way. */
public record ScarePayload(int kind, long seed) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ScarePayload> TYPE = new CustomPacketPayload.Type<>(Jumpscares.id("scare"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScarePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScarePayload::kind,
            ByteBufCodecs.VAR_LONG, ScarePayload::seed,
            ScarePayload::new);

    @Override
    public CustomPacketPayload.Type<ScarePayload> type() {
        return TYPE;
    }
}
