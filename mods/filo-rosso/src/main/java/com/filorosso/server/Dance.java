package com.filorosso.server;

import com.filorosso.data.Couple;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Ballo di coppia": a little waltz plays round every couple; partners close together who both
 * crouch in time (two dips a second or more) dance, and their affinity rises.
 */
public final class Dance {
    private Dance() {
    }

    public static final double CLOSE = 3.5;
    /** Dips each partner needs in a second to count as dancing. */
    public static final int DIPS = 2;
    public static final int AFFINITY = 2;
    /** Semitones of the waltz (note block pitch: 0-24), one every {@link #BEAT} ticks, -1 = rest. */
    private static final int[] TUNE = {6, 10, 13, 10, 13, 10, 8, 11, 15, 11, 15, 11, 6, 10, 13, 18, 17, 15, 13, 11, 10, 8, -1, -1};
    private static final int[] BASS = {6, -1, -1, 6, -1, -1, 3, -1, -1, 3, -1, -1, 6, -1, -1, 1, -1, -1, 3, -1, -1, 6, -1, -1};
    private static final int BEAT = 6;

    private static final Map<UUID, Boolean> CROUCHED = new HashMap<>();
    private static final Map<UUID, Integer> COUNT = new HashMap<>();

    private static float pitch(int note) {
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }

    public static void tick(MinecraftServer server, long now) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean down = p.isShiftKeyDown();
            Boolean was = CROUCHED.put(p.getUUID(), down);
            if (down && was != null && !was) {
                COUNT.merge(p.getUUID(), 1, Integer::sum);
            }
        }
        boolean beat = now % BEAT == 0;
        int step = (int) ((now / BEAT) % TUNE.length);
        boolean second = now % 20 == 0;
        for (Links.Pair pair : Links.active(server)) {
            Couple couple = pair.couple();
            ServerPlayer a = pair.a();
            ServerPlayer b = pair.b();
            if (couple == null || a.level() != b.level() || !(a.level() instanceof ServerLevel level) || a.distanceTo(b) > CLOSE * 2.0) {
                continue;
            }
            Vec3 mid = a.position().add(b.position()).scale(0.5);
            if (beat) {
                if (TUNE[step] >= 0) {
                    level.playSound(null, mid.x, mid.y + 1.0, mid.z, SoundEvents.NOTE_BLOCK_BELL, SoundSource.RECORDS, 0.7F, pitch(TUNE[step]));
                }
                if (BASS[step] >= 0) {
                    level.playSound(null, mid.x, mid.y + 1.0, mid.z, SoundEvents.NOTE_BLOCK_BASS, SoundSource.RECORDS, 0.8F, pitch(BASS[step]));
                }
            }
            if (second) {
                int da = COUNT.getOrDefault(a.getUUID(), 0);
                int db = COUNT.getOrDefault(b.getUUID(), 0);
                if (da >= DIPS && db >= DIPS && a.distanceTo(b) <= CLOSE) {
                    Affinity.add(server, couple, AFFINITY, true);
                    for (ServerPlayer p : new ServerPlayer[]{a, b}) {
                        level.sendParticles(ParticleTypes.NOTE, p.getX(), p.getY() + 2.2, p.getZ(), 1, 0.0, 0.0, 0.0, level.getRandom().nextDouble());
                    }
                    level.sendParticles(ParticleTypes.HEART, mid.x, mid.y + 2.0, mid.z, 1, 0.3, 0.1, 0.3, 0.0);
                }
            }
        }
        if (second) {
            COUNT.clear();
        }
    }

    public static void stop() {
        CROUCHED.clear();
        COUNT.clear();
    }
}
