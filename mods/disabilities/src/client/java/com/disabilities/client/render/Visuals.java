package com.disabilities.client.render;

import com.disabilities.condition.Condition;
import com.disabilities.gesture.Gesture;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.network.chat.Component;

/** What a player's conditions look like this frame, stored on their render state. */
public final class Visuals {
    public static final RenderStateDataKey<Visuals> KEY = RenderStateDataKey.create(() -> "disabilities:visuals");

    public int mask;
    public boolean asleep;
    public boolean frozen;
    public boolean winded;
    public boolean bleeding;
    public boolean confused;
    public boolean breathless;
    public boolean passenger;
    /** Seconds-ish clock for idle animations. */
    public float time;
    /** Random per player, so tremors do not move in sync. */
    public long seed;
    public Gesture gesture;
    /** Ticks since the gesture started. */
    public float gestureTime;
    public Component bubble;
    /** 0 = left, 1 = right: the arm a one-armed player is missing (the off-hand side). */
    public boolean missingLeftArm = true;

    public boolean has(Condition condition) {
        return (this.mask & condition.bit()) != 0;
    }
}
