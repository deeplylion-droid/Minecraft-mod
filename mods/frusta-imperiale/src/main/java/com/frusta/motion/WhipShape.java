package com.frusta.motion;

import javax.annotation.Nullable;

/**
 * Where every point of the whip is during a move. The moves are drawn by hand, not simulated, so a
 * crack looks the same on every client and lands exactly on the crack tick (see {@link Whip}).
 *
 * <p>A lash is a rolling loop: the part held in the hand stretches towards the target, the free end
 * comes over it, and the bend between the two (the loop) runs out towards the tip, getting tighter
 * and faster, until the tip flips over: the crack. If something is in the way, the loop smacks into
 * it instead. The grab is a lash whose free end wraps around the target; the Imperial Crack is a lash
 * thrown out of the spin over the head ({@link #twirl}).</p>
 */
public final class WhipShape {
    public enum Move { LASH, IMPERIAL, GRAB }

    /** Lash styles: overhead, from the right, backhand (from the left) and diagonal. */
    public static final int STYLES = 4;
    /** Roll of the loop around the aim for each style, degrees: where the free end comes from. */
    private static final double[] STYLE_ROLL = {0, -80, 80, -40};
    /** Radius of the loop when the stroke starts and when it reaches the tip, blocks. */
    private static final double LOOP_START = 0.55;
    private static final double LOOP_END = 0.07;

    /**
     * What is decided when a move starts.
     *
     * @param aim     unit look direction when the move started (the whip stays committed to it)
     * @param reach   distance from the eyes to where it cracks: the whip's length, or less if something
     *                (a block, or the target of a grab) is in the way
     * @param contact true if the lash runs into something before it is fully stretched
     */
    public record Strike(Move move, int style, double length, V3 aim, double reach, boolean contact) {
    }

    /** The target of a grab, where it is now. */
    public record Target(V3 center, double radius, double height) {
    }

    private WhipShape() {
    }

    /** Distance along the whip of node {@code i}. */
    public static double along(int i, double length) {
        return length * i / (Whip.NODES - 1);
    }

    /** Ticks after the start when the move is over and the whip is left to the physics. */
    public static double freeAt(Move move, boolean hasTarget) {
        return switch (move) {
            case LASH -> Whip.LASH_FREE;
            case IMPERIAL -> Whip.IMPERIAL_FREE;
            case GRAB -> hasTarget ? Whip.GRAB_FREE : Whip.GRAB_REACH + 1.5;
        };
    }

    /** Ticks after the start of the crack (or, for a grab, of the loop reaching the target). */
    public static double crackAt(Move move) {
        return switch (move) {
            case LASH -> Whip.LASH_CRACK;
            case IMPERIAL -> Whip.IMPERIAL_CRACK;
            case GRAB -> Whip.GRAB_REACH;
        };
    }

    private static double windup(Move move) {
        return switch (move) {
            case LASH -> Whip.LASH_WINDUP;
            case IMPERIAL -> Whip.IMPERIAL_WINDUP;
            case GRAB -> Whip.GRAB_WINDUP;
        };
    }

    /**
     * The whip {@code t} ticks into a lash, an Imperial Crack or a grab that missed.
     *
     * @param hand  where the whip leaves the handle, now
     * @param eye   the whipper's eyes, now: the crack point stays at {@code reach} along the aim
     * @param start the whip when the move started; the wind-up lifts it from there, handle end first
     */
    public static V3[] strike(Strike s, double t, V3 hand, V3 eye, V3[] start, V3[] out) {
        double length = s.length();
        V3 point = eye.add(s.aim().scale(s.reach()));
        V3 f = point.sub(hand).normalize(s.aim());
        V3 u = loopSide(f, s.style());
        double aEnd = endOfStroke(length, point.sub(hand).length());
        double windup = windup(s.move());
        double crack = crackAt(s.move());

        if (t < windup) {
            double q = t / windup;
            for (int i = 0; i < Whip.NODES; i++) {
                double along = along(i, length);
                V3 ready = loop(hand, f, u, 0, LOOP_START, along);
                out[i] = start[i].lerp(ready, Whip.smooth(q * 1.8 - 0.8 * along / length));
            }
        } else if (t <= crack) {
            double p = (t - windup) / (crack - windup);
            double a = aEnd * Math.pow(p, 1.7);
            double r = LOOP_START + (LOOP_END - LOOP_START) * Whip.smooth(p);
            for (int i = 0; i < Whip.NODES; i++) {
                out[i] = loop(hand, f, u, a, r, along(i, length));
            }
        } else if (s.contact() || s.move() == Move.GRAB) {
            // The loop ran into something: it bounces back a little and starts to fall.
            double e = t - crack;
            double a = Math.max(0, aEnd - 0.35 * Whip.smooth(e / 2));
            for (int i = 0; i < Whip.NODES; i++) {
                out[i] = loop(hand, f, u, a, LOOP_END, along(i, length)).add(0, -0.08 * e * e, 0);
            }
        } else {
            // The tip flips over the loop (the crack) and the whip ends up stretched out, then sags.
            double e = t - crack;
            double k = Whip.smooth(e / 1.2);
            for (int i = 0; i < Whip.NODES; i++) {
                double along = along(i, length);
                double g = along / length;
                V3 bent = loop(hand, f, u, aEnd, LOOP_END, along);
                V3 straight = hand.add(f.scale(along)).add(0, -0.1 * e * e * g * g, 0);
                out[i] = bent.lerp(straight, k);
            }
        }
        return out;
    }

    /** Where the crack goes off: the tip if the whip got fully stretched, else the loop that hit. */
    public static V3 crackPoint(Strike s, V3 hand, V3 eye) {
        V3 point = eye.add(s.aim().scale(s.reach()));
        V3 f = point.sub(hand).normalize(s.aim());
        double aEnd = endOfStroke(s.length(), point.sub(hand).length());
        if (s.contact() || s.move() == Move.GRAB) {
            return hand.add(f.scale(aEnd + LOOP_END));
        }
        return hand.add(f.scale(s.length()));
    }

    /**
     * A grab {@code t} ticks after it started: the loop flies to the target, the free end wraps around
     * it and stays wrapped while the target is pulled in (the part between hand and target sags as
     * the target gets closer).
     */
    public static V3[] grab(Strike s, double t, V3 hand, V3 eye, V3[] start, @Nullable Target target, V3[] out) {
        if (target == null || t <= Whip.GRAB_REACH) {
            return strike(s, t, hand, eye, start, out);
        }
        double length = s.length();
        // The whip as it was when the loop got there, and as it is when wrapped.
        V3[] reached = strike(s, Whip.GRAB_REACH, hand, eye, start, new V3[Whip.NODES]);
        double k = Whip.smooth((t - Whip.GRAB_REACH) / (Whip.GRAB_WRAPPED - Whip.GRAB_REACH));
        V3 c = target.center();
        double rho = target.radius() + 0.06;
        V3 side = horizontal(hand.sub(c), s.aim().scale(-1));
        double theta0 = Math.atan2(side.z(), side.x());
        V3 contact = c.add(Math.cos(theta0) * rho, 0, Math.sin(theta0) * rho);
        // Taut between the hand and the target, even while it is pulled in: what is not needed lies on the ground.
        double held = Math.min(length * 0.85, contact.sub(hand).length() + 0.05);
        double wrap = Math.min(length - held, 2 * Math.PI * rho * 1.4);
        double feet = c.y() - target.height() * 0.5 + 0.04;
        for (int i = 0; i < Whip.NODES; i++) {
            double along = along(i, length);
            V3 wrapped;
            if (along <= held) {
                wrapped = sag(hand, contact, held, along / held);
            } else if (along <= held + wrap) {
                double w = along - held;
                double theta = theta0 + w / rho;
                wrapped = c.add(Math.cos(theta) * rho, -0.35 * w / wrap * target.height() * 0.5, Math.sin(theta) * rho);
            } else {
                // what is left hangs from the coils down to the ground and lies there
                double theta = theta0 + wrap / rho;
                V3 end = c.add(Math.cos(theta) * rho, -0.35 * target.height() * 0.5, Math.sin(theta) * rho);
                double rest = along - held - wrap;
                double drop = Math.min(rest, end.y() - feet);
                V3 out1 = horizontal(end.sub(c), V3.UP.cross(s.aim()));
                wrapped = end.add(0, -drop, 0).add(out1.scale(rest - drop));
            }
            // The coils are laid from the contact point outwards.
            double order = Math.max(0, along - held) / Math.max(0.01, length - held);
            out[i] = reached[i].lerp(wrapped, Whip.smooth(k * 1.6 - 0.6 * order));
        }
        return out;
    }

    /**
     * The whip spinning over the head while the use key is held, {@code t} ticks after it started:
     * flatter, wider and faster as the charge grows. The first ticks lift it from {@code start}.
     */
    public static V3[] twirl(double t, V3 hand, double length, V3[] start, V3[] out) {
        double charge = Whip.charge(t);
        double phi = twirlAngle(t);
        double radius = Math.min(length * 0.5, 1.5 + 1.3 * charge);
        double droop = 0.95 - 0.8 * charge;
        double lift = Whip.smooth(t / 5);
        for (int i = 0; i < Whip.NODES; i++) {
            double along = along(i, length);
            double g = along / length;
            double rho;
            double theta;
            if (along <= radius) {
                rho = along;
                theta = phi - 0.9 * (along / radius) * (along / radius);
            } else {
                rho = radius;
                theta = phi - 0.9 - (along - radius) / radius;
            }
            double dy = 0.15 - droop * along * 0.55 + 0.07 * Math.sin(3 * phi + along * 2);
            V3 spun = hand.add(Math.cos(theta) * rho * (1 - 0.3 * droop * g), dy, Math.sin(theta) * rho * (1 - 0.3 * droop * g));
            out[i] = start[i].lerp(spun, Whip.smooth(lift * 1.6 - 0.6 * g));
        }
        return out;
    }

    /** Angle of the spin (radians) after {@code t} ticks: from 0.6 to 2.2 turns a second while it charges. */
    public static double twirlAngle(double t) {
        double sec = Math.max(0, t) / 20.0;
        double full = Whip.TWIRL_FULL / 20.0;
        double w0 = 0.6 * 2 * Math.PI;
        double w1 = 2.2 * 2 * Math.PI;
        if (sec <= full) {
            return w0 * sec + (w1 - w0) * sec * sec / (2 * full);
        }
        return w0 * full + (w1 - w0) * full / 2 + w1 * (sec - full);
    }

    /** How far the loop gets: to the tip, or up to what is in the way. */
    private static double endOfStroke(double length, double distance) {
        return Whip.clamp(Math.min(length - Math.PI * LOOP_END, distance - LOOP_END), 0, length);
    }

    /**
     * A point of the rolling loop: the held part goes from the hand along {@code f} for {@code a}
     * blocks, bends round a half circle of radius {@code r}, and the free end comes back on the
     * {@code -u} side.
     */
    static V3 loop(V3 hand, V3 f, V3 u, double a, double r, double along) {
        if (along <= a) {
            return hand.add(f.scale(along));
        }
        double arc = Math.PI * r;
        if (along <= a + arc) {
            double theta = (along - a) / r;
            return hand.add(f.scale(a + r * Math.sin(theta))).add(u.scale(r * Math.cos(theta) - r));
        }
        double back = along - a - arc;
        return hand.add(f.scale(a - back)).add(u.scale(-2 * r));
    }

    /** The side the free end is NOT on: down for an overhead lash, rolled round the aim for the others. */
    static V3 loopSide(V3 f, int style) {
        V3 up = V3.UP.sub(f.scale(f.y())).normalize(new V3(0, 0, 1));
        V3 right = f.cross(V3.UP).normalize(new V3(1, 0, 0));
        double roll = Math.toRadians(STYLE_ROLL[Math.floorMod(style, STYLES)]);
        return up.scale(-Math.cos(roll)).add(right.scale(Math.sin(roll)));
    }

    /** A rope of the given length hanging between two points (a parabola, close enough), at fraction x. */
    static V3 sag(V3 a, V3 b, double length, double x) {
        double d = b.sub(a).length();
        double h = Math.sqrt(Math.max(0, 3 * d * (length - d) / 8));
        return a.lerp(b, x).add(0, -h * 4 * x * (1 - x), 0);
    }

    private static V3 horizontal(V3 v, V3 fallback) {
        return new V3(v.x(), 0, v.z()).normalize(new V3(fallback.x(), 0, fallback.z()).normalize(new V3(0, 0, 1)));
    }
}
