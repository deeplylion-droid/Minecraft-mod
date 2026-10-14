package com.belowbedrock.net;

import com.belowbedrock.BelowBedrock;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Tells one client to play a screen effect: the server decides when, the client draws it. */
public record ScreenFxPayload(int effect, int ticks, float strength) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ScreenFxPayload> TYPE = new CustomPacketPayload.Type<>(BelowBedrock.id("fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenFxPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScreenFxPayload::effect,
            ByteBufCodecs.VAR_INT, ScreenFxPayload::ticks,
            ByteBufCodecs.FLOAT, ScreenFxPayload::strength,
            ScreenFxPayload::new);

    /** The effects a client knows how to draw. */
    public enum Fx {
        /** Torn, colour-split picture; strength 0..1. */
        GLITCH,
        /** Negative colours. */
        INVERT,
        /** Fade to black, hold, fade back. */
        BLACKOUT,
        /** His face for a frame or two. */
        FACE,
        /** His face, screaming, all over the screen. */
        JUMPSCARE,
        /** The fog closes in; strength = how far you can still see, in blocks. */
        FOG,
        /** The long fall: grain and darkness grow for {@code ticks}. */
        FALLING,
        /** The fall is over. */
        FALL_END
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(TYPE, CODEC);
    }

    public static void send(ServerPlayer player, Fx fx, int ticks, float strength) {
        if (ServerPlayNetworking.canSend(player, TYPE)) {
            ServerPlayNetworking.send(player, new ScreenFxPayload(fx.ordinal(), ticks, strength));
        }
    }

    @Override
    public CustomPacketPayload.Type<ScreenFxPayload> type() {
        return TYPE;
    }
}
