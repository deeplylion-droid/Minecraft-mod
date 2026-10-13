package com.wurstel.client.anim;

import java.util.Random;

/**
 * Animation state of one giant würstel (one instance per entity wearing the belt).
 *
 * <p>Pure math with no Minecraft classes: it is advanced once per client tick with a few numbers
 * read from the entity and sampled every frame by the renderer.</p>
 *
 * <p>A slap swings the root (at the belt) with a stiff spring, while a softer spring bends the
 * sausage: when the root accelerates the tip lags behind, when it stops the tip overshoots and
 * wobbles, like a floppy whip. On top of that every point along it (u = 0 at the belt, u = 1 at
 * the tip) replays what happened a moment earlier, so motion travels down the würstel as a wave.
 * Head turns, jumps and footsteps feed the same chain, so it also jiggles while walking.</p>
 */
public final class WurstelAnimState {

    /** Physics sub-steps per game tick (20 ticks per second). */
    public static final int SUBSTEPS = 4;
    private static final int HISTORY = 128; // 32 ticks of samples
    private static final float DT = 1f / (20f * SUBSTEPS);
    private static final float DEG = (float) (Math.PI / 180.0);

    // ---------- slap drive ----------
    /** Ticks spent pulling the würstel back before the strike. */
    public static final float WINDUP_TICKS = 1.2f;
    /** Ticks the strike target is held on the follow-through side. */
    public static final float STRIKE_TICKS = 1.8f;
    /** Ticks after the swing starts when the würstel passes through the target (sound/particles). */
    public static final int IMPACT_TICKS = 3;

    private static final float ROOT_OMEGA = 40f;   // rad/s, stiffness of the root swing
    private static final float ROOT_ZETA = 0.55f;
    private static final float ROOT_K = ROOT_OMEGA * ROOT_OMEGA;
    private static final float ROOT_C = 2f * ROOT_ZETA * ROOT_OMEGA;

    private static final float BEND_OMEGA = 16f;   // rad/s, the soft bending of the sausage
    private static final float BEND_ZETA = 0.2f;
    private static final float BEND_K = BEND_OMEGA * BEND_OMEGA;
    private static final float BEND_C = 2f * BEND_ZETA * BEND_OMEGA;
    private static final float BEND_INERTIA = 0.6f; // how much root acceleration bends it
    private static final float BEND_SHAPE = 1.25f;  // bend grows towards the tip as u^BEND_SHAPE
    private static final float MAX_BEND = 80f * DEG;

    private static final float WINDUP_AMP = 70f * DEG;
    private static final float STRIKE_AMP = 42f * DEG;
    private static final float JITTER = 12f; // degrees of random variation per slap

    /**
     * Wind-up directions in the view plane (degrees, 0 = to the right, 90 = up), cycled through so
     * that consecutive slaps come from different angles: right, left, high-right, low-left,
     * high-left, low-right, overhead, left.
     */
    private static final float[] SLAP_ANGLES = {0f, 180f, 65f, 245f, 115f, 295f, 90f, 200f};

    // ---------- softness ----------
    private static final float SLAP_DELAY = 0.6f;   // ticks for the root swing to reach the tip
    private static final float BEND_DELAY = 0.3f;   // ticks for the bend to reach the tip
    private static final float LOOK_DELAY = 1.6f;   // ticks the tip trails behind head turns
    private static final float WOBBLE_DELAY = 2.4f; // ticks for bounce/sway to reach the tip
    private static final float LOOK_GAIN = 0.8f;
    private static final float MAX_LOOK_LAG = 50f;  // degrees

    private static final float BOUNCE_K = 15f * 15f;
    private static final float BOUNCE_C = 2f * 0.16f * 15f;
    private static final float SWAY_K = 11f * 11f;
    private static final float SWAY_C = 2f * 0.22f * 11f;
    private static final float JUMP_KICK = 14f;      // rad/s per block/tick of vertical velocity change
    private static final float BOB_AMP = 7f * DEG;   // footstep bounce
    private static final float SWAY_AMP = 6f * DEG;  // left/right sway while walking
    private static final float IDLE_AMP = 1.2f * DEG;
    private static final float MAX_WOBBLE = 50f * DEG;
    private static final float MAX_ANGLE = 150f * DEG;

    private final float[] hSlapYaw = new float[HISTORY];
    private final float[] hSlapPitch = new float[HISTORY];
    private final float[] hBendYaw = new float[HISTORY];
    private final float[] hBendPitch = new float[HISTORY];
    private final float[] hLookYaw = new float[HISTORY];
    private final float[] hLookPitch = new float[HISTORY];
    private final float[] hBounce = new float[HISTORY];
    private final float[] hSway = new float[HISTORY];

    private long samples;          // number of samples written so far
    private int tick;              // number of ticks simulated so far
    private int slapStart = -1000; // tick at which the current slap started
    private int slapCount;
    private float dirX, dirY, windAmp, strikeAmp;

    private float slapYaw, slapPitch, slapYawVel, slapPitchVel;
    private float bendYaw, bendPitch, bendYawVel, bendPitchVel;
    private float bounce, bounceVel, sway, swayVel;
    private float lookYaw, lookPitch, prevVy;
    private boolean initialized;

    /** Starts a new slap. Call before {@link #tick} in the tick the swing was detected. */
    public void startSlap(Random random) {
        float angle = SLAP_ANGLES[slapCount++ % SLAP_ANGLES.length] + (random.nextFloat() * 2f - 1f) * JITTER;
        startSlap(angle);
    }

    /** Starts a slap whose wind-up points at {@code angleDeg} (0 = right, 90 = up). */
    public void startSlap(float angleDeg) {
        float a = angleDeg * DEG;
        dirX = (float) Math.cos(a);
        dirY = (float) Math.sin(a);
        // Less wind-up downwards (the floor is there) and a shorter follow-through when striking down.
        windAmp = WINDUP_AMP * (1f - 0.4f * Math.max(0f, -dirY));
        strikeAmp = STRIKE_AMP * (1f - 0.3f * Math.max(0f, dirY));
        slapStart = tick;
    }

    /** Ticks since the last slap started (large if none). */
    public int ticksSinceSlap() {
        return tick - slapStart;
    }

    /**
     * Advances the simulation by one game tick.
     *
     * @param headYaw   world head yaw in degrees (Minecraft convention, grows when turning right)
     * @param pitch     head pitch in degrees (Minecraft convention, positive = looking down)
     * @param vy        vertical velocity in blocks per tick
     * @param walkPos   walk animation position
     * @param walkSpeed walk animation speed (0 when standing still)
     */
    public void tick(float headYaw, float pitch, float vy, float walkPos, float walkSpeed) {
        if (!initialized) {
            initialized = true;
            lookYaw = headYaw;
            lookPitch = pitch;
            prevVy = vy;
            java.util.Arrays.fill(hLookYaw, lookYaw);
            java.util.Arrays.fill(hLookPitch, lookPitch);
        }
        tick++;

        float prevYaw = lookYaw;
        float prevPitch = lookPitch;
        lookYaw += wrapDegrees(headYaw - lookYaw); // keep the yaw continuous
        lookPitch = pitch;

        // Jumping or landing jolts the base, the tip lags behind.
        float ay = clamp(vy - prevVy, -1f, 1f);
        prevVy = vy;
        bounceVel -= ay * JUMP_KICK;

        float speed = clamp(walkSpeed, 0f, 1f);
        float bobTarget = BOB_AMP * speed * (float) Math.cos(walkPos * 1.3324f)
                + IDLE_AMP * (float) Math.sin(tick * 0.08f);
        float swayTarget = SWAY_AMP * speed * (float) Math.sin(walkPos * 0.6662f);

        for (int s = 1; s <= SUBSTEPS; s++) {
            float f = s / (float) SUBSTEPS;
            float tau = (tick - 1 - slapStart) + f; // ticks since the slap began

            float targetYaw = 0f;
            float targetPitch = 0f;
            if (tau < WINDUP_TICKS) {
                targetYaw = dirX * windAmp;
                targetPitch = dirY * windAmp;
            } else if (tau < WINDUP_TICKS + STRIKE_TICKS) {
                targetYaw = -dirX * strikeAmp;
                targetPitch = -dirY * strikeAmp;
            }

            // Semi-implicit Euler springs (stable at this step size).
            float accYaw = ROOT_K * (targetYaw - slapYaw) - ROOT_C * slapYawVel;
            float accPitch = ROOT_K * (targetPitch - slapPitch) - ROOT_C * slapPitchVel;
            slapYawVel += accYaw * DT;
            slapPitchVel += accPitch * DT;
            slapYaw += slapYawVel * DT;
            slapPitch += slapPitchVel * DT;

            // The soft body lags behind when the root accelerates and overshoots when it stops.
            bendYawVel += (-BEND_K * bendYaw - BEND_C * bendYawVel - BEND_INERTIA * accYaw) * DT;
            bendPitchVel += (-BEND_K * bendPitch - BEND_C * bendPitchVel - BEND_INERTIA * accPitch) * DT;
            bendYaw = clamp(bendYaw + bendYawVel * DT, -MAX_BEND, MAX_BEND);
            bendPitch = clamp(bendPitch + bendPitchVel * DT, -MAX_BEND, MAX_BEND);

            bounceVel += (BOUNCE_K * (bobTarget - bounce) - BOUNCE_C * bounceVel) * DT;
            bounce = clamp(bounce + bounceVel * DT, -MAX_WOBBLE, MAX_WOBBLE);
            swayVel += (SWAY_K * (swayTarget - sway) - SWAY_C * swayVel) * DT;
            sway = clamp(sway + swayVel * DT, -MAX_WOBBLE, MAX_WOBBLE);

            int i = (int) (samples % HISTORY);
            hSlapYaw[i] = slapYaw;
            hSlapPitch[i] = slapPitch;
            hBendYaw[i] = bendYaw;
            hBendPitch[i] = bendPitch;
            hLookYaw[i] = prevYaw + (lookYaw - prevYaw) * f;
            hLookPitch[i] = prevPitch + (lookPitch - prevPitch) * f;
            hBounce[i] = bounce;
            hSway[i] = sway;
            samples++;
        }
    }

    /**
     * Bend of the würstel at position {@code u} (0 = belt, 1 = tip), relative to the direction the
     * wearer is looking.
     *
     * @param out receives {yaw, pitch} in radians; yaw positive = towards the wearer's right,
     *            pitch positive = up
     */
    public void sample(float partialTick, float u, float[] out) {
        if (samples < 1) {
            out[0] = 0f;
            out[1] = 0f;
            return;
        }
        double now = (samples - 1 - SUBSTEPS) + (double) partialTick * SUBSTEPS;

        double tSlap = now - u * SLAP_DELAY * SUBSTEPS;
        double tBend = now - u * BEND_DELAY * SUBSTEPS;
        float shape = (float) Math.pow(u, BEND_SHAPE);
        float yaw = get(hSlapYaw, tSlap) + get(hBendYaw, tBend) * shape;
        float pitch = get(hSlapPitch, tSlap) + get(hBendPitch, tBend) * shape;

        double tLook = now - u * LOOK_DELAY * SUBSTEPS;
        float lagYaw = clamp(get(hLookYaw, tLook) - get(hLookYaw, now), -MAX_LOOK_LAG, MAX_LOOK_LAG);
        float lagUp = clamp(get(hLookPitch, now) - get(hLookPitch, tLook), -MAX_LOOK_LAG, MAX_LOOK_LAG);
        yaw += lagYaw * DEG * LOOK_GAIN;
        pitch += lagUp * DEG * LOOK_GAIN;

        double tWobble = now - u * WOBBLE_DELAY * SUBSTEPS;
        float w = u * (float) Math.sqrt(u); // u^1.5: the tip wobbles the most
        pitch += get(hBounce, tWobble) * w;
        yaw += get(hSway, tWobble) * w;

        out[0] = clamp(yaw, -MAX_ANGLE, MAX_ANGLE);
        out[1] = clamp(pitch, -85f * DEG, 85f * DEG);
    }

    private float get(float[] channel, double t) {
        double min = Math.max(0, samples - HISTORY + 1);
        double max = samples - 1;
        if (t < min) t = min;
        if (t > max) t = max;
        long i0 = (long) Math.floor(t);
        float f = (float) (t - i0);
        float a = channel[(int) (i0 % HISTORY)];
        if (f <= 0f) return a;
        float b = channel[(int) ((i0 + 1) % HISTORY)];
        return a + (b - a) * f;
    }

    private static float wrapDegrees(float deg) {
        float d = deg % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
