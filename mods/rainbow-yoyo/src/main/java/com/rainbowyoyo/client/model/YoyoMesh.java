package com.rainbowyoyo.client.model;

/**
 * The 3D yo-yo: two rounded halves around an axle with the string wound in the gap, built as a
 * smooth surface of revolution (no Minecraft classes, so it can be tested and previewed alone).
 *
 * <p>Model space: the axle is the X axis, the centre is the origin, units are blocks (1.25 across,
 * 0.8 wide). Texture: {@code u} goes around the axle, {@code v} along the profile, in the bands of
 * {@code textures/entity/rainbow_yoyo.png}: wound string (v &lt; 0.04), inner face (0.05 to 0.25),
 * rim (0.25 to 0.58, brightest line at 0.42), outer face (0.58 to 0.88) and cap with a star.</p>
 */
public final class YoyoMesh {
    /** Segments around the axle. */
    public static final int SEGMENTS = 48;

    /** One half, from the hub out over the rim to the centre of the outer face: {x, radius, v}. */
    private static final double[][] PROFILE = {
            {0.035, 0.100, 0.05},
            {0.035, 0.200, 0.09},
            {0.050, 0.280, 0.13},
            {0.090, 0.380, 0.18},
            {0.150, 0.480, 0.25},
            {0.215, 0.565, 0.31},
            {0.270, 0.610, 0.37},
            {0.315, 0.625, 0.42},
            {0.350, 0.612, 0.47},
            {0.372, 0.580, 0.52},
            {0.380, 0.540, 0.58},
            {0.382, 0.420, 0.66},
            {0.380, 0.300, 0.74},
            {0.383, 0.215, 0.88},
            {0.392, 0.180, 0.91},
            {0.398, 0.100, 0.95},
            {0.400, 0.000, 1.00},
    };
    /** The string wound around the axle, seen in the gap between the halves. */
    private static final double STRING_RADIUS = 0.17;
    private static final double STRING_HALF_WIDTH = 0.035;

    public static final YoyoMesh INSTANCE = new YoyoMesh();

    @FunctionalInterface
    public interface VertexSink {
        void vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v);
    }

    /** Per vertex: x, y, z, nx, ny, nz, u, v. Four vertices per quad. */
    private final float[] data;
    private int size;

    private YoyoMesh() {
        int quads = 2 * (PROFILE.length - 1) * SEGMENTS + SEGMENTS;
        data = new float[quads * 4 * 8];
        buildHalf(1);
        buildHalf(-1);
        buildString();
    }

    public int quadCount() {
        return size / 32;
    }

    /** Sends every vertex, four per quad, counter-clockwise seen from outside. */
    public void emit(VertexSink sink) {
        for (int i = 0; i < size; i += 8) {
            sink.vertex(data[i], data[i + 1], data[i + 2], data[i + 3], data[i + 4], data[i + 5],
                    data[i + 6], data[i + 7]);
        }
    }

    private void buildHalf(int side) {
        int n = PROFILE.length;
        // Profile normals in the (x, r) plane, pointing out of the solid.
        double[][] normal = new double[n][];
        for (int i = 0; i < n; i++) {
            double[] a = PROFILE[Math.max(0, i - 1)];
            double[] b = PROFILE[Math.min(n - 1, i + 1)];
            double tx = b[0] - a[0];
            double tr = b[1] - a[1];
            double len = Math.hypot(tx, tr);
            normal[i] = new double[]{-tr / len * side, tx / len};
        }
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < SEGMENTS; j++) {
                double[] a = ring(i, j, side, normal);
                double[] b = ring(i, j + 1, side, normal);
                double[] c = ring(i + 1, j + 1, side, normal);
                double[] d = ring(i + 1, j, side, normal);
                addQuad(a, b, c, d);
            }
        }
    }

    /** Vertex {x, y, z, nx, ny, nz, u, v} of profile point {@code i} at angle step {@code j}. */
    private static double[] ring(int i, int j, int side, double[][] normal) {
        double theta = 2 * Math.PI * j / SEGMENTS;
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double[] p = PROFILE[i];
        double[] nr = normal[i];
        return new double[]{side * p[0], p[1] * cos, p[1] * sin, nr[0], nr[1] * cos, nr[1] * sin,
                (double) j / SEGMENTS, p[2]};
    }

    private void buildString() {
        for (int j = 0; j < SEGMENTS; j++) {
            double t0 = 2 * Math.PI * j / SEGMENTS;
            double t1 = 2 * Math.PI * (j + 1) / SEGMENTS;
            double u0 = (double) j / SEGMENTS;
            double u1 = (double) (j + 1) / SEGMENTS;
            double r = STRING_RADIUS;
            double w = STRING_HALF_WIDTH;
            double[] a = {-w, r * Math.cos(t0), r * Math.sin(t0), 0, Math.cos(t0), Math.sin(t0), u0, 0.0};
            double[] b = {-w, r * Math.cos(t1), r * Math.sin(t1), 0, Math.cos(t1), Math.sin(t1), u1, 0.0};
            double[] c = {w, r * Math.cos(t1), r * Math.sin(t1), 0, Math.cos(t1), Math.sin(t1), u1, 0.04};
            double[] d = {w, r * Math.cos(t0), r * Math.sin(t0), 0, Math.cos(t0), Math.sin(t0), u0, 0.04};
            addQuad(a, b, c, d);
        }
    }

    /** Adds the quad, flipping the winding if needed so it faces along its normals. */
    private void addQuad(double[] a, double[] b, double[] c, double[] d) {
        // Cross product of the diagonals: robust even when two corners meet (at the cap centre).
        double ex = c[0] - a[0], ey = c[1] - a[1], ez = c[2] - a[2];
        double fx = d[0] - b[0], fy = d[1] - b[1], fz = d[2] - b[2];
        double gx = ey * fz - ez * fy;
        double gy = ez * fx - ex * fz;
        double gz = ex * fy - ey * fx;
        double nx = a[3] + b[3] + c[3] + d[3];
        double ny = a[4] + b[4] + c[4] + d[4];
        double nz = a[5] + b[5] + c[5] + d[5];
        if (gx * nx + gy * ny + gz * nz >= 0) {
            put(a);
            put(b);
            put(c);
            put(d);
        } else {
            put(a);
            put(d);
            put(c);
            put(b);
        }
    }

    private void put(double[] v) {
        for (int k = 0; k < 8; k++) {
            data[size++] = (float) v[k];
        }
    }
}
