package com.frusta.motion;

/**
 * The whip when nobody is cracking it: a rope that hangs from the handle, drags on the ground behind
 * the player, swings when they turn and falls after a crack. Verlet integration with length
 * constraints, a bit of bending stiffness (more near the handle, like a real tapered whip) and
 * collisions with the ground only.
 */
public final class WhipRope {
    /** Solid ground: the top of the block at or right below a point, or NaN if there is none. */
    @FunctionalInterface
    public interface Ground {
        double floor(double x, double y, double z);
    }

    public static final double STEP = 1.0 / 120.0;
    private static final double GRAVITY = 20.0;
    private static final double AIR_DRAG = 0.996;
    private static final double GROUND_FRICTION = 0.82;
    private static final int ITERATIONS = 12;
    /** How hard the whip resists bending at the handle (once a step); it fades out quickly towards the tip. */
    private static final double STIFFNESS = 0.08;

    private final int n;
    private final double[] x;
    private final double[] y;
    private final double[] z;
    private final double[] px;
    private final double[] py;
    private final double[] pz;
    private final boolean[] onGround;
    private boolean ready;

    public WhipRope() {
        n = Whip.NODES;
        x = new double[n];
        y = new double[n];
        z = new double[n];
        px = new double[n];
        py = new double[n];
        pz = new double[n];
        onGround = new boolean[n];
    }

    public boolean isReady() {
        return ready;
    }

    public void invalidate() {
        ready = false;
    }

    /** Lays the whip down: from the handle straight down to the ground, then along it in {@code back}. */
    public void layDown(V3 hand, V3 back, double groundY, double length) {
        double segment = length / (n - 1);
        V3 dir = new V3(back.x(), 0, back.z()).normalize(new V3(0, 0, 1));
        double drop = Math.max(0, hand.y() - groundY - Whip.radius(0));
        for (int i = 0; i < n; i++) {
            double along = i * segment;
            V3 p;
            if (along <= drop) {
                p = hand.add(0, -along, 0);
            } else {
                p = new V3(hand.x(), hand.y() - drop, hand.z()).add(dir.scale(along - drop));
            }
            set(i, p, p);
        }
        ready = true;
    }

    /** Takes over from a hand-drawn move: positions now and one {@link #STEP} earlier (the speed). */
    public void takeOver(V3[] now, V3[] before) {
        for (int i = 0; i < n; i++) {
            set(i, now[i], before[i]);
        }
        ready = true;
    }

    /** Moves the whip by one {@link #STEP}, with its handle end at {@code hand} pointing along {@code handleDir}. */
    public void step(V3 hand, V3 handleDir, double length, Ground ground) {
        double segment = length / (n - 1);
        double dt2 = STEP * STEP;
        for (int i = 1; i < n; i++) {
            double drag = onGround[i] ? GROUND_FRICTION : AIR_DRAG;
            double vx = (x[i] - px[i]) * drag;
            double vy = (y[i] - py[i]) * AIR_DRAG;
            double vz = (z[i] - pz[i]) * drag;
            px[i] = x[i];
            py[i] = y[i];
            pz[i] = z[i];
            x[i] += vx;
            y[i] += vy - GRAVITY * dt2;
            z[i] += vz;
        }
        set(0, hand, hand);
        V3 d = handleDir.normalize(new V3(0, -1, 0));
        for (int it = 0; it < ITERATIONS; it++) {
            // The first segment leaves the handle along it, like the stiff butt of a real whip.
            pull(1, hand.x() + d.x() * segment, hand.y() + d.y() * segment, hand.z() + d.z() * segment, 0.5);
            for (int i = 0; i < n - 1; i++) {
                keepDistance(i, i + 1, segment);
            }
        }
        for (int i = 1; i < n - 1; i++) {
            double f = 1 - (double) i / (n - 1);
            double k = STIFFNESS * f * f * f * f;
            if (k > 1e-4) {
                pull(i, (x[i - 1] + x[i + 1]) * 0.5, (y[i - 1] + y[i + 1]) * 0.5, (z[i - 1] + z[i + 1]) * 0.5, k);
            }
        }
        for (int i = 1; i < n; i++) {
            double r = Whip.radius((double) i / (n - 1)) + 0.01;
            double floor = ground.floor(x[i], y[i], z[i]);
            onGround[i] = false;
            if (!Double.isNaN(floor) && y[i] < floor + r + 0.004) {
                if (y[i] < floor + r) {
                    y[i] = floor + r;
                    if (py[i] < y[i]) {
                        py[i] = y[i];
                    }
                }
                onGround[i] = true;
            }
        }
    }

    public V3 node(int i) {
        return new V3(x[i], y[i], z[i]);
    }

    /** Where the node was one {@link #STEP} ago. */
    public V3 previous(int i) {
        return new V3(px[i], py[i], pz[i]);
    }

    /** Moves every point by the same amount (the player was teleported or the world shifted). */
    public void shift(V3 by) {
        for (int i = 0; i < n; i++) {
            x[i] += by.x();
            y[i] += by.y();
            z[i] += by.z();
            px[i] += by.x();
            py[i] += by.y();
            pz[i] += by.z();
        }
    }

    private void set(int i, V3 now, V3 before) {
        x[i] = now.x();
        y[i] = now.y();
        z[i] = now.z();
        px[i] = before.x();
        py[i] = before.y();
        pz[i] = before.z();
    }

    private void pull(int i, double tx, double ty, double tz, double k) {
        x[i] += (tx - x[i]) * k;
        y[i] += (ty - y[i]) * k;
        z[i] += (tz - z[i]) * k;
    }

    private void keepDistance(int a, int b, double rest) {
        double dx = x[b] - x[a];
        double dy = y[b] - y[a];
        double dz = z[b] - z[a];
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1e-9) {
            return;
        }
        double diff = (dist - rest) / dist;
        if (a == 0) {
            // the handle end does not move: the whole correction goes to the other point
            x[b] -= dx * diff;
            y[b] -= dy * diff;
            z[b] -= dz * diff;
        } else {
            x[a] += dx * diff * 0.5;
            y[a] += dy * diff * 0.5;
            z[a] += dz * diff * 0.5;
            x[b] -= dx * diff * 0.5;
            y[b] -= dy * diff * 0.5;
            z[b] -= dz * diff * 0.5;
        }
    }
}
