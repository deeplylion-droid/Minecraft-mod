package com.infezione.client;

import com.infezione.client.fx.SymptomFx;
import com.infezione.client.hud.InfectionHud;
import com.infezione.infection.Change;
import com.infezione.infection.Infection;
import com.infezione.infection.Stages;
import com.infezione.infection.Symptom;
import com.infezione.network.StatePacket;
import com.infezione.network.SymptomPacket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** What this client knows of the infections: every player's stage, the local player's clock, the episodes going on. */
public final class ClientInfections {
    /** The local player's infection, as the server last said (the time left counts down here between updates). */
    public static final class Self {
        public int stage;
        public boolean paused;
        public int ticksLeft;
        public int stageTicks = 1;

        /** How far into the stage, 0 to 1. */
        public float progress(float partialTick) {
            if (!Stages.progresses(stage)) {
                return 1.0F;
            }
            float left = paused ? ticksLeft : Math.max(0, ticksLeft - partialTick);
            return 1.0F - left / Math.max(1, stageTicks);
        }
    }

    /** An episode going on: what, since when (in this client's ticks), and a number for its randomness. */
    public record Episode(Symptom symptom, long start, int seed) {
        /** Ticks since it started. */
        public float time(float partialTick) {
            return clock - start + partialTick;
        }

        public boolean over() {
            return clock - start > symptom.ticks;
        }
    }

    public static final Self SELF = new Self();
    private static final Map<Integer, Episode> EPISODES = new HashMap<>();
    private static long clock;
    private static int seeds;

    private ClientInfections() {
    }

    /** Ticks of this client (they stop while the game is paused). */
    public static long clock() {
        return clock;
    }

    public static int stage(Entity entity) {
        return entity instanceof Player ? Infection.CLIENT_STAGES.getOrDefault(entity.getUUID(), Stages.HEALTHY) : Stages.HEALTHY;
    }

    public static int stage(UUID player) {
        return Infection.CLIENT_STAGES.getOrDefault(player, Stages.HEALTHY);
    }

    /** The episode going on for an entity, or null. */
    @Nullable
    public static Episode episode(Entity entity) {
        Episode episode = EPISODES.get(entity.getId());
        return episode == null || episode.over() ? null : episode;
    }

    public static void onState(StatePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        int before = stage(packet.player());
        if (packet.stage() > Stages.HEALTHY) {
            Infection.CLIENT_STAGES.put(packet.player(), packet.stage());
        } else {
            Infection.CLIENT_STAGES.remove(packet.player());
            EPISODES.remove(packet.entity());
        }
        Change change = Change.byId(packet.change());
        if (mc.player != null && packet.player().equals(mc.player.getUUID())) {
            int selfBefore = SELF.stage;
            SELF.stage = packet.stage();
            SELF.paused = packet.paused();
            SELF.ticksLeft = packet.ticksLeft();
            SELF.stageTicks = Math.max(1, packet.stageTicks());
            if (change != Change.NONE && change != Change.PAUSE) {
                InfectionHud.onChange(selfBefore, packet.stage(), change);
            }
        } else if (mc.level != null && change == Change.CURED && before > Stages.HEALTHY) {
            Entity entity = mc.level.getEntity(packet.entity());
            if (entity != null) {
                SymptomFx.cured(entity);
            }
        }
    }

    public static void onSymptom(SymptomPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Symptom symptom = Symptom.byId(packet.symptom());
        EPISODES.put(packet.entity(), new Episode(symptom, clock, seeds++ * 7919));
        Entity entity = mc.level.getEntity(packet.entity());
        if (entity != null) {
            SymptomFx.start(entity, symptom);
        }
    }

    /** Every client tick (not while paused). */
    public static void tick(Minecraft mc) {
        clock++;
        if (SELF.ticksLeft > 0 && !SELF.paused && Stages.progresses(SELF.stage)) {
            SELF.ticksLeft--;
        }
        EPISODES.values().removeIf(e -> clock - e.start() > e.symptom().ticks + 40);
    }

    /** Leaving the server: forget everything. */
    public static void clear() {
        Infection.CLIENT_STAGES.clear();
        EPISODES.clear();
        SELF.stage = Stages.HEALTHY;
        SELF.paused = false;
        SELF.ticksLeft = 0;
    }
}
