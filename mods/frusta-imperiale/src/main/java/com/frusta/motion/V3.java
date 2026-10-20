package com.frusta.motion;

/**
 * A tiny immutable 3D vector, so the whip maths does not depend on Minecraft classes and can be
 * tested on its own. World axes are Minecraft's: +Y up; yaw 0 faces +Z (south), yaw 90 faces -X.
 */
public record V3(double x, double y, double z) {
    public static final V3 ZERO = new V3(0, 0, 0);
    public static final V3 UP = new V3(0, 1, 0);

    public V3 add(V3 o) {
        return new V3(x + o.x, y + o.y, z + o.z);
    }

    public V3 add(double dx, double dy, double dz) {
        return new V3(x + dx, y + dy, z + dz);
    }

    public V3 sub(V3 o) {
        return new V3(x - o.x, y - o.y, z - o.z);
    }

    public V3 scale(double s) {
        return new V3(x * s, y * s, z * s);
    }

    public double dot(V3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public V3 cross(V3 o) {
        return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double lengthSqr() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSqr());
    }

    /** Unit vector in the same direction, or {@code fallback} when this is (almost) zero. */
    public V3 normalize(V3 fallback) {
        double len = length();
        return len < 1e-9 ? fallback : scale(1.0 / len);
    }

    public V3 lerp(V3 o, double t) {
        return new V3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    /** Horizontal direction the yaw faces (Minecraft degrees). */
    public static V3 forward(double yawDeg) {
        double yaw = Math.toRadians(yawDeg);
        return new V3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** Horizontal direction to the right of the yaw. */
    public static V3 right(double yawDeg) {
        double yaw = Math.toRadians(yawDeg);
        return new V3(-Math.cos(yaw), 0, -Math.sin(yaw));
    }

    /** Look direction for a yaw and pitch (Minecraft degrees, positive pitch looks down). */
    public static V3 look(double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch);
        return new V3(-Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp);
    }
}
