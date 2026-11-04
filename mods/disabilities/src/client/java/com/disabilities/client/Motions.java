package com.disabilities.client;

import com.disabilities.net.MotionKind;

import java.util.HashMap;
import java.util.Map;

/** Body movements in progress, by entity id (the alien hand slapping, CPR, a hand to the ear...). */
public final class Motions {
    private Motions() {
    }

    public record Moving(MotionKind kind, long start, int arg) {
    }

    private static final Map<Integer, Moving> MOVING = new HashMap<>();

    public static void start(int entityId, MotionKind kind, int arg) {
        if (kind != null) {
            MOVING.put(entityId, new Moving(kind, Me.now(), arg));
        }
    }

    public static Moving get(int entityId) {
        Moving m = MOVING.get(entityId);
        if (m == null) {
            return null;
        }
        if (Me.now() - m.start() >= m.kind().ticks) {
            MOVING.remove(entityId);
            return null;
        }
        return m;
    }

    public static void clear() {
        MOVING.clear();
    }
}
