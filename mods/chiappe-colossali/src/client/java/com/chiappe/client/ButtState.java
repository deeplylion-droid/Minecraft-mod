package com.chiappe.client;

import com.chiappe.Butt;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/**
 * One player's cheeks, moving: their size follows the XP level with a springy "boing", and each cheek
 * hangs on a soft, barely damped spring that the body shakes: steps, jumps and landings, turns, speeding
 * up and slowing down, farts. Offsets are in units of the cheek's radius (x outwards, y up, z back),
 * squash is how much taller (positive) or flatter (negative) a cheek is.
 */
public final class ButtState {
    private static final int SUBSTEPS = 4;
    private static final double DT = 0.05 / SUBSTEPS;
    private static final double LIMIT = 0.35;
    private static final double SQUASH_LIMIT = 0.4;

    /** Diameter (blocks) and its speed, and the value of the previous tick. */
    double size;
    double sizeV;
    double prevSize;
    final double[] x = new double[2], y = new double[2], z = new double[2], s = new double[2];
    final double[] vx = new double[2], vy = new double[2], vz = new double[2], vs = new double[2];
    final double[] px = new double[2], py = new double[2], pz = new double[2], ps = new double[2];
    /** Ticks since the last fart (-1: none). */
    int fartAge = -1;
    private int lastLevel = -1;
    private boolean started;
    private double lastX, lastY, lastZ, lastVy, lastForward, lastYaw;
    private long lastStep;

    public void fart() {
        this.fartAge = 0;
    }

    void tick(AbstractClientPlayer p, int level) {
        for (int i = 0; i < 2; i++) {
            this.px[i] = this.x[i];
            this.py[i] = this.y[i];
            this.pz[i] = this.z[i];
            this.ps[i] = this.s[i];
        }
        this.prevSize = this.size;
        double target = Butt.diameter(level);
        if (!this.started) {
            this.started = true;
            this.size = this.prevSize = target;
            this.lastLevel = level;
            this.lastX = p.getX();
            this.lastY = p.getY();
            this.lastZ = p.getZ();
            this.lastYaw = p.yBodyRot;
            this.lastStep = stepIndex(p);
            return;
        }
        if (level != this.lastLevel) {
            // a new level: they swell with a pop
            for (int i = 0; i < 2; i++) {
                this.vs[i] += level > this.lastLevel ? -3.0 : 2.0;
            }
            this.lastLevel = level;
        }
        double r = Math.max(0.2, this.size / 2.0);

        // how the body moved this tick (positions: remote players have no real velocity on the client)
        double dx = p.getX() - this.lastX, dy = p.getY() - this.lastY, dz = p.getZ() - this.lastZ;
        this.lastX = p.getX();
        this.lastY = p.getY();
        this.lastZ = p.getZ();
        double big = 1.0 / (0.3 + r);
        double ay = dy - this.lastVy;
        this.lastVy = dy;
        float yawRad = p.yBodyRot * Mth.DEG_TO_RAD;
        double forward = -Mth.sin(yawRad) * dx + Mth.cos(yawRad) * dz;
        double af = forward - this.lastForward;
        this.lastForward = forward;
        double turn = Mth.wrapDegrees(p.yBodyRot - this.lastYaw);
        this.lastYaw = p.yBodyRot;
        if (Math.abs(ay) > 0.6 || Math.abs(af) > 0.6) {
            // a teleport, not a movement
            ay = 0.0;
            af = 0.0;
        }
        for (int i = 0; i < 2; i++) {
            // they lag behind the body: up when it falls, down when it stops falling (landing), back when it speeds up
            this.vy[i] -= ay * 20.0 * 0.5 * big;
            this.vz[i] += af * 20.0 * 0.35 * big;
            // turning swings them outwards, the outer one more
            double side = i == 0 ? 1.0 : -1.0;
            this.vx[i] += Math.abs(turn) * 0.012 + turn * side * 0.01;
        }
        // every step shakes the cheek on that side
        long step = stepIndex(p);
        float walking = Math.min(1.0F, p.walkAnimation.speed() * 2.0F);
        if (step != this.lastStep && walking > 0.05F && p.onGround()) {
            int i = (int) (step & 1);
            this.vy[i] += 1.6 * walking;
            this.vs[i] -= 1.2 * walking;
            this.vy[1 - i] -= 0.5 * walking;
        }
        this.lastStep = step;

        // the fart: they squeeze together for three ticks, then blow apart
        double clench = 0.0;
        if (this.fartAge >= 0) {
            if (this.fartAge < 3) {
                clench = 1.0;
            } else if (this.fartAge == 3) {
                for (int i = 0; i < 2; i++) {
                    this.vx[i] += 2.6;
                    this.vs[i] -= 2.4;
                    this.vz[i] += 1.6;
                    this.vy[i] += 0.8;
                }
            }
            this.fartAge = this.fartAge > 40 ? -1 : this.fartAge + 1;
        }

        // big ones are slower and heavier
        double w = Mth.TWO_PI * 3.0 / (1.0 + 0.2 * this.size);
        double damp = 2.0 * 0.14 * w;
        double ws = w * 1.25;
        double dampS = 2.0 * 0.12 * ws;
        double wSize = Mth.TWO_PI * 1.3;
        for (int n = 0; n < SUBSTEPS; n++) {
            for (int i = 0; i < 2; i++) {
                double tx = -0.12 * clench, ts = 0.15 * clench;
                this.vx[i] += (-w * w * (this.x[i] - tx) - damp * this.vx[i]) * DT;
                this.vy[i] += (-w * w * this.y[i] - damp * this.vy[i]) * DT;
                this.vz[i] += (-w * w * this.z[i] - damp * this.vz[i]) * DT;
                this.vs[i] += (-ws * ws * (this.s[i] - ts) - dampS * this.vs[i]) * DT;
                this.x[i] = Mth.clamp(this.x[i] + this.vx[i] * DT, -LIMIT, LIMIT);
                this.y[i] = Mth.clamp(this.y[i] + this.vy[i] * DT, -LIMIT, LIMIT);
                this.z[i] = Mth.clamp(this.z[i] + this.vz[i] * DT, -LIMIT, LIMIT);
                this.s[i] = Mth.clamp(this.s[i] + this.vs[i] * DT, -SQUASH_LIMIT, SQUASH_LIMIT);
            }
            this.sizeV += (-wSize * wSize * (this.size - target) - 2.0 * 0.3 * wSize * this.sizeV) * DT;
            this.size = Math.max(0.05, this.size + this.sizeV * DT);
        }
    }

    /** Which step of the walk this is (the legs swing with cos(position * 0.6662): a step every half swing). */
    private static long stepIndex(AbstractClientPlayer p) {
        return (long) Math.floor(p.walkAnimation.position() * 0.6662 / Math.PI);
    }

    /** Fills what the renderer needs, between the last tick and this one. */
    void fill(ButtVisual v, float partial) {
        v.diameter = (float) Mth.lerp(partial, this.prevSize, this.size);
        for (int i = 0; i < 2; i++) {
            v.x[i] = (float) Mth.lerp(partial, this.px[i], this.x[i]);
            v.y[i] = (float) Mth.lerp(partial, this.py[i], this.y[i]);
            v.z[i] = (float) Mth.lerp(partial, this.pz[i], this.z[i]);
            v.s[i] = (float) Mth.lerp(partial, this.ps[i], this.s[i]);
        }
    }
}
