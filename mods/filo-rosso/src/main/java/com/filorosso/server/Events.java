package com.filorosso.server;

import com.filorosso.data.Couple;
import com.filorosso.data.CoupleBook;
import com.filorosso.net.Net;
import com.filorosso.sound.ModSounds;
import com.filorosso.util.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** "Capricci del destino": every few minutes something happens to every thread for a while. */
public final class Events {
    private Events() {
    }

    public enum Kind {
        SHORT("corto", 20 * 180),
        CHAIN("catena", 20 * 180),
        FREEDOM("liberta", 20 * 180),
        SWAP("scambio", 20 * 180),
        ROMANCE("romantica", 20 * 300),
        QUARREL("litigio", 20 * 120),
        STORM("tempesta", 20 * 120),
        HEARTS("cuori", 20 * 150),
        DANCE("ballo", 20 * 120);

        public static final Kind[] ALL = values();
        public final String command;
        public final int duration;

        Kind(String command, int duration) {
            this.command = command;
            this.duration = duration;
        }

        public static Kind byCommand(String s) {
            for (Kind k : ALL) {
                if (k.command.equals(s)) {
                    return k;
                }
            }
            return null;
        }

        public String key() {
            return "filorosso.event." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    public record State(Kind kind, long end, List<UUID> extra, boolean swapped) {
        public static final State NONE = new State(null, 0L, List.of(), false);
    }

    private static State state = State.NONE;
    private static long nextAt = -1;
    private static Kind last;
    private static final java.util.Map<Integer, Long> STORM_WARNED = new java.util.HashMap<>();

    public static State state() {
        return state;
    }

    public static double length(MinecraftServer server) {
        double base = server.overworld().getGameRules().get(ModRules.LENGTH);
        return state.kind() == Kind.SHORT ? Math.min(3.0, base) : base;
    }

    public static boolean active(Kind kind) {
        return state.kind() == kind;
    }

    private static long interval(MinecraftServer server) {
        return server.overworld().getGameRules().get(ModRules.EVENT_MINUTES) * 1200L;
    }

    /** Couples with both partners online. */
    private static List<Couple> complete(MinecraftServer server) {
        List<Couple> list = new ArrayList<>();
        for (Couple c : CoupleBook.get(server).couples) {
            if (Links.usable(Bonds.player(server, c.a)) && Links.usable(Bonds.player(server, c.b))) {
                list.add(c);
            }
        }
        return list;
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (nextAt < 0) {
            nextAt = now + interval(server);
        }
        if (state.kind() != null) {
            if (now >= state.end()) {
                end(server);
            } else if (state.kind() == Kind.ROMANCE && now % 20 == 0) {
                for (Links.Pair p : Links.active(server)) {
                    Vec3 mid = p.a().position().add(p.b().position()).scale(0.5);
                    ((ServerLevel) p.a().level()).sendParticles(ParticleTypes.HEART, mid.x, mid.y + 2.0, mid.z, 2, 1.5, 0.5, 1.5, 0.0);
                }
            } else if (state.kind() == Kind.STORM) {
                storm(server, now);
            } else if (state.kind() == Kind.HEARTS) {
                HeartRain.tick(server, now);
            } else if (state.kind() == Kind.DANCE) {
                Dance.tick(server, now);
            }
            return;
        }
        if (!server.overworld().getGameRules().get(ModRules.EVENTS) || Boss.fighting(server)) {
            nextAt = Math.max(nextAt, now + 1200);
            return;
        }
        if (now >= nextAt) {
            List<Kind> options = new ArrayList<>();
            int couples = complete(server).size();
            for (Kind k : Kind.ALL) {
                if (k == last || couples == 0) {
                    continue;
                }
                if ((k == Kind.CHAIN || k == Kind.SWAP) && couples < 2) {
                    continue;
                }
                options.add(k);
            }
            if (options.isEmpty() || !start(server, options.get(server.overworld().getRandom().nextInt(options.size())))) {
                nextAt = now + 1200;
            }
        }
    }

    public static boolean start(MinecraftServer server, Kind kind) {
        List<Couple> couples = complete(server);
        if (couples.isEmpty() || ((kind == Kind.CHAIN || kind == Kind.SWAP) && couples.size() < 2)) {
            return false;
        }
        long now = server.overworld().getGameTime();
        java.util.Collections.shuffle(couples, new java.util.Random(now));
        List<UUID> extra = new ArrayList<>();
        if (kind == Kind.CHAIN) {
            for (int i = 0; i < couples.size(); i++) {
                Couple c = couples.get(i);
                Couple n = couples.get((i + 1) % couples.size());
                if (couples.size() == 2 && i == 1) {
                    break;
                }
                extra.add(c.b);
                extra.add(n.a);
            }
        } else if (kind == Kind.SWAP) {
            int i = 0;
            for (; i + 1 < couples.size(); i += 2) {
                Couple c = couples.get(i);
                Couple n = couples.get(i + 1);
                extra.add(c.a);
                extra.add(n.b);
                extra.add(n.a);
                extra.add(c.b);
            }
            if (i < couples.size()) {
                Couple c = couples.get(i);
                extra.add(c.a);
                extra.add(c.b);
            }
        }
        state = new State(kind, now + kind.duration, extra, kind == Kind.SWAP);
        broadcast(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Bonds.title(p, Component.translatable(kind.key()).withStyle(ChatFormatting.LIGHT_PURPLE),
                    Component.translatable(kind.key() + ".desc").withStyle(ChatFormatting.GRAY), 10, 80, 20);
            Bonds.sound(p, ModSounds.EVENT, 1.0F);
        }
        server.getPlayerList().broadcastSystemMessage(Component.empty().append(Text.icon(Text.STAR, 0xFFD030)).append(Component.literal(" "))
                .append(Component.translatable("filorosso.msg.event", Component.translatable(kind.key()).withStyle(ChatFormatting.LIGHT_PURPLE),
                        kind.duration / 1200)).append(Component.literal(" ")).append(Component.translatable(kind.key() + ".desc").withStyle(ChatFormatting.GRAY)), false);
        if (kind == Kind.SWAP || kind == Kind.CHAIN) {
            for (int i = 0; i + 1 < extra.size(); i += 2) {
                ServerPlayer a = Bonds.player(server, extra.get(i));
                ServerPlayer b = Bonds.player(server, extra.get(i + 1));
                if (a != null && b != null && (a.level() != b.level() || a.distanceTo(b) > length(server) + 2.0)) {
                    Tether.reunite(a, b, now);
                }
            }
        }
        return true;
    }

    public static void end(MinecraftServer server) {
        Kind kind = state.kind();
        if (kind == null) {
            return;
        }
        long now = server.overworld().getGameTime();
        state = State.NONE;
        last = kind;
        Dance.stop();
        nextAt = now + interval(server);
        broadcast(server);
        double length = length(server);
        for (Couple c : complete(server)) {
            ServerPlayer a = Bonds.player(server, c.a);
            ServerPlayer b = Bonds.player(server, c.b);
            if (c.cutUntil <= now && (a.level() != b.level() || a.distanceTo(b) > length + 2.0)) {
                Tether.reunite(a, b, now);
                for (ServerPlayer p : new ServerPlayer[]{a, b}) {
                    p.sendSystemMessage(Component.translatable("filorosso.msg.reunited").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
            }
        }
        server.getPlayerList().broadcastSystemMessage(Component.translatable("filorosso.msg.event_end",
                Component.translatable(kind.key())).withStyle(ChatFormatting.GRAY), false);
    }

    public static Net.WorldState payload(MinecraftServer server) {
        return new Net.WorldState(state.kind() == null ? -1 : state.kind().ordinal(), state.end(), (float) length(server),
                state.extra(), state.swapped());
    }

    public static void broadcast(MinecraftServer server) {
        Net.WorldState ws = payload(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Bonds.send(p, ws);
        }
    }

    public static void sendTo(ServerPlayer player) {
        Bonds.send(player, payload(player.level().getServer()));
    }

    /** Lightning runs along the threads of the couples who are out in the open. */
    private static void storm(MinecraftServer server, long now) {
        for (Links.Pair pair : Links.active(server)) {
            if (pair.couple() == null || pair.a().level() != pair.b().level()) {
                continue;
            }
            ServerLevel level = (ServerLevel) pair.a().level();
            Vec3 mid = pair.a().position().add(pair.b().position()).scale(0.5);
            BlockPos top = BlockPos.containing(mid.x, mid.y + 1.5, mid.z);
            boolean open = level.canSeeSky(top) && level.canSeeSky(pair.a().blockPosition().above()) && level.canSeeSky(pair.b().blockPosition().above());
            int id = pair.couple().id;
            Long warned = STORM_WARNED.get(id);
            if (warned == null) {
                if (open && level.getRandom().nextInt(160) == 0) {
                    STORM_WARNED.put(id, now);
                    level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.WEATHER, 0.6F, 2.0F);
                    for (ServerPlayer p : new ServerPlayer[]{pair.a(), pair.b()}) {
                        p.sendSystemMessage(Component.translatable("filorosso.msg.storm_warning").withStyle(ChatFormatting.YELLOW), true);
                    }
                }
            } else if (now - warned >= 40) {
                STORM_WARNED.remove(id);
                if (!open) {
                    continue;
                }
                LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                if (bolt != null) {
                    bolt.setPos(mid.x, mid.y, mid.z);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                for (ServerPlayer p : new ServerPlayer[]{pair.a(), pair.b()}) {
                    p.hurtServer(level, level.damageSources().source(ModDamage.STORM), 3.0F);
                }
            }
        }
    }
}
