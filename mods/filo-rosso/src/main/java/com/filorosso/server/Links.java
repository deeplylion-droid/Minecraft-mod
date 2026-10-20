package com.filorosso.server;

import com.filorosso.data.Couple;
import com.filorosso.data.CoupleBook;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The threads that exist right now: every couple whose partners are both online (and whose thread
 * is not cut), changed by the events (swapped partners, everybody in one chain, freedom).
 */
public final class Links {
    private Links() {
    }

    /** A thread between two online players. {@code couple} is null for the extra threads of the events. */
    public record Pair(ServerPlayer a, ServerPlayer b, Couple couple) {
        public ServerPlayer other(ServerPlayer p) {
            return p == this.a ? this.b : this.a;
        }
    }

    public static List<Pair> active(MinecraftServer server) {
        List<Pair> pairs = new ArrayList<>();
        Events.State event = Events.state();
        if (event.kind() == Events.Kind.FREEDOM) {
            return pairs;
        }
        long now = server.overworld().getGameTime();
        CoupleBook book = CoupleBook.get(server);
        if (!event.swapped()) {
            for (Couple c : book.couples) {
                if (c.cutUntil > now) {
                    continue;
                }
                ServerPlayer a = server.getPlayerList().getPlayer(c.a);
                ServerPlayer b = server.getPlayerList().getPlayer(c.b);
                if (usable(a) && usable(b)) {
                    pairs.add(new Pair(a, b, c));
                }
            }
        }
        List<UUID> extra = event.extra();
        for (int i = 0; i + 1 < extra.size(); i += 2) {
            ServerPlayer a = server.getPlayerList().getPlayer(extra.get(i));
            ServerPlayer b = server.getPlayerList().getPlayer(extra.get(i + 1));
            if (usable(a) && usable(b)) {
                pairs.add(new Pair(a, b, null));
            }
        }
        return pairs;
    }

    public static boolean usable(ServerPlayer p) {
        return p != null && !p.isSpectator() && !p.isRemoved();
    }
}
