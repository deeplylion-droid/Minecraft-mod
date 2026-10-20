package com.frusta.client;

import com.frusta.item.WhipMaterial;
import com.frusta.motion.V3;
import com.frusta.motion.Whip;
import com.frusta.motion.WhipRope;
import com.frusta.motion.WhipShape;
import java.util.ArrayDeque;
import javax.annotation.Nullable;

/**
 * Everything one player's whip is doing on this client: the rope lying on the ground, the move being
 * drawn (lash, Imperial Crack, grab), the spin over the head and the glowing trail of the tip. Time is
 * counted in client ticks ({@link WhipAnims#clock}).
 */
public final class WhipAnim {
    /** A point of the trail left by the tip. */
    public record TrailPoint(double clock, V3 pos) {
    }

    private static final double TRAIL_TICKS = 5.0;

    final WhipRope rope = new WhipRope();
    final V3[] nodes = new V3[Whip.NODES];
    boolean hasNodes;

    /** The move under way, kept until the arm is back at rest; null when there is none. */
    @Nullable
    WhipShape.Strike strike;
    long moveStart;
    /** The move's drawn part is over and the physics has the whip again (the arm may still be moving). */
    boolean released;
    @Nullable
    V3[] snapshot;
    int target = -1;
    float charge;
    /** Ticks the whip had been spinning when an Imperial Crack was let go (for the arm). */
    double spunFor;
    boolean cracked;
    boolean reached;

    /** Spinning over the head while the use key is held. */
    boolean twirling;
    double twirlStart;
    @Nullable
    V3[] twirlSnapshot;

    /** Where the whip leaves the handle and which way the handle points, and in which frame that was found. */
    @Nullable
    V3 hand;
    V3 handleDir = new V3(0, -1, 0);
    long handFrame = -1;
    @Nullable
    V3 eye;
    @Nullable
    private V3 lastHand;
    private double lastClock = Double.NaN;

    WhipMaterial material = WhipMaterial.LEATHER;
    boolean foil;
    final ArrayDeque<TrailPoint> trail = new ArrayDeque<>();

    public boolean hasNodes() {
        return hasNodes;
    }

    /** Point {@code i} of the whip this frame (0 = where it leaves the handle). */
    public V3 node(int i) {
        return nodes[i];
    }

    public WhipMaterial material() {
        return material;
    }

    /** The whip is enchanted: it shimmers. */
    public boolean foil() {
        return foil;
    }

    /** Recent positions of the tip while it was doing something, oldest first. */
    public Iterable<TrailPoint> trail() {
        return trail;
    }

    /** Starts a move from wherever the whip is now. */
    void startMove(WhipShape.Strike strike, long now, int target, float charge, double spunFor) {
        this.snapshot = hasNodes ? nodes.clone() : null;
        this.strike = strike;
        this.moveStart = now;
        this.target = target;
        this.charge = charge;
        this.spunFor = spunFor;
        this.cracked = false;
        this.reached = false;
        this.released = false;
        this.twirling = false;
    }

    void startTwirl(double start) {
        twirling = true;
        twirlStart = start;
        twirlSnapshot = hasNodes ? nodes.clone() : null;
    }

    /** The spin ended without a move: the physics takes the whip as it was spinning. */
    void stopTwirl(double clock, V3 hand) {
        if (!twirling) {
            return;
        }
        twirling = false;
        if (twirlSnapshot == null || hand == null) {
            return;
        }
        double t = clock - twirlStart;
        double length = material.length();
        V3[] now = WhipShape.twirl(t, hand, length, twirlSnapshot, new V3[Whip.NODES]);
        V3[] before = WhipShape.twirl(t - WhipRope.STEP * 20, hand, length, twirlSnapshot, new V3[Whip.NODES]);
        rope.takeOver(now, before);
        lastClock = clock;
        lastHand = hand;
    }

    /** Drawn by hand right now (a move or the spin), rather than by the physics. */
    boolean isDrawn() {
        return strike != null && !released || twirling;
    }

    /**
     * Brings the whip to {@code clock} and fills {@link #nodes}.
     *
     * @param back      direction the whip lies in when it is first laid down (behind the player)
     * @param floorY    ground height under the hand, for laying it down
     * @param target    where the grabbed entity is now, if any
     */
    void update(double clock, V3 hand, V3 handleDir, V3 eye, WhipRope.Ground ground, V3 back, double floorY,
                @Nullable WhipShape.Target target) {
        double length = material.length();
        if (!rope.isReady() || lastHand == null || hand.sub(lastHand).lengthSqr() > 64 || Double.isNaN(lastClock)) {
            rope.layDown(hand, back, floorY, length);
            lastClock = clock;
            lastHand = hand;
            fillFromRope(hand);
        }
        if (snapshot == null && strike != null && !released) {
            snapshot = nodes.clone();
        }
        if (twirling && twirlSnapshot == null) {
            twirlSnapshot = nodes.clone();
        }

        if (strike != null && !released) {
            double t = clock - moveStart;
            double free = WhipShape.freeAt(strike.move(), target != null);
            if (t < free) {
                pose(t, hand, eye, target, nodes);
                remember(clock, hand);
                return;
            }
            // The move is over: the physics carries on with the speed the whip had.
            V3[] now = pose(free, hand, eye, target, new V3[Whip.NODES]);
            V3[] before = pose(free - WhipRope.STEP * 20, hand, eye, target, new V3[Whip.NODES]);
            rope.takeOver(now, before);
            released = true;
            lastClock = Math.min(clock, moveStart + free);
        }
        if (twirling) {
            WhipShape.twirl(clock - twirlStart, hand, length, twirlSnapshot, nodes);
            remember(clock, hand);
            return;
        }

        double elapsed = (clock - lastClock) / 20.0;
        int steps = (int) Math.floor(elapsed / WhipRope.STEP);
        if (steps > 24) {
            steps = 24;
            lastClock = clock;
        } else {
            lastClock += steps * WhipRope.STEP * 20.0;
        }
        V3 from = lastHand;
        for (int i = 1; i <= steps; i++) {
            rope.step(from.lerp(hand, (double) i / steps), handleDir, length, ground);
        }
        lastHand = hand;
        fillFromRope(hand);
    }

    private V3[] pose(double t, V3 hand, V3 eye, @Nullable WhipShape.Target target, V3[] out) {
        V3[] start = snapshot != null ? snapshot : nodes;
        if (strike.move() == WhipShape.Move.GRAB) {
            return WhipShape.grab(strike, t, hand, eye, start, target, out);
        }
        return WhipShape.strike(strike, t, hand, eye, start, out);
    }

    private void remember(double clock, V3 hand) {
        lastClock = clock;
        lastHand = hand;
        hasNodes = true;
    }

    private void fillFromRope(V3 hand) {
        for (int i = 0; i < Whip.NODES; i++) {
            nodes[i] = rope.node(i);
        }
        // The physics runs in fixed steps: pin the handle end to where the hand is this very frame.
        V3 off = hand.sub(nodes[0]);
        for (int i = 0; i < 4; i++) {
            nodes[i] = nodes[i].add(off.scale(1.0 - i / 4.0));
        }
        hasNodes = true;
    }

    /** Remembers where the tip is, for the trail (only while it is doing something). */
    void recordTrail(double clock) {
        if (!hasNodes) {
            return;
        }
        if (isDrawn()) {
            trail.addLast(new TrailPoint(clock, nodes[Whip.NODES - 1]));
        }
        while (!trail.isEmpty() && (clock - trail.peekFirst().clock() > TRAIL_TICKS || trail.size() > 64)) {
            trail.removeFirst();
        }
    }
}
