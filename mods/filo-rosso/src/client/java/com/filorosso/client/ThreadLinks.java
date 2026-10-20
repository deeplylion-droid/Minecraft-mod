package com.filorosso.client;

import com.filorosso.data.BondType;
import com.filorosso.data.Link;
import com.filorosso.data.ModData;
import com.filorosso.net.Net;
import com.filorosso.server.Events;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The threads to draw and to obey on this client: the same rules as the server's {@code Links}. */
public final class ThreadLinks {
    private ThreadLinks() {
    }

    /**
     * A thread between two loaded players.
     *
     * @param extra a thread of an event (chain, swapped partners), not the couple's own
     */
    public record Tie(Player a, Player b, int color, int level, BondType type, boolean extra, long bondedAt) {
        public Player other(Player p) {
            return p == this.a ? this.b : this.a;
        }

        public boolean has(Player p) {
            return p == this.a || p == this.b;
        }
    }

    private static boolean usable(Player p) {
        return p != null && !p.isSpectator() && !p.isRemoved();
    }

    public static List<Tie> all(ClientLevel level) {
        List<Tie> ties = new ArrayList<>();
        Net.WorldState ws = ClientState.world;
        if (ClientState.event() == Events.Kind.FREEDOM) {
            return ties;
        }
        long now = level.getGameTime();
        if (!ws.swapped()) {
            for (Player p : level.players()) {
                Link link = ModData.link(p);
                if (link.partner().isEmpty() || link.cut(now) || !usable(p)) {
                    continue;
                }
                UUID other = link.partner().get();
                if (p.getUUID().compareTo(other) > 0) {
                    continue;
                }
                Player q = level.getPlayerByUUID(other);
                if (usable(q)) {
                    ties.add(new Tie(p, q, link.color(), link.level().ordinal(), link.bondType(), false, link.bondedAt()));
                }
            }
        }
        List<UUID> extra = ws.extra();
        for (int i = 0; i + 1 < extra.size(); i += 2) {
            Player p = level.getPlayerByUUID(extra.get(i));
            Player q = level.getPlayerByUUID(extra.get(i + 1));
            if (usable(p) && usable(q)) {
                Link link = ModData.link(p);
                ties.add(new Tie(p, q, link.color(), link.level().ordinal(), BondType.CLASSIC, true, 0L));
            }
        }
        return ties;
    }

    public static List<Tie> of(Player player) {
        List<Tie> mine = new ArrayList<>();
        if (!(player.level() instanceof ClientLevel level)) {
            return mine;
        }
        for (Tie t : all(level)) {
            if (t.has(player)) {
                mine.add(t);
            }
        }
        return mine;
    }
}
