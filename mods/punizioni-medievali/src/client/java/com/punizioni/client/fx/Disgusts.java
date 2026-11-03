package com.punizioni.client.fx;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/** Who just ate gruel (the server says so): their face pulls a grimace for a second (Poses). */
public final class Disgusts {
    private Disgusts() {
    }

    /** How long the grimace lasts (ticks). */
    public static final float LENGTH = 30.0F;

    private static final Map<Integer, Long> WHEN = new HashMap<>();

    public static void received(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            WHEN.put(entityId, mc.level.getGameTime());
            if (WHEN.size() > 64) {
                long now = mc.level.getGameTime();
                WHEN.values().removeIf(t -> now - t > 200L);
            }
        }
    }

    /** Ticks since this entity last ate gruel, or a big number. */
    public static float since(int entityId, long now, float partial) {
        Long t = WHEN.get(entityId);
        return t == null ? 1000.0F : now - t + partial;
    }

    public static void reset() {
        WHEN.clear();
    }
}
