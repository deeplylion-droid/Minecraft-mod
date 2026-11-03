package com.punizioni.net;

import com.punizioni.Punizioni;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Effects the server sends: what the victim feels on their own screen, and the pieces that fly. */
public final class Net {
    private Net() {
    }

    /** On the victim's own screen: a red flash, a jolt, depending on what happened. */
    public record VictimFx(int kind, float strength) implements CustomPacketPayload {
        public static final int LASH = 0;
        public static final int SPIKES = 1;
        public static final int STRETCH = 2;
        public static final int CHOP = 3;
        public static final int BITE = 4;
        public static final int BURN = 5;
        public static final int BONE = 6;
        public static final int PECK = 7;
        public static final int HANG = 8;
        /** Dung in the face (strength 1) or elsewhere (less). */
        public static final int POOP = 9;

        public static final Type<VictimFx> TYPE = new Type<>(Punizioni.id("victim_fx"));
        public static final StreamCodec<ByteBuf, VictimFx> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, VictimFx::kind, ByteBufCodecs.FLOAT, VictimFx::strength, VictimFx::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A hand or a foot comes off on the chopping block (and flies). */
    public record ChopFx(int machine, int victim, int limb) implements CustomPacketPayload {
        public static final Type<ChopFx> TYPE = new Type<>(Punizioni.id("chop_fx"));
        public static final StreamCodec<ByteBuf, ChopFx> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ChopFx::machine, ByteBufCodecs.VAR_INT, ChopFx::victim, ByteBufCodecs.VAR_INT, ChopFx::limb, ChopFx::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The horses tear arms and legs off (they fly after the horses). */
    public record TearFx(int machine, int victim) implements CustomPacketPayload {
        public static final Type<TearFx> TYPE = new Type<>(Punizioni.id("tear_fx"));
        public static final StreamCodec<ByteBuf, TearFx> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TearFx::machine, ByteBufCodecs.VAR_INT, TearFx::victim, TearFx::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The guillotine's blade: the head drops into the basket. */
    public record HeadFx(int machine, int victim) implements CustomPacketPayload {
        public static final Type<HeadFx> TYPE = new Type<>(Punizioni.id("head_fx"));
        public static final StreamCodec<ByteBuf, HeadFx> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HeadFx::machine, ByteBufCodecs.VAR_INT, HeadFx::victim, HeadFx::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Through the king's lens: the players nearby with fresh misdeeds (they glow red), sent every second to whoever holds it. */
    public record Guilty(List<Integer> ids) implements CustomPacketPayload {
        public static final Type<Guilty> TYPE = new Type<>(Punizioni.id("guilty"));
        public static final StreamCodec<ByteBuf, Guilty> CODEC = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).map(Guilty::new, Guilty::ids);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(VictimFx.TYPE, VictimFx.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ChopFx.TYPE, ChopFx.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TearFx.TYPE, TearFx.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HeadFx.TYPE, HeadFx.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Guilty.TYPE, Guilty.CODEC);
    }
}
