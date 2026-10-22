package com.filorosso.util;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Particles around a player's body, sent to everyone but that player. */
public final class Fx {
    private Fx() {
    }

    /**
     * Particles around a player reach their eyes too: in first person a sparkle a hand away fills a
     * corner of the screen with a big square. These are for the others to see.
     */
    public static void forOthers(ServerLevel level, ServerPlayer body, ParticleOptions particle, double x, double y, double z, int count,
                                 double dx, double dy, double dz, double speed) {
        for (ServerPlayer p : level.players()) {
            if (p != body) {
                level.sendParticles(p, particle, false, false, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }
}
