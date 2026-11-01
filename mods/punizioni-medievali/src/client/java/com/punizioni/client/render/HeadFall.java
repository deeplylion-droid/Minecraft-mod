package com.punizioni.client.render;

import com.punizioni.machine.Guillotine;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * The guillotine's head falling into the basket: the same path for whoever watches (MachineRenderer)
 * and for the victim, who sees it from inside the head (VictimCamera). Positions are in the machine's
 * frame (right, up, forward); the head's orientation is in the frame of the pose stack rotated like
 * the machine (-z is where the machine looks, +y up), with the face towards -z.
 */
public final class HeadFall {
    private HeadFall() {
    }

    /** The fall takes this many ticks, then a bounce and a little roll. */
    public static final float FALL = 9.0F;
    public static final float SETTLE = 16.0F;

    /** Where the middle of the head starts: just past the lunette. */
    private static final Vec3 START = new Vec3(0.0, Guillotine.NECK.y, Guillotine.NECK.z + 0.24);
    /** Where it rests: on its side in the basket. */
    private static final Vec3 REST = new Vec3(Guillotine.BASKET.x + 0.06, Guillotine.BASKET.y + 0.07, Guillotine.BASKET.z);

    /** The middle of the head, ticks after the blade (machine frame). */
    public static Vec3 position(float t) {
        if (t <= 0.0F) {
            return START;
        }
        if (t < FALL) {
            float k = t / FALL;
            // pushed a little forward by the blade, then straight down, faster and faster
            double x = Mth.lerp(k, START.x, REST.x);
            double z = Mth.lerp(1.0F - (1.0F - k) * (1.0F - k), START.z, REST.z);
            double y = Mth.lerp(k * k, START.y, REST.y);
            return new Vec3(x, y, z);
        }
        float s = t - FALL;
        double bounce = s < 6.0F ? Math.abs(Mth.sin(s / 6.0F * Mth.PI)) * 0.09 * (1.0F - s / 6.0F) : 0.0;
        return REST.add(0.0, bounce, 0.0);
    }

    /** The head's orientation (see the class comment), ticks after the blade. */
    public static Quaternionf orientation(float t) {
        float k = Mth.clamp(t / SETTLE, 0.0F, 1.0F);
        float e = 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
        // it tumbles forward (one and a bit turns), turns round to face the machine, ends on its side
        float yaw = Mth.lerp(e, 0.0F, 168.0F);
        float pitch = Mth.lerp(e, 62.0F, -14.0F + 360.0F);
        float roll = Mth.lerp(e, 0.0F, 74.0F);
        if (t > SETTLE) {
            // the last rocking
            float s = t - SETTLE;
            roll += Mth.sin(s * 0.5F) * 6.0F * (float) Math.exp(-s * 0.15F);
        }
        return new Quaternionf().rotationYXZ(-yaw * Mth.DEG_TO_RAD, -pitch * Mth.DEG_TO_RAD, roll * Mth.DEG_TO_RAD);
    }
}
