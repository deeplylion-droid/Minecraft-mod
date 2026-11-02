package com.punizioni.client.royal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The royal mantle as real cloth: a grid of points (COLS across, ROWS down) hanging from the
 * shoulders, moved by gravity, the wind and the air it runs through (it billows when the king runs),
 * kept in shape by distance constraints, pushed out of the torso and the legs, and lying on the
 * ground as a train. It lives in world space and steps at 60 Hz whatever the frame rate; the top
 * row is pinned to the shoulders as they are drawn in this very frame, so it never comes loose.
 *
 * <p>Model-space numbers are in pixels of the player model (16 to a block before its 0.9375 scale).
 */
public final class CapeCloth {
    public static final int COLS = 9;
    public static final int ROWS = 16;
    public static final int N = COLS * ROWS;
    /** From the shoulders to the end of the train (blocks). */
    public static final float LENGTH = 2.55F;
    public static final float TOP_WIDTH = 0.62F;
    public static final float BOTTOM_WIDTH = 1.3F;

    /** The shoulder line it hangs from, in the body part's space (pixels): just behind the back. */
    public static final float ANCHOR_HALF_WIDTH = 5.3F;
    public static final float ANCHOR_Y = 0.4F;
    public static final float ANCHOR_Z = 2.6F;

    /** One step: 1/60 s (a third of a tick). */
    static final double STEP = 1.0 / 3.0;
    static final double DT = 1.0 / 60.0;
    static final double GRAVITY = 18.0;
    static final double DAMPING = 0.988;
    /** How hard the air pushes on the cloth (per block/second of air speed across it). */
    static final double AERO = 1.6;
    static final int ITERATIONS = 6;
    /** How strongly (per step) the cloth is drawn towards its drape: weak at the shoulders, more at the end. */
    static final double PULL = 0.035;
    static final int MAX_STEPS = 6;

    /** Collision boxes in their part's space (pixels): x min/max, y min/max, z min/max; z max is the back. */
    static final float[] TORSO = {-4.5F, 4.5F, -1.0F, 12.6F, -2.5F, 2.5F};
    static final float[] LEG = {-2.45F, 2.45F, -0.5F, 12.6F, -2.45F, 2.45F};

    final double[] x = new double[N], y = new double[N], z = new double[N];
    final double[] px = new double[N], py = new double[N], pz = new double[N];
    final double[] floor = new double[N];
    /** The anchors (the shoulder line) now, and at the last step. */
    final double[] ax = new double[COLS], ay = new double[COLS], az = new double[COLS];
    final double[] oax = new double[COLS], oay = new double[COLS], oaz = new double[COLS];

    private final int[] ca;
    private final int[] cb;
    private final double[] rest;
    private final double[] stiff;

    private final Matrix4f[] toLocal = {new Matrix4f(), new Matrix4f(), new Matrix4f()};
    private final Matrix4f[] toWorld = {new Matrix4f(), new Matrix4f(), new Matrix4f()};
    private final float[][] boxes = {TORSO, LEG, LEG};
    private double ox, oy, oz;
    private double backX, backY, backZ;

    /** Where the cloth is gently drawn to (CapeCloth.drape), and the ground under the king. */
    private final double[] tx = new double[N], ty = new double[N], tz = new double[N];
    private double bodyFloor = -1.0E9;
    /** The anchors at the last frame (to tell a teleport from a step). */
    private final double[] fax = new double[COLS], fay = new double[COLS], faz = new double[COLS];
    private double simTime;
    private long floorTick = Long.MIN_VALUE;
    private long flipCheckTick = Long.MIN_VALUE;
    private double stillX, stillY, stillZ;
    private boolean ready;
    /** Which winding of the grid faces away from the body (the velvet side). */
    boolean outerFlip;
    long lastSeen;

    private final Vector3f tmp = new Vector3f();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public CapeCloth() {
        int[] a = new int[N * 6];
        int[] b = new int[N * 6];
        double[] r = new double[N * 6];
        double[] s = new double[N * 6];
        int n = 0;
        for (int row = 0; row < ROWS; row++) {
            for (int c = 0; c < COLS; c++) {
                int i = row * COLS + c;
                if (c + 1 < COLS) {
                    n = add(a, b, r, s, n, i, i + 1, 1.0);
                }
                if (row + 1 < ROWS) {
                    n = add(a, b, r, s, n, i, i + COLS, 1.0);
                    if (c + 1 < COLS) {
                        n = add(a, b, r, s, n, i, i + COLS + 1, 0.55);
                        n = add(a, b, r, s, n, i + 1, i + COLS, 0.55);
                    }
                }
                if (c + 2 < COLS) {
                    n = add(a, b, r, s, n, i, i + 2, 0.12);
                }
                if (row + 2 < ROWS) {
                    n = add(a, b, r, s, n, i, i + 2 * COLS, 0.12);
                }
            }
        }
        this.ca = java.util.Arrays.copyOf(a, n);
        this.cb = java.util.Arrays.copyOf(b, n);
        this.rest = java.util.Arrays.copyOf(r, n);
        this.stiff = java.util.Arrays.copyOf(s, n);
    }

    private static int add(int[] a, int[] b, double[] r, double[] s, int n, int i, int j, double stiffness) {
        a[n] = i;
        b[n] = j;
        double du = sheetX(i) - sheetX(j);
        double dv = sheetY(i) - sheetY(j);
        r[n] = Math.sqrt(du * du + dv * dv);
        s[n] = stiffness;
        return n + 1;
    }

    static double across(int c) {
        return (double) c / (COLS - 1) - 0.5;
    }

    static double down(int row) {
        return (double) row / (ROWS - 1);
    }

    static double width(double v) {
        return TOP_WIDTH + (BOTTOM_WIDTH - TOP_WIDTH) * Math.pow(v, 0.85);
    }

    /** The cloth laid flat (blocks): x across, y down. */
    private static double sheetX(int i) {
        return across(i % COLS) * width(down(i / COLS));
    }

    private static double sheetY(int i) {
        return down(i / COLS) * LENGTH;
    }

    /** A point of the shoulder line in the body's space (pixels), for column c. */
    public static void anchor(int c, Vector3f out) {
        double u = across(c) * 2.0;
        out.set((float) (u * ANCHOR_HALF_WIDTH), ANCHOR_Y, (float) (ANCHOR_Z - 0.9 * u * u)).mul(1.0F / 16.0F);
    }

    /**
     * Moves the cloth to this frame.
     *
     * @param body      the body part's space (model units) to the entity's space (world axes, blocks, from origin)
     * @param colliders torso, right leg, left leg: their parts' spaces to the entity's space
     */
    public void update(BlockGetter level, double time, long gameTime, Matrix4f body, Matrix4f[] colliders, double originX, double originY,
                       double originZ) {
        this.lastSeen = gameTime;
        this.ox = originX;
        this.oy = originY;
        this.oz = originZ;
        for (int c = 0; c < COLS; c++) {
            anchor(c, this.tmp);
            body.transformPosition(this.tmp);
            this.ax[c] = originX + this.tmp.x;
            this.ay[c] = originY + this.tmp.y;
            this.az[c] = originZ + this.tmp.z;
        }
        body.transformDirection(this.tmp.set(0.0F, 0.0F, 1.0F)).normalize();
        this.backX = this.tmp.x;
        this.backY = this.tmp.y;
        this.backZ = this.tmp.z;
        for (int j = 0; j < 3; j++) {
            this.toWorld[j].set(colliders[j]);
            colliders[j].invert(this.toLocal[j]);
        }
        int mid = COLS / 2;
        double jump = sq(this.ax[mid] - this.fax[mid]) + sq(this.ay[mid] - this.fay[mid]) + sq(this.az[mid] - this.faz[mid]);
        if (!this.ready || time < this.simTime || time - this.simTime > 40.0 || jump > 64.0) {
            this.reset(level, time, gameTime);
            this.rememberFrame();
            return;
        }
        double turn = wrap(lineYaw(this.ax, this.az) - lineYaw(this.fax, this.faz));
        if (jump > 0.8 || Math.abs(turn) > Math.toRadians(50.0)) {
            // teleported (or turned at once): the whole mantle comes along as it is, no yank
            this.carry(this.ax[mid] - this.fax[mid], this.ay[mid] - this.fay[mid], this.az[mid] - this.faz[mid], turn, mid);
        }
        this.rememberFrame();
        if (gameTime - this.flipCheckTick >= 40L) {
            this.checkFlipped(level, time, gameTime);
        }
        if (gameTime != this.floorTick) {
            this.floorTick = gameTime;
            for (int i = COLS; i < N; i++) {
                this.floor[i] = floorAt(level, this.x[i], this.y[i], this.z[i]);
            }
            this.bodyFloor = floorAt(level, this.ax[mid], this.ay[mid] - 1.0, this.az[mid]);
        }
        this.drape(level, false, this.tx, this.ty, this.tz);
        int steps = (int) Math.floor((time - this.simTime) / STEP);
        if (steps > MAX_STEPS) {
            this.simTime = time - MAX_STEPS * STEP;
            steps = MAX_STEPS;
        }
        if (steps <= 0) {
            return;
        }
        double[] fromX = this.oax.clone();
        double[] fromY = this.oay.clone();
        double[] fromZ = this.oaz.clone();
        for (int k = 1; k <= steps; k++) {
            double alpha = (double) k / steps;
            for (int c = 0; c < COLS; c++) {
                this.x[c] = fromX[c] + (this.ax[c] - fromX[c]) * alpha;
                this.y[c] = fromY[c] + (this.ay[c] - fromY[c]) * alpha;
                this.z[c] = fromZ[c] + (this.az[c] - fromZ[c]) * alpha;
                this.px[c] = this.x[c];
                this.py[c] = this.y[c];
                this.pz[c] = this.z[c];
            }
            this.step(this.simTime + k * STEP);
        }
        this.simTime += steps * STEP;
        System.arraycopy(this.ax, 0, this.oax, 0, COLS);
        System.arraycopy(this.ay, 0, this.oay, 0, COLS);
        System.arraycopy(this.az, 0, this.oaz, 0, COLS);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double lineYaw(double[] x, double[] z) {
        return Math.atan2(z[COLS - 1] - z[0], x[COLS - 1] - x[0]);
    }

    private static double wrap(double a) {
        while (a > Math.PI) {
            a -= Math.PI * 2.0;
        }
        while (a < -Math.PI) {
            a += Math.PI * 2.0;
        }
        return a;
    }

    private void rememberFrame() {
        System.arraycopy(this.ax, 0, this.fax, 0, COLS);
        System.arraycopy(this.ay, 0, this.fay, 0, COLS);
        System.arraycopy(this.az, 0, this.faz, 0, COLS);
    }

    /** Moves (and turns about the shoulders' middle) the whole cloth, its last positions too: same shape, same speed. */
    private void carry(double dx, double dy, double dz, double turn, int mid) {
        double cx = this.ax[mid];
        double cz = this.az[mid];
        double c = Math.cos(turn);
        double s = Math.sin(turn);
        for (int i = 0; i < N; i++) {
            double[][] sets = {{this.x[i], this.z[i]}, {this.px[i], this.pz[i]}};
            for (int k = 0; k < 2; k++) {
                double rx = sets[k][0] + dx - cx;
                double rz = sets[k][1] + dz - cz;
                double nx = cx + rx * c - rz * s;
                double nz = cz + rx * s + rz * c;
                if (k == 0) {
                    this.x[i] = nx;
                    this.z[i] = nz;
                } else {
                    this.px[i] = nx;
                    this.pz[i] = nz;
                }
            }
            this.y[i] += dy;
            this.py[i] += dy;
        }
        for (int c2 = 0; c2 < COLS; c2++) {
            this.oax[c2] = this.ax[c2];
            this.oay[c2] = this.ay[c2];
            this.oaz[c2] = this.az[c2];
        }
        // the ground is somewhere else now: looked up again right away
        this.floorTick = Long.MIN_VALUE;
    }

    /**
     * A safety net: if the king stands still and the train on the ground shows its lining (it folded
     * over itself after something violent), it is laid out again.
     */
    private void checkFlipped(BlockGetter level, double time, long gameTime) {
        this.flipCheckTick = gameTime;
        int mid = COLS / 2;
        double moved = sq(this.ax[mid] - this.stillX) + sq(this.ay[mid] - this.stillY) + sq(this.az[mid] - this.stillZ);
        this.stillX = this.ax[mid];
        this.stillY = this.ay[mid];
        this.stillZ = this.az[mid];
        if (moved > 0.01) {
            return;
        }
        int ground = 0;
        int lining = 0;
        for (int row = 1; row < ROWS - 1; row++) {
            for (int c = 1; c < COLS - 1; c++) {
                int i = row * COLS + c;
                if (this.y[i] > this.floor[i] + 0.08) {
                    continue;
                }
                double hx = this.x[i + 1] - this.x[i - 1], hy = this.y[i + 1] - this.y[i - 1], hz = this.z[i + 1] - this.z[i - 1];
                double vx = this.x[i + COLS] - this.x[i - COLS], vy = this.y[i + COLS] - this.y[i - COLS], vz = this.z[i + COLS] - this.z[i - COLS];
                // down x across, the velvet's side (as in Capes.sheet)
                double ny = vz * hx - vx * hz;
                if (this.outerFlip) {
                    ny = -ny;
                }
                ground++;
                if (ny < 0.0) {
                    lining++;
                }
            }
        }
        if (ground >= 8 && lining > ground * 0.6) {
            this.reset(level, time, gameTime);
        }
    }

    /** Hanging straight down from the shoulders, the rest lying behind on the ground; then a moment to settle. */
    /**
     * The mantle hanging straight down from the shoulders and, where it reaches the ground, lying flat
     * behind the king: the shape a reset starts from, and the one the cloth is gently drawn towards
     * (perPoint: the ground under every point; otherwise the ground under the king, for speed).
     */
    private void drape(BlockGetter level, boolean perPoint, double[] ox, double[] oy, double[] oz) {
        double rx = this.ax[COLS - 1] - this.ax[0];
        double rz = this.az[COLS - 1] - this.az[0];
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1.0E-4) {
            rx = 1.0;
            rz = 0.0;
            rl = 1.0;
        }
        rx /= rl;
        rz /= rl;
        double bh = Math.sqrt(this.backX * this.backX + this.backZ * this.backZ);
        double bx = bh > 1.0E-4 ? this.backX / bh : 0.0;
        double bz = bh > 1.0E-4 ? this.backZ / bh : 1.0;
        double seg = LENGTH / (ROWS - 1);
        for (int c = 0; c < COLS; c++) {
            double cx = this.ax[c];
            double cy = this.ay[c];
            double cz = this.az[c];
            ox[c] = cx;
            oy[c] = cy;
            oz[c] = cz;
            for (int row = 1; row < ROWS; row++) {
                double flare = across(c) * (width(down(row)) - width(down(row - 1)));
                double nx = cx + rx * flare + bx * 0.012 * row;
                double ny = cy - seg;
                double nz = cz + rz * flare + bz * 0.012 * row;
                double f = perPoint ? floorAt(level, nx, cy, nz) : this.bodyFloor;
                if (ny < f + 0.02) {
                    nx = cx + rx * flare + bx * seg;
                    nz = cz + rz * flare + bz * seg;
                    ny = Math.max(f + 0.02, cy - 0.02);
                }
                int i = row * COLS + c;
                ox[i] = cx = nx;
                oy[i] = cy = ny;
                oz[i] = cz = nz;
            }
        }
    }

    private void reset(BlockGetter level, double time, long gameTime) {
        this.bodyFloor = floorAt(level, this.ax[COLS / 2], this.ay[COLS / 2] - 1.0, this.az[COLS / 2]);
        this.drape(level, true, this.x, this.y, this.z);
        this.drape(level, false, this.tx, this.ty, this.tz);
        for (int i = 0; i < N; i++) {
            this.px[i] = this.x[i];
            this.py[i] = this.y[i];
            this.pz[i] = this.z[i];
            this.floor[i] = i < COLS ? -1.0E9 : floorAt(level, this.x[i], this.y[i], this.z[i]);
        }
        this.floorTick = gameTime;
        System.arraycopy(this.ax, 0, this.oax, 0, COLS);
        System.arraycopy(this.ay, 0, this.oay, 0, COLS);
        System.arraycopy(this.az, 0, this.oaz, 0, COLS);
        for (int k = 0; k < 40; k++) {
            this.step(time);
        }
        this.simTime = time;
        this.ready = true;
        // the velvet is the side that faces away from the body
        double e1x = this.x[COLS] - this.x[0], e1y = this.y[COLS] - this.y[0], e1z = this.z[COLS] - this.z[0];
        double e2x = this.x[COLS + 1] - this.x[0], e2y = this.y[COLS + 1] - this.y[0], e2z = this.z[COLS + 1] - this.z[0];
        double nx = e1y * e2z - e1z * e2y;
        double ny = e1z * e2x - e1x * e2z;
        double nz = e1x * e2y - e1y * e2x;
        this.outerFlip = nx * this.backX + ny * this.backY + nz * this.backZ < 0.0;
    }

    private void step(double time) {
        double t = time / 20.0;
        // a breeze that turns and gusts, the same for every mantle
        double gust = 0.55 + 0.45 * Math.sin(t * 0.71) * Math.sin(t * 1.93 + 0.6);
        double windX = (0.45 * Math.sin(t * 0.21) + 0.2 * Math.sin(t * 1.13 + 1.7)) * gust;
        double windY = 0.04 * Math.sin(t * 2.3);
        double windZ = (0.45 * Math.cos(t * 0.17) + 0.2 * Math.sin(t * 0.93 + 0.4)) * gust;
        double dt2 = DT * DT;
        for (int row = 1; row < ROWS; row++) {
            for (int c = 0; c < COLS; c++) {
                int i = row * COLS + c;
                // the cloth's normal here, from its neighbours
                int l = row * COLS + Math.max(0, c - 1);
                int r = row * COLS + Math.min(COLS - 1, c + 1);
                int u = (row - 1) * COLS + c;
                int d = Math.min(ROWS - 1, row + 1) * COLS + c;
                double hx = this.x[r] - this.x[l], hy = this.y[r] - this.y[l], hz = this.z[r] - this.z[l];
                double vx = this.x[d] - this.x[u], vy = this.y[d] - this.y[u], vz = this.z[d] - this.z[u];
                double nx = hy * vz - hz * vy;
                double ny = hz * vx - hx * vz;
                double nz = hx * vy - hy * vx;
                double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double velX = (this.x[i] - this.px[i]) / DT;
                double velY = (this.y[i] - this.py[i]) / DT;
                double velZ = (this.z[i] - this.pz[i]) / DT;
                double accX = 0.0, accY = -GRAVITY, accZ = 0.0;
                if (nl > 1.0E-6) {
                    nx /= nl;
                    ny /= nl;
                    nz /= nl;
                    double relX = windX - velX, relY = windY - velY, relZ = windZ - velZ;
                    double push = AERO * (relX * nx + relY * ny + relZ * nz);
                    accX += push * nx + relX * 0.12;
                    accY += push * ny + relY * 0.12;
                    accZ += push * nz + relZ * 0.12;
                }
                double nxp = this.x[i] + (this.x[i] - this.px[i]) * DAMPING + accX * dt2;
                double nyp = this.y[i] + (this.y[i] - this.py[i]) * DAMPING + accY * dt2;
                double nzp = this.z[i] + (this.z[i] - this.pz[i]) * DAMPING + accZ * dt2;
                this.px[i] = this.x[i];
                this.py[i] = this.y[i];
                this.pz[i] = this.z[i];
                this.x[i] = nxp;
                this.y[i] = nyp;
                this.z[i] = nzp;
            }
        }
        // a gentle pull towards the drape: the train stays behind the king and never stays folded over
        for (int row = 1; row < ROWS; row++) {
            double k = PULL * (0.2 + 0.8 * row / (ROWS - 1.0));
            for (int c = 0; c < COLS; c++) {
                int i = row * COLS + c;
                this.x[i] += (this.tx[i] - this.x[i]) * k;
                this.y[i] += (this.ty[i] - this.y[i]) * k * 0.5;
                this.z[i] += (this.tz[i] - this.z[i]) * k;
            }
        }
        for (int it = 0; it < ITERATIONS; it++) {
            for (int k = 0; k < this.ca.length; k++) {
                int a = this.ca[k];
                int b = this.cb[k];
                boolean fa = a >= COLS;
                boolean fb = b >= COLS;
                if (!fa && !fb) {
                    continue;
                }
                double dx = this.x[b] - this.x[a];
                double dy = this.y[b] - this.y[a];
                double dz = this.z[b] - this.z[a];
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist < 1.0E-7) {
                    continue;
                }
                double diff = (dist - this.rest[k]) / dist * this.stiff[k];
                if (fa && fb) {
                    diff *= 0.5;
                    this.x[a] += dx * diff;
                    this.y[a] += dy * diff;
                    this.z[a] += dz * diff;
                    this.x[b] -= dx * diff;
                    this.y[b] -= dy * diff;
                    this.z[b] -= dz * diff;
                } else if (fa) {
                    this.x[a] += dx * diff;
                    this.y[a] += dy * diff;
                    this.z[a] += dz * diff;
                } else {
                    this.x[b] -= dx * diff;
                    this.y[b] -= dy * diff;
                    this.z[b] -= dz * diff;
                }
            }
        }
        for (int i = COLS; i < N; i++) {
            this.collide(i);
        }
    }

    /** Out of the torso and the legs (to the back or the side, never through the front), and on the ground. */
    private void collide(int i) {
        for (int j = 0; j < 3; j++) {
            float[] box = this.boxes[j];
            this.tmp.set((float) (this.x[i] - this.ox), (float) (this.y[i] - this.oy), (float) (this.z[i] - this.oz));
            this.toLocal[j].transformPosition(this.tmp);
            float lx = this.tmp.x * 16.0F;
            float ly = this.tmp.y * 16.0F;
            float lz = this.tmp.z * 16.0F;
            if (lx <= box[0] || lx >= box[1] || ly <= box[2] || ly >= box[3] || lz <= box[4] || lz >= box[5]) {
                continue;
            }
            // out by the nearest side: cloth that got in from the front goes back out in front, never through
            float back = box[5] - lz;
            float front = lz - box[4];
            float left = lx - box[0];
            float right = box[1] - lx;
            float least = Math.min(Math.min(back, front), Math.min(left, right));
            if (least == back) {
                lz = box[5];
            } else if (least == front) {
                lz = box[4];
            } else if (least == left) {
                lx = box[0];
            } else {
                lx = box[1];
            }
            this.tmp.set(lx / 16.0F, ly / 16.0F, lz / 16.0F);
            this.toWorld[j].transformPosition(this.tmp);
            this.x[i] = this.ox + this.tmp.x;
            this.y[i] = this.oy + this.tmp.y;
            this.z[i] = this.oz + this.tmp.z;
        }
        double f = this.floor[i] + 0.015;
        if (this.y[i] < f) {
            this.y[i] = f;
            // the train drags: the ground holds it
            this.px[i] += (this.x[i] - this.px[i]) * 0.3;
            this.pz[i] += (this.z[i] - this.pz[i]) * 0.3;
            if (this.py[i] < f) {
                this.py[i] = f;
            }
        }
    }

    /** The top of the ground under a point (or -1e9): blocks a little above it count, a ledge high above does not. */
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

    /** The point to draw at (row, column) now: the top row exactly on the shoulders of this frame. */
    public double px(int row, int c) {
        return row == 0 ? this.ax[c] : this.x[row * COLS + c];
    }

    public double py(int row, int c) {
        return row == 0 ? this.ay[c] : this.y[row * COLS + c];
    }

    public double pz(int row, int c) {
        return row == 0 ? this.az[c] : this.z[row * COLS + c];
    }

    public boolean ready() {
        return this.ready;
    }
}
