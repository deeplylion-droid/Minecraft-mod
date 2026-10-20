package com.disabilities.client;

import com.disabilities.gesture.Gesture;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/** Speech bubbles and gestures in progress, by entity id (for drawing them over everyone's head). */
public final class Bubbles {
    private Bubbles() {
    }

    public record Bubble(Component text, long until) {
    }

    public record Gesturing(Gesture gesture, long start) {
    }

    private static final Map<Integer, Bubble> BUBBLES = new HashMap<>();
    private static final Map<Integer, Gesturing> GESTURES = new HashMap<>();

    public static void show(int entityId, Component text, int ticks) {
        BUBBLES.put(entityId, new Bubble(text, Me.now() + ticks));
    }

    public static void gesture(int entityId, Gesture gesture) {
        GESTURES.put(entityId, new Gesturing(gesture, Me.now()));
        show(entityId, Component.empty().append(com.disabilities.server.Chooser.badge(String.valueOf(gesture.glyph())))
                .append(Component.literal(" ")).append(gesture.displayName()), Gesture.DURATION);
    }

    public static Component bubble(int entityId) {
        Bubble b = BUBBLES.get(entityId);
        if (b == null) {
            return null;
        }
        if (Me.now() >= b.until()) {
            BUBBLES.remove(entityId);
            return null;
        }
        return b.text();
    }

    public static Gesturing gesturing(int entityId) {
        Gesturing g = GESTURES.get(entityId);
        if (g == null) {
            return null;
        }
        if (Me.now() - g.start() >= Gesture.DURATION) {
            GESTURES.remove(entityId);
            return null;
        }
        return g;
    }

    public static void clear() {
        BUBBLES.clear();
        GESTURES.clear();
    }
}
