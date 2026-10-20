package com.frusta.client.render;

import com.frusta.motion.V3;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The camera of the frame being drawn, so points found while drawing an entity (in view space) can be
 * turned back into world positions. Set at the start of every world render.
 */
public final class ViewSpace {
    private static final Matrix4f VIEW_TO_WORLD = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;

    private ViewSpace() {
    }

    /** {@code view}: the world render's pose matrix (the camera rotation only). */
    public static void set(Matrix4f view, Vec3 cameraPos) {
        VIEW_TO_WORLD.set(view).invert();
        camera = cameraPos;
    }

    public static Vec3 camera() {
        return camera;
    }

    /** A point in view space (what a pose matrix of the world render gives) as a world position. */
    public static V3 toWorld(Vector3f view) {
        Vector3f w = VIEW_TO_WORLD.transformPosition(new Vector3f(view));
        return new V3(camera.x + w.x(), camera.y + w.y(), camera.z + w.z());
    }

    /** A direction in view space as a world direction. */
    public static V3 dirToWorld(Vector3f view) {
        Vector3f w = VIEW_TO_WORLD.transformDirection(new Vector3f(view));
        return new V3(w.x(), w.y(), w.z());
    }
}
