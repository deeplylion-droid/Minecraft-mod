package com.wurstel.client.geom;

/**
 * Builds the würstel as a smooth, bendable tube with rounded ends.
 *
 * <p>Local frame (same as Minecraft model space after the head rotation): the origin is the
 * attachment point at the belt buckle, {@code -Z} is where the wearer looks, {@code -Y} is up and
 * {@code -X} is the wearer's right. Units are blocks.</p>
 *
 * <p>Texture layout: {@code u} wraps around the sausage (0 and 1 on the underside, 0.5 on top),
 * {@code v} runs along it (0 at the belt end, 1 at the tip).</p>
 */
public final class WurstelGeometry {

    /** End-to-end length in blocks. */
    public static final float LENGTH = 4.0f;
    /** Radius in blocks: the würstel is a quarter of a block thick. */
    public static final float RADIUS = 0.125f;
    public static final int SIDES = 14;

    private static final int BODY_SEGMENTS = 40;
    private static final int CAP_SEGMENTS = 5;
    private static final int RINGS = CAP_SEGMENTS + BODY_SEGMENTS + 1 + CAP_SEGMENTS;

    /** Supplies the bend at position u (0 = belt, 1 = tip): out = {yaw right+, pitch up+} radians. */
    @FunctionalInterface
    public interface Bend {
        void sample(float u, float[] out);
    }

    @FunctionalInterface
    public interface VertexSink {
        void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz);
    }

    private final float[] px = new float[RINGS], py = new float[RINGS], pz = new float[RINGS];
    private final float[] tx = new float[RINGS], ty = new float[RINGS], tz = new float[RINGS];
    private final float[] nx = new float[RINGS], ny = new float[RINGS], nz = new float[RINGS];
    private final float[] bx = new float[RINGS], by = new float[RINGS], bz = new float[RINGS];
    private final float[] radius = new float[RINGS];
    private final float[] radialN = new float[RINGS];
    private final float[] axialN = new float[RINGS];
    private final float[] texV = new float[RINGS];

    private final float[] cos = new float[SIDES + 1];
    private final float[] sin = new float[SIDES + 1];
    private final float[] bend = new float[2];
    private final float[] dir = new float[3];

    public WurstelGeometry() {
        for (int j = 0; j <= SIDES; j++) {
            // Start on the underside so the texture seam stays hidden; u = 0.5 lands on top.
            double theta = Math.PI + 2.0 * Math.PI * j / SIDES;
            cos[j] = (float) Math.cos(theta);
            sin[j] = (float) Math.sin(theta);
        }
    }

    /**
     * Computes the rings of the tube.
     *
     * @param lengthScale 1 for the real 4-block würstel; smaller to shorten it (e.g. in GUIs)
     */
    public void build(Bend bendFn, float lengthScale) {
        final float length = LENGTH * lengthScale;
        final float r = Math.min(RADIUS, length * 0.25f);
        final float bodyLength = length - 2f * r;
        final int body0 = CAP_SEGMENTS;                 // ring index of the body start (u = 0)
        final int bodyN = CAP_SEGMENTS + BODY_SEGMENTS; // ring index of the body end (u = 1)
        final float ds = bodyLength / BODY_SEGMENTS;

        // Centre line, integrated from the buckle with the bend sampled at each segment midpoint.
        float x = 0f, y = 0f, z = 0f;
        for (int k = 0; k <= BODY_SEGMENTS; k++) {
            int i = body0 + k;
            if (k > 0) {
                direction(bendFn, (k - 0.5f) / BODY_SEGMENTS);
                x += dir[0] * ds;
                y += dir[1] * ds;
                z += dir[2] * ds;
            }
            direction(bendFn, k / (float) BODY_SEGMENTS);
            px[i] = x;
            py[i] = y;
            pz[i] = z;
            tx[i] = dir[0];
            ty[i] = dir[1];
            tz[i] = dir[2];
            radius[i] = r;
            radialN[i] = 1f;
            axialN[i] = 0f;
            texV[i] = (r + k * ds) / length;
        }

        // Frame carried along the curve (projection-based parallel transport), starting "up".
        float ux = 0f, uy = -1f, uz = 0f;
        for (int i = body0; i <= bodyN; i++) {
            float d = ux * tx[i] + uy * ty[i] + uz * tz[i];
            float ax = ux - d * tx[i], ay = uy - d * ty[i], az = uz - d * tz[i];
            float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            if (len < 1e-4f) { // tangent parallel to the old normal: pick any perpendicular
                ax = 0f;
                ay = tz[i];
                az = -ty[i];
                len = (float) Math.sqrt(ay * ay + az * az);
                if (len < 1e-4f) {
                    ax = 1f;
                    ay = 0f;
                    az = 0f;
                    len = 1f;
                }
            }
            nx[i] = ax / len;
            ny[i] = ay / len;
            nz[i] = az / len;
            // binormal = tangent x normal
            bx[i] = ty[i] * nz[i] - tz[i] * ny[i];
            by[i] = tz[i] * nx[i] - tx[i] * nz[i];
            bz[i] = tx[i] * ny[i] - ty[i] * nx[i];
            ux = nx[i];
            uy = ny[i];
            uz = nz[i];
        }

        // Rounded ends: half spheres continuing the first and last body rings.
        for (int k = 0; k < CAP_SEGMENTS; k++) {
            // base cap, from the pole (beta = 0) towards the equator
            double beta = (Math.PI / 2.0) * k / CAP_SEGMENTS;
            float cb = (float) Math.cos(beta), sb = (float) Math.sin(beta);
            capRing(k, body0, -cb * r, sb * r, sb, -cb, (r - r * cb) / length);

            // tip cap, from the equator (excluded) towards the pole
            double beta2 = (Math.PI / 2.0) * (CAP_SEGMENTS - 1 - k) / CAP_SEGMENTS;
            float cb2 = (float) Math.cos(beta2), sb2 = (float) Math.sin(beta2);
            capRing(bodyN + 1 + k, bodyN, cb2 * r, sb2 * r, sb2, cb2, (length - r + r * cb2) / length);
        }
    }

    private void capRing(int i, int src, float along, float rad, float radial, float axial, float v) {
        tx[i] = tx[src];
        ty[i] = ty[src];
        tz[i] = tz[src];
        nx[i] = nx[src];
        ny[i] = ny[src];
        nz[i] = nz[src];
        bx[i] = bx[src];
        by[i] = by[src];
        bz[i] = bz[src];
        px[i] = px[src] + tx[src] * along;
        py[i] = py[src] + ty[src] * along;
        pz[i] = pz[src] + tz[src] * along;
        radius[i] = rad;
        radialN[i] = radial;
        axialN[i] = axial;
        texV[i] = v;
    }

    /** Unit direction for the bend at u, in the local frame (-Z forward, -Y up, -X right). */
    private void direction(Bend bendFn, float u) {
        bendFn.sample(u, bend);
        float yaw = bend[0], pitch = bend[1];
        float cp = (float) Math.cos(pitch);
        dir[0] = -(float) Math.sin(yaw) * cp;
        dir[1] = -(float) Math.sin(pitch);
        dir[2] = -(float) Math.cos(yaw) * cp;
    }

    /** Emits the tube as quads (4 vertices each, counter-clockwise seen from outside). */
    public void emitQuads(VertexSink sink) {
        for (int i = 0; i < RINGS - 1; i++) {
            for (int j = 0; j < SIDES; j++) {
                emit(sink, i, j);
                emit(sink, i, j + 1);
                emit(sink, i + 1, j + 1);
                emit(sink, i + 1, j);
            }
        }
    }

    private void emit(VertexSink sink, int i, int j) {
        float c = cos[j], s = sin[j];
        float rx = c * nx[i] + s * bx[i];
        float ry = c * ny[i] + s * by[i];
        float rz = c * nz[i] + s * bz[i];
        float rad = radius[i];
        float a = axialN[i], b = radialN[i];
        sink.vertex(
                px[i] + rx * rad, py[i] + ry * rad, pz[i] + rz * rad,
                j / (float) SIDES, texV[i],
                rx * b + tx[i] * a, ry * b + ty[i] * a, rz * b + tz[i] * a);
    }

    /** Centre of the tip in the local frame (valid after {@link #build}). */
    public void tipCenter(float[] out) {
        int i = RINGS - 1;
        out[0] = px[i];
        out[1] = py[i];
        out[2] = pz[i];
    }
}
