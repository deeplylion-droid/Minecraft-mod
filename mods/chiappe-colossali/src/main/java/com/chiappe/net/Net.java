package com.chiappe.net;

import com.chiappe.Chiappe;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Every packet of the mod. */
public final class Net {
    private Net() {
    }

    /** Client to server: the fart key was pressed. */
    public record Fart() implements CustomPacketPayload {
        public static final Fart INSTANCE = new Fart();
        public static final Type<Fart> TYPE = new Type<>(Chiappe.id("fart"));
        public static final StreamCodec<ByteBuf, Fart> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to clients: this player farted (the cheeks move, the gas comes out). */
    public record FartFx(int entity, int level) implements CustomPacketPayload {
        public static final Type<FartFx> TYPE = new Type<>(Chiappe.id("fart_fx"));
        public static final StreamCodec<ByteBuf, FartFx> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FartFx::entity, ByteBufCodecs.VAR_INT, FartFx::level, FartFx::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(Fart.TYPE, Fart.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FartFx.TYPE, FartFx.CODEC);
    }
}
