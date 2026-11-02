package com.punizioni.client.royal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The overseer's bullwhip lash: a chain of points from the tip of the handle, in world space, with
 * gravity, a little springiness and the ground (idle, it hangs and drags behind). A crack is drawn
 * by hand, in the frame of where its owner looks: the lash swings up and back over the shoulder, then
 * a loop rolls along it from the handle to the end, and when the loop runs out at the tip, the tip
 * breaks the sound barrier: crack. After that the physics takes over again with the speed the lash had.
 */
public final class Lash {
    public static final int NODES = 22;
    /** Blocks, from the handle to the cracker. */
    public static final double LENGTH = 3.6;
    static final double SEG = LENGTH / (NODES - 1);

    /** Crack timing, in ticks from the swing: up and back until WIND, the loop runs until CRACK, then free. */
    public static final double WIND = 1.6;
    public static final double CRACK = 4.2;
    public static final double FREE = 5.0;

    static final double STEP = 1.0 / 3.0;
    static final double DT = 1.0 / 60.0;
    static final double GRAVITY = 22.0;
    static final double DAMPING = 0.985;
    static final int ITERATIONS = 10;
    static final int MAX_STEPS = 6;

    final double[] x = new double[NODES], y = new double[NODES], z = new double[NODES];
    final double[] px = new double[NODES], py = new double[NODES], pz = new double[NODES];
    final double[] floor = new double[NODES];
    private final double[] tx = new double[NODES], ty = new double[NODES], tz = new double[NODES];

    /** The handle's tip and its direction (along the handle, outwards), now and at the last step. */
    double ax, ay, az, hx, hy, hz;
    double oax, oay, oaz;
    /** Where the owner looks: forward, and up across it. */
    private double fx, fy, fz, ux, uy, uz;

    /** Game time (ticks) the last crack started at. */
    long crackStart = Long.MIN_VALUE / 2;
    /** The crack whose tip already went off (particles). */
    long crackDone = Long.MIN_VALUE / 2;
    long lastSeen;
    private double simTime;
    private long floorTick = Long.MIN_VALUE;
    private boolean ready;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    /**
     * Moves the lash to this frame.
     *
     * @param tip    the handle's tip (world)
     * @param dir    along the handle, outwards (unit)
     * @param yaw    where the owner looks (degrees, Minecraft's convention)
     */
    public void update(BlockGetter level, double time, long gameTime, double tipX, double tipY, double tipZ, double dirX, double dirY, double dirZ,
                       float yaw, float pitch) {
        this.lastSeen = gameTime;
        this.ax = tipX;
        this.ay = tipY;
        this.az = tipZ;
        this.hx = dirX;
        this.hy = dirY;
        this.hz = dirZ;
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        this.fx = -Math.sin(yr) * Math.cos(pr);
        this.fy = -Math.sin(pr);
        this.fz = Math.cos(yr) * Math.cos(pr);
        // up across the look: world up, minus its part along the look
        double dot = this.fy;
        this.ux = -dot * this.fx;
        this.uy = 1.0 - dot * this.fy;
        this.uz = -dot * this.fz;
        double ul = Math.sqrt(this.ux * this.ux + this.uy * this.uy + this.uz * this.uz);
        if (ul < 1.0E-4) {
            this.ux = 0.0;
            this.uy = 0.0;
            this.uz = 1.0;
        } else {
            this.ux /= ul;
            this.uy /= ul;
            this.uz /= ul;
        }
        double jump = sq(tipX - this.oax) + sq(tipY - this.oay) + sq(tipZ - this.oaz);
        if (!this.ready || time < this.simTime || time - this.simTime > 40.0 || jump > 64.0) {
            this.reset(level, time, gameTime);
            return;
        }
        if (jump > 0.8) {
            // teleported: the lash comes along as it is
            double dx = tipX - this.oax;
            double dy = tipY - this.oay;
            double dz = tipZ - this.oaz;
            for (int i = 0; i < NODES; i++) {
                this.x[i] += dx;
                this.y[i] += dy;
                this.z[i] += dz;
                this.px[i] += dx;
                this.py[i] += dy;
                this.pz[i] += dz;
            }
            this.oax = tipX;
            this.oay = tipY;
            this.oaz = tipZ;
            this.floorTick = Long.MIN_VALUE;
        }
        if (gameTime != this.floorTick) {
            this.floorTick = gameTime;
            for (int i = 1; i < NODES; i++) {
                this.floor[i] = floorAt(level, this.x[i], this.y[i], this.z[i]);
            }
        }
        int steps = (int) Math.floor((time - this.simTime) / STEP);
        if (steps > MAX_STEPS) {
            this.simTime = time - MAX_STEPS * STEP;
            steps = MAX_STEPS;
        }
        if (steps <= 0) {
            return;
        }
        double fromX = this.oax;
        double fromY = this.oay;
        double fromZ = this.oaz;
        for (int k = 1; k <= steps; k++) {
            double alpha = (double) k / steps;
            this.x[0] = this.px[0] = fromX + (tipX - fromX) * alpha;
            this.y[0] = this.py[0] = fromY + (tipY - fromY) * alpha;
            this.z[0] = this.pz[0] = fromZ + (tipZ - fromZ) * alpha;
            this.step(this.simTime + k * STEP);
        }
        this.simTime += steps * STEP;
        this.oax = tipX;
        this.oay = tipY;
        this.oaz = tipZ;
    }

    private static double sq(double v) {
        return v * v;
    }

    public void crack(long gameTime) {
        this.crackStart = gameTime;
    }

    /** Ticks into the current crack (or a big number: none). */
    public double crackAge(double time) {
        return time - this.crackStart;
    }

    private void reset(BlockGetter level, double time, long gameTime) {
        this.x[0] = this.ax;
        this.y[0] = this.ay;
        this.z[0] = this.az;
        for (int i = 1; i < NODES; i++) {
            double nx = this.x[i - 1];
            double ny = this.y[i - 1] - SEG;
            double nz = this.z[i - 1];
            double f = floorAt(level, nx, this.y[i - 1], nz);
            if (ny < f + 0.02) {
                // lying on the ground, behind the look
                nx = this.x[i - 1] - this.fx * SEG;
                nz = this.z[i - 1] - this.fz * SEG;
                ny = f + 0.02;
            }
            this.x[i] = nx;
            this.y[i] = ny;
            this.z[i] = nz;
        }
        for (int i = 0; i < NODES; i++) {
            this.px[i] = this.x[i];
            this.py[i] = this.y[i];
            this.pz[i] = this.z[i];
            this.floor[i] = i == 0 ? -1.0E9 : floorAt(level, this.x[i], this.y[i], this.z[i]);
        }
        this.floorTick = gameTime;
        this.oax = this.ax;
        this.oay = this.ay;
        this.oaz = this.az;
        for (int k = 0; k < 30; k++) {
            this.step(time - 100.0);
        }
        this.simTime = time;
        this.ready = true;
    }

    private void step(double time) {
        double age = time - this.crackStart;
        boolean scripted = age >= 0.0 && age < FREE;
        double dt2 = DT * DT;
        for (int i = 1; i < NODES; i++) {
            double nx = this.x[i] + (this.x[i] - this.px[i]) * DAMPING;
            double ny = this.y[i] + (this.y[i] - this.py[i]) * DAMPING - GRAVITY * dt2;
            double nz = this.z[i] + (this.z[i] - this.pz[i]) * DAMPING;
            this.px[i] = this.x[i];
            this.py[i] = this.y[i];
            this.pz[i] = this.z[i];
            this.x[i] = nx;
            this.y[i] = ny;
            this.z[i] = nz;
        }
        if (scripted) {
            // the hand-drawn crack pulls the lash where it has to be (the speed it gets is its own)
            this.shape(age);
            double follow = age < WIND ? 0.35 : 0.6;
            for (int i = 1; i < NODES; i++) {
                this.x[i] += (this.tx[i] - this.x[i]) * follow;
                this.y[i] += (this.ty[i] - this.y[i]) * follow;
                this.z[i] += (this.tz[i] - this.z[i]) * follow;
            }
        }
        // the first segment carries on the handle (a stiff fall)
        double sx = this.x[0] + this.hx * SEG;
        double sy = this.y[0] + this.hy * SEG;
        double sz = this.z[0] + this.hz * SEG;
        this.x[1] += (sx - this.x[1]) * 0.5;
        this.y[1] += (sy - this.y[1]) * 0.5;
        this.z[1] += (sz - this.z[1]) * 0.5;
        for (int it = 0; it < ITERATIONS; it++) {
            for (int i = 0; i + 1 < NODES; i++) {
                this.keep(i, i + 1, SEG, 1.0);
            }
            for (int i = 0; i + 2 < NODES; i++) {
                // a little springiness: the leather does not fold flat
                this.keep(i, i + 2, SEG * 1.9, 0.08 * (1.0 - (double) i / NODES));
            }
        }
        for (int i = 1; i < NODES; i++) {
            double f = this.floor[i] + 0.02;
            if (this.y[i] < f) {
                this.y[i] = f;
                this.px[i] += (this.x[i] - this.px[i]) * 0.5;
                this.pz[i] += (this.z[i] - this.pz[i]) * 0.5;
                if (this.py[i] < f) {
                    this.py[i] = f;
                }
            }
        }
    }

    private void keep(int a, int b, double rest, double stiffness) {
        double dx = this.x[b] - this.x[a];
        double dy = this.y[b] - this.y[a];
        double dz = this.z[b] - this.z[a];
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1.0E-7 || (stiffness < 1.0 && d > rest)) {
            return;
        }
        double diff = (d - rest) / d * stiffness;
        if (a == 0) {
            this.x[b] -= dx * diff;
            this.y[b] -= dy * diff;
            this.z[b] -= dz * diff;
        } else {
            diff *= 0.5;
            this.x[a] += dx * diff;
            this.y[a] += dy * diff;
            this.z[a] += dz * diff;
            this.x[b] -= dx * diff;
            this.y[b] -= dy * diff;
            this.z[b] -= dz * diff;
        }
    }

    /**
     * Where the crack wants each point at this age, in the frame of the look (forward f, up u): first
     * an arc that rises and bends back over the shoulder; then forward, with a loop that rolls from the
     * handle to the tip (before the loop the lash lies straight along the look, after it it runs back
     * above itself).
     */
    private void shape(double age) {
        double px0 = this.x[0], py0 = this.y[0], pz0 = this.z[0];
        double cx = px0, cy = py0, cz = pz0;
        this.tx[0] = cx;
        this.ty[0] = cy;
        this.tz[0] = cz;
        double loop = 0.0;
        double wind = 0.0;
        if (age < WIND) {
            double w = age / WIND;
            wind = w * w * (3.0 - 2.0 * w);
        } else {
            double u = Math.min(1.0, (age - WIND) / (CRACK - WIND));
            loop = Math.pow(u, 1.25) * (NODES - 1);
        }
        for (int i = 1; i < NODES; i++) {
            double a;
            if (age < WIND) {
                // from up (0) towards back (pi): more at the end of the lash
                double s = (double) i / (NODES - 1);
                a = wind * (0.55 + 2.35 * s);
                double dirF = -Math.sin(a);
                double dirU = Math.cos(a);
                cx += SEG * (this.fx * dirF + this.ux * dirU);
                cy += SEG * (this.fy * dirF + this.uy * dirU);
                cz += SEG * (this.fz * dirF + this.uz * dirU);
            } else {
                double past = i - loop;
                if (past <= 0.0) {
                    // unrolled: straight ahead, a touch down
                    a = -0.12;
                } else {
                    a = Math.min(1.0, past / 2.6) * Math.PI;
                }
                double dirF = Math.cos(a);
                double dirU = Math.sin(a);
                cx += SEG * (this.fx * dirF + this.ux * dirU);
                cy += SEG * (this.fy * dirF + this.uy * dirU);
                cz += SEG * (this.fz * dirF + this.uz * dirU);
            }
            this.tx[i] = cx;
            this.ty[i] = cy;
            this.tz[i] = cz;
        }
    }

    double floorAt(BlockGetter level, double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = (int) Math.floor(y + 0.4);
        for (int by = top; by >= top - 3; by--) {
            this.cursor.set(bx, by, bz);
            BlockState state = level.getBlockState(this.cursor);
            if (state.isAir()) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level, this.cursor);
            if (shape.isEmpty()) {
                continue;
            }
            double h = by + shape.max(Direction.Axis.Y);
            if (h <= y + 0.45) {
                return h;
            }
        }
        return -1.0E9;
    }

    public double nx(int i) {
        return i == 0 ? this.ax : this.x[i];
    }

    public double ny(int i) {
        return i == 0 ? this.ay : this.y[i];
    }

    public double nz(int i) {
        return i == 0 ? this.az : this.z[i];
    }
}
