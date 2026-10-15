package com.disabilities.net;

import com.disabilities.Disabilities;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Every packet of the mod. */
public final class Net {
    private Net() {
    }

    /** Server to client: pick your conditions now ({@code taken} = conditions other players already have). */
    public record OpenChooser(int taken) implements CustomPacketPayload {
        public static final Type<OpenChooser> TYPE = new Type<>(Disabilities.id("open_chooser"));
        public static final StreamCodec<ByteBuf, OpenChooser> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenChooser::taken, OpenChooser::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the conditions picked in the chooser. */
    public record Choose(int mask) implements CustomPacketPayload {
        public static final Type<Choose> TYPE = new Type<>(Disabilities.id("choose"));
        public static final StreamCodec<ByteBuf, Choose> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Choose::mask, Choose::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: a mute player makes a gesture. */
    public record MakeGesture(int gesture) implements CustomPacketPayload {
        public static final Type<MakeGesture> TYPE = new Type<>(Disabilities.id("make_gesture"));
        public static final StreamCodec<ByteBuf, MakeGesture> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, MakeGesture::gesture, MakeGesture::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to clients: player {@code entityId} is making a gesture (arm animation and bubble). */
    public record ShowGesture(int entityId, int gesture) implements CustomPacketPayload {
        public static final Type<ShowGesture> TYPE = new Type<>(Disabilities.id("show_gesture"));
        public static final StreamCodec<ByteBuf, ShowGesture> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ShowGesture::entityId,
                ByteBufCodecs.VAR_INT, ShowGesture::gesture,
                ShowGesture::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to clients: a speech bubble over a player's head (tics, confusion). */
    public record Bubble(int entityId, Component text, int ticks) implements CustomPacketPayload {
        public static final Type<Bubble> TYPE = new Type<>(Disabilities.id("bubble"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Bubble> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Bubble::entityId,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Bubble::text,
                ByteBufCodecs.VAR_INT, Bubble::ticks,
                Bubble::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to the player concerned: something happens to your body (sneeze, tic, stumble...). */
    public record Episode(int kind, int arg) implements CustomPacketPayload {
        public static final Type<Episode> TYPE = new Type<>(Disabilities.id("episode"));
        public static final StreamCodec<ByteBuf, Episode> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Episode::kind,
                ByteBufCodecs.VAR_INT, Episode::arg,
                Episode::new);

        public Episode(EpisodeKind kind, int arg) {
            this(kind.ordinal(), arg);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to clients: everybody's conditions (for the tab list, also for players far away). */
    public record Roster(java.util.Map<java.util.UUID, Integer> masks) implements CustomPacketPayload {
        public static final Type<Roster> TYPE = new Type<>(Disabilities.id("roster"));
        public static final StreamCodec<ByteBuf, Roster> CODEC = StreamCodec.composite(
                ByteBufCodecs.map(java.util.HashMap::new, net.minecraft.core.UUIDUtil.STREAM_CODEC, ByteBufCodecs.VAR_INT), Roster::masks,
                Roster::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(OpenChooser.TYPE, OpenChooser.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Choose.TYPE, Choose.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MakeGesture.TYPE, MakeGesture.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShowGesture.TYPE, ShowGesture.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Bubble.TYPE, Bubble.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Episode.TYPE, Episode.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Roster.TYPE, Roster.CODEC);
    }
}
