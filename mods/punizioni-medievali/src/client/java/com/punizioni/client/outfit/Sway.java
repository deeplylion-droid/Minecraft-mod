package com.punizioni.client.outfit;

import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * How the loose bits of a costume swing (the jester's hat points and bells, the rags and the
 * chains of the prisoner): three damped springs per player, pushed by how the body moves in its
 * own frame. Starting to run throws them back, stopping throws them forward, turning throws them
 * out, a jump makes them droop and the fall makes them float, the landing slaps them down.
 */
public final class Sway {
    /** Fore and aft (positive: swung back), sideways (positive: to the body's right), up and down (positive: lifted). */
    public float pitch, roll, lift;
    private float vPitch, vRoll, vLift;
    private double lastX, lastY, lastZ;
    private double velF, velR, velY;
    private float lastYaw;
    private float lastTime = Float.NaN;
    private long seenFrame;

    private static final Map<Integer, Sway> ALL = new HashMap<>();
    private static long frame;

    private static final float STIFF = 0.09F;
    private static final float DAMP = 0.16F;

    public static Sway of(int entityId) {
        Sway s = ALL.computeIfAbsent(entityId, id -> new Sway());
        s.seenFrame = frame;
        return s;
    }

    /** Once a frame: who was not drawn for a while is forgotten. */
    public static void endFrame() {
        frame++;
        if (frame % 200 == 0) {
            for (Iterator<Sway> it = ALL.values().iterator(); it.hasNext(); ) {
                if (frame - it.next().seenFrame > 600) {
                    it.remove();
                }
            }
        }
    }

    public static void reset() {
        ALL.clear();
    }

    /**
     * Moves the springs on to {@code time} (ticks, with the partial tick) with the body at (x, y, z),
     * turned {@code yaw} degrees. Several updates in one frame (shadow passes) count once.
     */
    public void update(float time, double x, double y, double z, float yaw) {
        if (Float.isNaN(this.lastTime) || time - this.lastTime > 6.0F || time < this.lastTime) {
            this.lastTime = time;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.lastYaw = yaw;
            this.velF = this.velR = this.velY = 0.0;
            return;
        }
        float dt = time - this.lastTime;
        if (dt < 0.02F) {
            return;
        }
        double dx = (x - this.lastX) / dt;
        double dy = (y - this.lastY) / dt;
        double dz = (z - this.lastZ) / dt;
        if (dx * dx + dy * dy + dz * dz > 4.0) {
            // a teleport: nothing swings for it
            dx = dy = dz = 0.0;
        }
        float r = yaw * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(r);
        double fz = Mth.cos(r);
        double vf = dx * fx + dz * fz;
        double vr = dx * -fz + dz * fx;
        double af = (vf - this.velF) / dt;
        double ar = (vr - this.velR) / dt;
        double ay = (dy - this.velY) / dt;
        float turn = Mth.wrapDegrees(yaw - this.lastYaw) / dt;
        this.velF = vf;
        this.velR = vr;
        this.velY = dy;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.lastYaw = yaw;
        this.lastTime = time;

        // the pushes: inertia, so the loose bits go the other way
        float pushPitch = (float) Mth.clamp(af * 2.2 + vf * 0.35, -0.6, 0.6);
        float pushRoll = (float) Mth.clamp(-ar * 2.2 + turn * 0.004, -0.6, 0.6);
        float pushLift = (float) Mth.clamp(-ay * 1.6, -0.7, 0.7);
        int steps = Math.max(1, Math.min(6, Mth.ceil(dt / 0.25F)));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            this.vPitch += (pushPitch - STIFF * 4.0F * this.pitch - DAMP * 2.0F * this.vPitch) * h;
            this.vRoll += (pushRoll - STIFF * 4.0F * this.roll - DAMP * 2.0F * this.vRoll) * h;
            this.vLift += (pushLift - STIFF * 5.0F * this.lift - DAMP * 2.0F * this.vLift) * h;
            this.pitch = Mth.clamp(this.pitch + this.vPitch * h, -1.2F, 1.2F);
            this.roll = Mth.clamp(this.roll + this.vRoll * h, -1.2F, 1.2F);
            this.lift = Mth.clamp(this.lift + this.vLift * h, -1.2F, 1.2F);
        }
    }
}
