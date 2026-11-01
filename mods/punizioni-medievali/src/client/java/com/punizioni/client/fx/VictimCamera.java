package com.punizioni.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.punizioni.client.render.HeadFall;
import com.punizioni.client.render.Poses;
import com.punizioni.client.render.Visuals;
import com.punizioni.data.Limb;
import com.punizioni.machine.BrazenBull;
import com.punizioni.machine.Gallows;
import com.punizioni.machine.Guillotine;
import com.punizioni.machine.Machine;
import com.punizioni.machine.MachineKind;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The victim's own eyes, in first person, wherever the machine puts the head: on the back on the
 * rack and on the rat table (looking at the sky), face down through the lunette, spinning with the
 * wheel, swaying on the rope, crouched inside the bull, kneeling at the block. After the guillotine
 * the view falls with the head into the basket and stays there, looking back at the body.
 *
 * <p>The body's frame is built with the same rotations the body is drawn with (Poses), so the eyes
 * are where the drawn head is; the mouse still turns the head inside that frame.
 */
public final class VictimCamera {
    private VictimCamera() {
    }

    public record View(Vec3 position, Quaternionf rotation) {
    }

    private static final float PX = 0.9375F / 16.0F;

    /** The guillotine that took the local player's head (-1: none), and when. */
    private static int headMachine = -1;
    private static long headTime;
    private static Vec3 headMachinePos = Vec3.ZERO;
    private static float headMachineYaw;

    public static void headOff(Machine machine) {
        headMachine = machine.getId();
        headTime = machine.level().getGameTime();
        headMachinePos = machine.position();
        headMachineYaw = machine.getYRot();
    }

    public static void reset() {
        headMachine = -1;
    }

    /** Called every client tick: the head view ends when the player is alive again. */
    public static void tick(Minecraft mc) {
        if (headMachine >= 0 && (mc.player == null || mc.level == null || mc.player.isAlive() && mc.level.getGameTime() - headTime > 40L
                || mc.level.getGameTime() - headTime > 20L * 60L)) {
            headMachine = -1;
        }
    }

    /** Whether this player's own head is where the camera is (then the head is not drawn: the view is from inside it). */
    public static boolean seesFromHead(java.util.@Nullable UUID owner) {
        Minecraft mc = Minecraft.getInstance();
        return headMachine >= 0 && owner != null && mc.player != null && owner.equals(mc.player.getUUID())
                && mc.options.getCameraType().isFirstPerson();
    }

    /** Ticks since the head came off (or -1). */
    public static float sinceHead(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return headMachine >= 0 && mc.level != null ? mc.level.getGameTime() - headTime + partial : -1.0F;
    }

    /** Where the camera goes instead of vanilla's eyes, or null: vanilla's. */
    public static @Nullable View view(Entity entity, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player || !(entity instanceof Avatar avatar)) {
            return null;
        }
        if (headMachine >= 0) {
            return headView(partial);
        }
        if (!(entity.getVehicle() instanceof Machine machine)) {
            return null;
        }
        Visuals v = Visuals.of(avatar, partial);
        if (v == null || v.kind == null) {
            return null;
        }
        MachineKind kind = v.kind;
        float bodyYaw = Mth.rotLerp(partial, avatar.yBodyRotO, avatar.yBodyRot);
        PoseStack ps = new PoseStack();
        ps.rotateDegrees(Axis.YP, 180.0F - bodyYaw);
        Poses.rotations(v, ps);
        Matrix4f m = ps.last().pose();

        // the eyes in the body's own frame (feet at 0, -z is the face's side)
        Vector3f eye;
        float pitchBias = 0.0F;
        float minPitch = -90.0F;
        float maxPitch = 90.0F;
        float roll = 0.0F;
        switch (kind) {
            case QUARTERING_RACK, RAT_TABLE -> {
                // on the back: the face looks up, the top of the view is past the head
                eye = new Vector3f(0.0F, 28.0F * PX, -0.2F);
                minPitch = -70.0F;
                maxPitch = 80.0F;
            }
            case GUILLOTINE -> {
                // face down through the lunette: looking down at the basket, or up at the crowd
                eye = new Vector3f(0.0F, 28.0F * PX, -0.2F);
                pitchBias = -28.0F;
                minPitch = -88.0F;
                maxPitch = 12.0F;
            }
            case WHEEL -> eye = new Vector3f(0.0F, 28.0F * PX, -0.18F);
            case GALLOWS -> {
                eye = new Vector3f(0.0F, 28.0F * PX, -0.12F);
                if (v.state == Gallows.HANGING) {
                    // the broken neck: the head hangs to one side
                    roll = 24.0F;
                    pitchBias = 20.0F;
                }
            }
            case CHOPPING_BLOCK -> {
                boolean hand = Limb.ALL[Math.floorMod(v.choice, Limb.ALL.length)].hand();
                eye = new Vector3f(0.0F, avatar.getEyeHeight(), hand ? -0.2F : 0.0F);
            }
            case BRAZEN_BULL -> eye = new Vector3f(0.0F, v.state == BrazenBull.IDLE ? avatar.getEyeHeight() : 0.62F, 0.0F);
            default -> eye = new Vector3f(0.0F, avatar.getEyeHeight(), 0.0F);
        }
        m.transformPosition(eye);
        Vec3 offset = Poses.offset(v);
        double x = Mth.lerp(partial, entity.xo, entity.getX()) + offset.x + eye.x;
        double y = Mth.lerp(partial, entity.yo, entity.getY()) + offset.y + eye.y;
        double z = Mth.lerp(partial, entity.zo, entity.getZ()) + offset.z + eye.z;

        // the frame's rotation, then the head's own turn inside it (as vanilla does it: yaw, pitch)
        Quaternionf frame = m.getNormalizedRotation(new Quaternionf());
        float rel = Mth.wrapDegrees(entity.getViewYRot(partial) - bodyYaw);
        float pitch = Mth.clamp(entity.getViewXRot(partial) + pitchBias, minPitch, maxPitch);
        Quaternionf q = frame.mul(new Quaternionf().rotationZ(roll * Mth.DEG_TO_RAD))
                .mul(new Quaternionf().rotationYXZ(-rel * Mth.DEG_TO_RAD, -pitch * Mth.DEG_TO_RAD, 0.0F));
        if (kind == MachineKind.GUILLOTINE && machine.state() == Guillotine.DROP) {
            // the blade comes: the head jerks
            q.mul(new Quaternionf().rotationZ(Mth.sin(v.age * 2.7F) * 0.05F));
        }
        return new View(new Vec3(x, y, z), q);
    }

    /** Inside the falling head: down into the basket, then looking back at the machine and the body. */
    private static @Nullable View headView(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Entity e = mc.level.getEntity(headMachine);
        Vec3 origin = e != null ? e.position() : headMachinePos;
        float yaw = e != null ? e.getYRot() : headMachineYaw;
        float t = mc.level.getGameTime() - headTime + partial;
        Vec3 c = HeadFall.position(t);
        Quaternionf frame = new Quaternionf().rotationY((180.0F - yaw) * Mth.DEG_TO_RAD);
        Quaternionf head = HeadFall.orientation(t);
        // from the middle of the head to just behind the eyes
        Vector3f eyes = head.transform(new Vector3f(0.0F, 0.03F, -0.18F));
        Vector3f local = new Vector3f((float) c.x, (float) c.y, (float) -c.z).add(eyes);
        frame.transform(local);
        return new View(origin.add(local.x, local.y, local.z), frame.mul(head));
    }
}
