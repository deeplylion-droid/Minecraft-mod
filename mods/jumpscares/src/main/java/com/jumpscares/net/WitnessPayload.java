package com.jumpscares.net;

import com.jumpscares.Jumpscares;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent to the players near a victim, so they can watch the monster jump at the victim's face. */
public record WitnessPayload(int victim, int kind, long seed) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WitnessPayload> TYPE = new CustomPacketPayload.Type<>(Jumpscares.id("witness"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WitnessPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WitnessPayload::victim,
            ByteBufCodecs.VAR_INT, WitnessPayload::kind,
            ByteBufCodecs.VAR_LONG, WitnessPayload::seed,
            WitnessPayload::new);

    @Override
    public CustomPacketPayload.Type<WitnessPayload> type() {
        return TYPE;
    }
}
