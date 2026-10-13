package com.rainbowyoyo.motion;

/**
 * Where the yo-yo is during every phase, as a pure function of time and of the owner's hand and look.
 *
 * <p>Server and clients run the same functions: the server to hit things, the clients to draw the
 * yo-yo smoothly at any partial tick. Only the phase, its start and a few anchors (see
 * {@link Context}) travel over the network. All positions are relative to the owner's current hand,
 * so the tricks follow the player while they move.</p>
 */
public final class YoyoMotion {
    /** Radius of the yo-yo in blocks: it is 1.25 blocks across. */
    public static final double RADIUS = 0.625;
    /** Tick of {@link YoyoPhase#SHOOT_THE_MOON} at which the yo-yo hits the ground. */
    public static final int MOON_IMPACT_TICK = 20;
    /** Tick of {@link YoyoPhase#SUPERNOVA} at which the yo-yo explodes. */
    public static final int NOVA_BOOM_TICK = 32;

    private static final double TAU = Math.PI * 2;

    /** Finds the ground: the y of the first surface below (x, fromY, z), or NaN if there is none nearby. */
    @FunctionalInterface
    public interface Ground {
        double surfaceBelow(double x, double fromY, double z);
    }

    /** How the axle of the yo-yo is turned. */
    public enum Axle {
        /** Across the string, horizontal: the classic yo-yo. */
        STRING,
        /** Along the player's right, so the yo-yo rolls or loops forward. */
        RIGHT,
        /** Vertical: the yo-yo lies flat and spins like a saw blade. */
        UP,
        /** Pointing at the player, who sees its face. */
        FORWARD
    }

    /** Inputs of one evaluation. */
    public static final class Context {
        public YoyoPhase phase = YoyoPhase.SLEEP;
        /** Ticks since the phase started (fractional while rendering). */
        public double t;
        /** The owner's hand anchor now. */
        public V3 hand = V3.ZERO;
        /** The owner's look direction now (unit). */
        public V3 look = new V3(0, 0, 1);
        /** The owner's rotation when the phase started (degrees). */
        public float yaw0;
        public float pitch0;
        /** Yo-yo position minus hand when the phase started. */
        public V3 startRel = V3.ZERO;
        /** Absolute anchor of some tricks: slam point of Shoot the Moon, centre of the Supernova. */
        public V3 target = V3.ZERO;
        /** How far the quick shot goes. */
        public double dist = 8;
        /** String length. */
        public double length = 8;
        public Ground ground = (x, y, z) -> Double.NaN;
    }

    private YoyoMotion() {
    }

    /** Position of the yo-yo's centre. For {@link YoyoPhase#SLEEP} this is the point it springs towards. */
    public static V3 position(Context c) {
        return switch (c.phase) {
            case SHOT -> shot(c);
            case THROW -> thrown(c);
            case SLEEP -> sleepTarget(c);
            case RETURN -> returning(c);
            case LOOP_THE_LOOP -> loopTheLoop(c);
            case AROUND_THE_WORLD -> aroundTheWorld(c);
            case WALK_THE_DOG -> walkTheDog(c);
            case SHOOT_THE_MOON -> shootTheMoon(c);
            case SUPERNOVA -> supernova(c);
        };
    }

    /** End of the string, where the sleeping yo-yo wants to be: straight along the look. */
    public static V3 sleepTarget(Context c) {
        return c.hand.add(c.look.scale(c.length)).add(0, 0.12 * Math.sin(c.t * 0.3), 0);
    }

    /** Ticks the yo-yo needs to wind back into the hand from {@code startRel}. */
    public static int returnDuration(V3 startRel) {
        return (int) clamp(Math.round(startRel.length() / 1.4), 4, 10);
    }

    /** Size of the yo-yo (1 = normal). */
    public static double scale(Context c) {
        double t = c.t;
        return switch (c.phase) {
            case SHOOT_THE_MOON -> t < 8 ? 1 : t < 16 ? 1 + 0.4 * smooth((t - 8) / 8)
                    : t < MOON_IMPACT_TICK ? 1.4 - 0.4 * smooth((t - 16) / 4) : 1;
            case SUPERNOVA -> t < 10 ? 1 : t < NOVA_BOOM_TICK ? 1 + 2 * easeInCubic((t - 10) / (NOVA_BOOM_TICK - 10))
                    : t < 40 ? 3 - 2 * easeOutBack((t - NOVA_BOOM_TICK) / (40.0 - NOVA_BOOM_TICK)) : 1;
            default -> 1;
        };
    }

    /** Extra glow (0 to 1) on top of the rainbow rim. */
    public static double glow(Context c) {
        double t = c.t;
        return switch (c.phase) {
            case SHOOT_THE_MOON -> t < 8 ? 0.3 : t < MOON_IMPACT_TICK ? 0.3 + 0.7 * smooth((t - 8) / 12)
                    : Math.max(0.3, 1 - (t - MOON_IMPACT_TICK) / 8);
            case SUPERNOVA -> t < NOVA_BOOM_TICK ? clamp((t - 6) / (NOVA_BOOM_TICK - 6), 0.3, 1)
                    : Math.max(0.3, 1 - (t - NOVA_BOOM_TICK) / 10);
            case LOOP_THE_LOOP, AROUND_THE_WORLD, WALK_THE_DOG -> 0.3;
            default -> 0;
        };
    }

    /** Spin speed around the axle, radians per tick (rolling on the ground is added separately). */
    public static double spinRate(Context c) {
        return switch (c.phase) {
            case SLEEP -> 1.5;
            case SHOT, THROW -> 2.2;
            case RETURN -> 1.2;
            case WALK_THE_DOG -> 0.0;
            case SUPERNOVA -> 2.0 + 3.0 * clamp((c.t - 10) / (NOVA_BOOM_TICK - 10), 0, 1);
            default -> 2.4;
        };
    }

    public static Axle axle(YoyoPhase phase) {
        return switch (phase) {
            case AROUND_THE_WORLD -> Axle.UP;
            case LOOP_THE_LOOP, WALK_THE_DOG, SHOOT_THE_MOON -> Axle.RIGHT;
            case SUPERNOVA -> Axle.FORWARD;
            default -> Axle.STRING;
        };
    }

    /** How slack the string is: 0 = taut, 1 = loose. */
    public static double slack(Context c) {
        return switch (c.phase) {
            case RETURN -> 0.5;
            case THROW -> 0.3 * (1 - clamp(c.t / YoyoPhase.THROW.duration, 0, 1));
            default -> 0;
        };
    }

    // ---------------------------------------------------------------- phases

    private static V3 shot(Context c) {
        V3 dir = V3.look(c.yaw0, c.pitch0);
        double t = clamp(c.t, 0, YoyoPhase.SHOT.duration);
        double d;
        if (t <= 5) {
            d = c.dist * easeOutCubic(t / 5);
        } else if (t <= 6) {
            d = c.dist;
        } else {
            d = c.dist * (1 - easeInQuad((t - 6) / 6));
        }
        return c.hand.add(c.startRel.scale(1 - smooth(t / 3))).add(dir.scale(d))
                .add(0, 0.3 * Math.sin(Math.PI * t / YoyoPhase.SHOT.duration), 0);
    }

    private static V3 thrown(Context c) {
        double a = easeOutBack(clamp(c.t / YoyoPhase.THROW.duration, 0, 1));
        V3 from = c.hand.add(c.startRel);
        V3 to = c.hand.add(c.look.scale(c.length));
        return from.lerp(to, a);
    }

    private static V3 returning(Context c) {
        double a = clamp(c.t / returnDuration(c.startRel), 0, 1);
        double e = a * a * a; // speeds up into the hand
        return c.hand.add(c.startRel.scale(1 - e)).add(0, 0.6 * Math.sin(Math.PI * a), 0);
    }

    /** Three giant vertical loops in front of the player, coming over the top towards them. */
    private static V3 loopTheLoop(Context c) {
        double u = clamp(c.t / YoyoPhase.LOOP_THE_LOOP.duration, 0, 1);
        V3 f = V3.forward(c.yaw0);
        V3 r = V3.right(c.yaw0);
        V3 center = c.hand.add(f.scale(4.0)).add(0, 2.2, 0);
        V3 rel = c.hand.add(c.startRel).sub(center);
        double a0 = Math.atan2(rel.y(), rel.dot(f));
        double r0 = Math.hypot(rel.y(), rel.dot(f));
        double side0 = rel.dot(r);
        double k = bump(u, 0.18);
        double theta = a0 + 3 * TAU * smooth(u);
        double radius = lerp(r0, 2.8, k);
        return center.add(f.scale(radius * Math.cos(theta))).add(0, radius * Math.sin(theta), 0)
                .add(r.scale(side0 * (1 - k)));
    }

    /** Two fast horizontal laps around the player at waist height. */
    private static V3 aroundTheWorld(Context c) {
        double u = clamp(c.t / YoyoPhase.AROUND_THE_WORLD.duration, 0, 1);
        V3 f = V3.forward(c.yaw0);
        V3 r = V3.right(c.yaw0);
        V3 center = c.hand.add(0, -0.25, 0);
        V3 rel = c.hand.add(c.startRel).sub(center);
        double a0 = Math.atan2(rel.dot(r), rel.dot(f));
        double r0 = Math.hypot(rel.dot(r), rel.dot(f));
        double k = bump(u, 0.15);
        double phi = a0 + 2 * TAU * smooth(u);
        double radius = lerp(r0, 4.5, k);
        double h = lerp(rel.y(), 0.35 * Math.sin(2 * TAU * u), k);
        return center.add(f.scale(radius * Math.cos(phi))).add(r.scale(radius * Math.sin(phi))).add(0, h, 0);
    }

    /** Drops to the ground, rolls out 9 blocks and back, then hops up into the hand's reach. */
    private static V3 walkTheDog(Context c) {
        double t = clamp(c.t, 0, YoyoPhase.WALK_THE_DOG.duration);
        V3 f = V3.forward(c.yaw0);
        V3 start = c.hand.add(c.startRel);
        if (t < 6) {
            double a = smooth(t / 6);
            return start.lerp(onGround(c, f, 3.0), a).add(0, 0.8 * Math.sin(Math.PI * a), 0);
        }
        if (t < 22) {
            return onGround(c, f, 3.0 + 6.0 * smooth((t - 6) / 16));
        }
        if (t < 30) {
            return onGround(c, f, 9.0 - 5.0 * smooth((t - 22) / 8));
        }
        double a = smooth((t - 30) / 6);
        return onGround(c, f, 4.0).lerp(start, a).add(0, 1.5 * Math.sin(Math.PI * a), 0);
    }

    /** Shoots up 10 blocks, hovers and grows, slams into {@link Context#target}, bounces and comes back. */
    private static V3 shootTheMoon(Context c) {
        double t = clamp(c.t, 0, YoyoPhase.SHOOT_THE_MOON.duration);
        V3 f = V3.forward(c.yaw0);
        V3 r = V3.right(c.yaw0);
        V3 start = c.hand.add(c.startRel);
        V3 top = c.hand.add(0, 10, 0).add(f.scale(1.0));
        if (t < 8) {
            return start.lerp(top, easeOutCubic(t / 8));
        }
        double w = clamp((t - 8) / 8, 0, 1);
        V3 hover = top.add(r.scale(0.4 * Math.sin(2 * TAU * w))).add(f.scale(0.4 * (Math.cos(2 * TAU * w) - 1)));
        if (t < 16) {
            return hover;
        }
        if (t < MOON_IMPACT_TICK) {
            double a = (t - 16) / (MOON_IMPACT_TICK - 16);
            return hover.lerp(c.target, a * a * a);
        }
        if (t < 26) {
            double b = (t - MOON_IMPACT_TICK) / (26.0 - MOON_IMPACT_TICK);
            return c.target.add(0, 0.9 * Math.abs(Math.sin(Math.PI * b * 1.5)) * (1 - b), 0);
        }
        double a = smooth((t - 26) / 8);
        return c.target.lerp(start, a).add(0, 1.2 * Math.sin(Math.PI * a), 0);
    }

    /** Flies to {@link Context#target}, trembles while it grows, explodes and comes back. */
    private static V3 supernova(Context c) {
        double t = clamp(c.t, 0, YoyoPhase.SUPERNOVA.duration);
        V3 start = c.hand.add(c.startRel);
        if (t < 10) {
            return start.lerp(c.target, smooth(t / 10));
        }
        if (t < NOVA_BOOM_TICK) {
            double e = (t - 10) / (NOVA_BOOM_TICK - 10);
            V3 r = V3.right(c.yaw0);
            return c.target.add(r.scale(0.08 * e * Math.sin(t * 3.7))).add(0, 0.08 * e * Math.cos(t * 4.3), 0);
        }
        if (t < 40) {
            return c.target;
        }
        return c.target.lerp(start, smooth((t - 40) / 10));
    }

    /** Point {@code s} blocks in front of the hand where the yo-yo rests on the ground. */
    private static V3 onGround(Context c, V3 forward, double s) {
        V3 p = c.hand.add(forward.scale(s));
        double y = c.ground.surfaceBelow(p.x(), c.hand.y(), p.z());
        if (Double.isNaN(y)) {
            y = c.hand.y() - 1.25;
        }
        return new V3(p.x(), y + RADIUS, p.z());
    }

    // ---------------------------------------------------------------- easing

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double smooth(double x) {
        x = clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static double easeOutCubic(double x) {
        x = clamp(x, 0, 1);
        double y = 1 - x;
        return 1 - y * y * y;
    }

    private static double easeInCubic(double x) {
        x = clamp(x, 0, 1);
        return x * x * x;
    }

    private static double easeInQuad(double x) {
        x = clamp(x, 0, 1);
        return x * x;
    }

    /** Overshoots a little past 1 before settling: a springy snap. */
    private static double easeOutBack(double x) {
        x = clamp(x, 0, 1);
        double c1 = 1.70158;
        double c3 = c1 + 1;
        double y = x - 1;
        return 1 + c3 * y * y * y + c1 * y * y;
    }

    /** 0 at both ends of [0, 1], 1 in the middle, with smooth ramps of width {@code w}. */
    private static double bump(double u, double w) {
        return smooth(u / w) * smooth((1 - u) / w);
    }
}
