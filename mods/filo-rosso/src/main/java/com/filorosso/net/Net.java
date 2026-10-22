package com.filorosso.net;

import com.filorosso.FiloRosso;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

/** Every packet of the mod. */
public final class Net {
    private Net() {
    }

    /** Client to server: "I want to be tied to this player". */
    public record Propose(UUID target) implements CustomPacketPayload {
        public static final Type<Propose> TYPE = new Type<>(FiloRosso.id("propose"));
        public static final StreamCodec<ByteBuf, Propose> CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC, Propose::target, Propose::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: yes or no to someone's proposal. */
    public record Answer(UUID proposer, boolean yes) implements CustomPacketPayload {
        public static final Type<Answer> TYPE = new Type<>(FiloRosso.id("answer"));
        public static final StreamCodec<ByteBuf, Answer> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Answer::proposer, ByteBufCodecs.BOOL, Answer::yes, Answer::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: send me the list of players (to choose a soulmate). */
    public record OpenMenu() implements CustomPacketPayload {
        public static final OpenMenu INSTANCE = new OpenMenu();
        public static final Type<OpenMenu> TYPE = new Type<>(FiloRosso.id("open_menu"));
        public static final StreamCodec<ByteBuf, OpenMenu> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: I want to break up (the partner is asked). */
    public record BreakUp() implements CustomPacketPayload {
        public static final BreakUp INSTANCE = new BreakUp();
        public static final Type<BreakUp> TYPE = new Type<>(FiloRosso.id("break_up"));
        public static final StreamCodec<ByteBuf, BreakUp> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the partner's answer to a break up. */
    public record BreakUpAnswer(boolean yes) implements CustomPacketPayload {
        public static final Type<BreakUpAnswer> TYPE = new Type<>(FiloRosso.id("break_up_answer"));
        public static final StreamCodec<ByteBuf, BreakUpAnswer> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, BreakUpAnswer::yes, BreakUpAnswer::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: use an ability of the bond (see {@code Abilities}). */
    public record UseAbility(int ability) implements CustomPacketPayload {
        public static final Type<UseAbility> TYPE = new Type<>(FiloRosso.id("use_ability"));
        public static final StreamCodec<ByteBuf, UseAbility> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, UseAbility::ability, UseAbility::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * A player in the soulmate list.
     *
     * @param status {@link #FREE}, {@link #BOUND} (to {@code other}), {@link #PROPOSED_TO_YOU}, {@link #YOU_PROPOSED}
     */
    public record Candidate(UUID id, String name, int status, String other) {
        public static final int FREE = 0;
        public static final int BOUND = 1;
        public static final int PROPOSED_TO_YOU = 2;
        public static final int YOU_PROPOSED = 3;

        public static final StreamCodec<ByteBuf, Candidate> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Candidate::id,
                ByteBufCodecs.STRING_UTF8, Candidate::name,
                ByteBufCodecs.VAR_INT, Candidate::status,
                ByteBufCodecs.STRING_UTF8, Candidate::other,
                Candidate::new);
    }

    /** Server to client: the players you can tie yourself to ({@code open}: open the screen, else only refresh it). */
    public record Candidates(List<Candidate> list, boolean open) implements CustomPacketPayload {
        public static final Type<Candidates> TYPE = new Type<>(FiloRosso.id("candidates"));
        public static final StreamCodec<ByteBuf, Candidates> CODEC = StreamCodec.composite(
                Candidate.CODEC.apply(ByteBufCodecs.list()), Candidates::list, ByteBufCodecs.BOOL, Candidates::open, Candidates::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: {@code name} wants to be tied to you. */
    public record Proposal(UUID from, String name) implements CustomPacketPayload {
        public static final Type<Proposal> TYPE = new Type<>(FiloRosso.id("proposal"));
        public static final StreamCodec<ByteBuf, Proposal> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Proposal::from, ByteBufCodecs.STRING_UTF8, Proposal::name, Proposal::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: your partner wants to break up. */
    public record BreakUpAsk(String name) implements CustomPacketPayload {
        public static final Type<BreakUpAsk> TYPE = new Type<>(FiloRosso.id("break_up_ask"));
        public static final StreamCodec<ByteBuf, BreakUpAsk> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, BreakUpAsk::name, BreakUpAsk::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server to clients: something happens to a thread between two players (for the animation).
     *
     * @param kind {@link #SNAP} (break up), {@link #CUT} (scissors, Recisione), {@link #COMBO} (couple hit on a target, {@code b} = target)
     */
    public record ThreadFx(int kind, int a, int b) implements CustomPacketPayload {
        public static final int SNAP = 0;
        public static final int CUT = 1;
        public static final int COMBO = 2;
        public static final int LEVEL_UP = 3;
        public static final int PULL = 4;
        /** The slingshot: the thread stretches and lets go ({@code a}, {@code b} = the couple). */
        public static final int SLING = 5;
        /** The kiss: a wave of hearts from between the two ({@code a}, {@code b} = the couple). */
        public static final int KISS = 6;
        /** The lasso flies from {@code a} to {@code b}. */
        public static final int LASSO = 7;
        /** "Eterni" begins ({@code a}, {@code b} = the couple). */
        public static final int ETERNAL = 8;

        public static final Type<ThreadFx> TYPE = new Type<>(FiloRosso.id("thread_fx"));
        public static final StreamCodec<ByteBuf, ThreadFx> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ThreadFx::kind, ByteBufCodecs.VAR_INT, ThreadFx::a, ByteBufCodecs.VAR_INT, ThreadFx::b, ThreadFx::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server to clients: the world-wide state of the threads (events), sent when it changes.
     *
     * @param event      {@code Events.Kind} ordinal, -1 = none
     * @param eventEnd   game time at which the event ends
     * @param length     thread length right now (6, 3 during "short thread"...)
     * @param extra      extra links during events: pairs of player UUIDs (chain, swapped partners)
     * @param swapped    true if {@code extra} replaces the partners (swap) instead of adding to them (chain)
     */
    public record WorldState(int event, long eventEnd, float length, List<UUID> extra, boolean swapped) implements CustomPacketPayload {
        public static final WorldState NONE = new WorldState(-1, 0L, 6.0F, List.of(), false);
        public static final Type<WorldState> TYPE = new Type<>(FiloRosso.id("world_state"));
        public static final StreamCodec<ByteBuf, WorldState> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, WorldState::event,
                ByteBufCodecs.VAR_LONG, WorldState::eventEnd,
                ByteBufCodecs.FLOAT, WorldState::length,
                UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), WorldState::extra,
                ByteBufCodecs.BOOL, WorldState::swapped,
                WorldState::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server to client: your couple's challenges and score.
     *
     * @param ids      challenge ids of this session
     * @param progress progress per challenge
     * @param goals    target per challenge
     * @param done     rank at which you finished each one (0 = not yet)
     */
    public record CoupleInfo(int points, int rank, int couples, List<String> ids, List<Integer> progress, List<Integer> goals,
                             List<Integer> done) implements CustomPacketPayload {
        public static final CoupleInfo NONE = new CoupleInfo(0, 0, 0, List.of(), List.of(), List.of(), List.of());
        public static final Type<CoupleInfo> TYPE = new Type<>(FiloRosso.id("couple_info"));
        public static final StreamCodec<ByteBuf, CoupleInfo> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CoupleInfo::points,
                ByteBufCodecs.VAR_INT, CoupleInfo::rank,
                ByteBufCodecs.VAR_INT, CoupleInfo::couples,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), CoupleInfo::ids,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), CoupleInfo::progress,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), CoupleInfo::goals,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), CoupleInfo::done,
                CoupleInfo::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: cooldowns of your abilities (game time at which each one is ready again). */
    public record Cooldowns(List<Long> readyAt) implements CustomPacketPayload {
        public static final Type<Cooldowns> TYPE = new Type<>(FiloRosso.id("cooldowns"));
        public static final StreamCodec<ByteBuf, Cooldowns> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), Cooldowns::readyAt, Cooldowns::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to clients: every bound player's couple colour (for the hearts in the tab list). */
    public record Roster(java.util.Map<UUID, Integer> colors) implements CustomPacketPayload {
        public static final Type<Roster> TYPE = new Type<>(FiloRosso.id("roster"));
        public static final StreamCodec<ByteBuf, Roster> CODEC = StreamCodec.composite(
                ByteBufCodecs.map(java.util.HashMap::new, UUIDUtil.STREAM_CODEC, ByteBufCodecs.VAR_INT), Roster::colors, Roster::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to clients: a dome of threads stands here until {@code end} (game time). */
    public record Dome(int id, double x, double y, double z, float radius, long start, long end, int color) implements CustomPacketPayload {
        public static final Type<Dome> TYPE = new Type<>(FiloRosso.id("dome"));
        public static final StreamCodec<ByteBuf, Dome> CODEC = new StreamCodec<>() {
            @Override
            public Dome decode(ByteBuf buf) {
                return new Dome(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf),
                        ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.INT.decode(buf));
            }

            @Override
            public void encode(ByteBuf buf, Dome d) {
                ByteBufCodecs.VAR_INT.encode(buf, d.id);
                ByteBufCodecs.DOUBLE.encode(buf, d.x);
                ByteBufCodecs.DOUBLE.encode(buf, d.y);
                ByteBufCodecs.DOUBLE.encode(buf, d.z);
                ByteBufCodecs.FLOAT.encode(buf, d.radius);
                ByteBufCodecs.VAR_LONG.encode(buf, d.start);
                ByteBufCodecs.VAR_LONG.encode(buf, d.end);
                ByteBufCodecs.INT.encode(buf, d.color);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to clients: {@code by} has lassoed {@code target} until {@code until} (game time; 0 = let go). */
    public record Tied(int target, int by, long until) implements CustomPacketPayload {
        public static final Type<Tied> TYPE = new Type<>(FiloRosso.id("tied"));
        public static final StreamCodec<ByteBuf, Tied> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Tied::target, ByteBufCodecs.VAR_INT, Tied::by, ByteBufCodecs.VAR_LONG, Tied::until, Tied::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(Propose.TYPE, Propose.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Answer.TYPE, Answer.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(OpenMenu.TYPE, OpenMenu.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BreakUp.TYPE, BreakUp.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BreakUpAnswer.TYPE, BreakUpAnswer.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UseAbility.TYPE, UseAbility.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Candidates.TYPE, Candidates.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Proposal.TYPE, Proposal.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BreakUpAsk.TYPE, BreakUpAsk.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ThreadFx.TYPE, ThreadFx.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WorldState.TYPE, WorldState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CoupleInfo.TYPE, CoupleInfo.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Cooldowns.TYPE, Cooldowns.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Roster.TYPE, Roster.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Dome.TYPE, Dome.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Tied.TYPE, Tied.CODEC);
    }
}
