package com.punizioni.client.bonds;

import com.mojang.blaze3d.vertex.PoseStack;
import com.punizioni.client.render.Ropes;
import com.punizioni.data.Bound;
import com.punizioni.data.ModData;
import com.punizioni.machine.Machine;
import com.punizioni.server.Bonds;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Prisoners on the client: your own client pulls you along when the rope is taut (the server does
 * not move players: that would rubber-band), and every rope is drawn from the knot on the prisoner's belly
 * to the captor's hand (or to the knot on a fence post), sagging when slack. The ends come from the
 * drawn models when they are drawn (BondsLayer), else from where the hands would be.
 */
public final class Captives {
    private Captives() {
    }

    /** Players who lead someone now (their hand is an end of a rope). */
    private static final Set<UUID> CAPTORS = new HashSet<>();

    /** Rope ends worked out from the drawn models this frame (or the one before). */
    private record End(Vec3 pos, long frame) {
    }

    private static final Map<Integer, End> WRISTS = new HashMap<>();
    private static final Map<Integer, End> HANDS = new HashMap<>();
    private static long frame;
    /** The knot's model (from the bonds model, set by BondsLayer). */
    static @Nullable ModelPart knot;

    public static boolean isCaptor(UUID id) {
        return CAPTORS.contains(id);
    }

    public static void reset() {
        CAPTORS.clear();
        WRISTS.clear();
        HANDS.clear();
    }

    static void wrists(int entityId, Vec3 pos) {
        WRISTS.put(entityId, new End(pos, frame));
    }

    static void hand(int entityId, Vec3 pos) {
        HANDS.put(entityId, new End(pos, frame));
    }

    // ------------------------------------------------------------------ every tick

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        CAPTORS.clear();
        if (level == null) {
            return;
        }
        for (Player p : level.players()) {
            Bound b = ModData.bound(p);
            if (b != null && b.knot().isEmpty()) {
                CAPTORS.add(b.captor());
            }
        }
        if (WRISTS.size() > 256 || HANDS.size() > 256) {
            WRISTS.clear();
            HANDS.clear();
        }
        LocalPlayer me = mc.player;
        if (me == null) {
            return;
        }
        Bound b = ModData.bound(me);
        if (b == null || me.getVehicle() != null) {
            return;
        }
        Vec3 holder = holder(level, b);
        if (holder == null) {
            return;
        }
        Vec3 to = holder.subtract(me.position());
        double dist = to.length();
        if (dist > Bonds.LENGTH && dist < Bonds.FAR) {
            // the rope does not stretch: no going further, and a pull back towards the other end
            Vec3 n = to.scale(1.0 / dist);
            Vec3 v = me.getDeltaMovement();
            double away = -v.dot(n);
            if (away > 0.0) {
                v = v.add(n.scale(away));
            }
            v = v.add(n.scale(Math.min(0.45, (dist - Bonds.LENGTH) * 0.15)));
            me.setDeltaMovement(v);
            if (me.horizontalCollision && me.onGround()) {
                // stumbles over whatever is in the way
                me.jumpFromGround();
            }
        }
    }

    /** Where the rope's other end is (the captor's feet, or the post), or null: not here. */
    public static @Nullable Vec3 holder(ClientLevel level, Bound b) {
        if (b.knot().isPresent()) {
            return Vec3.atBottomCenterOf(b.knot().get());
        }
        Player captor = level.getPlayerByUUID(b.captor());
        return captor != null ? captor.position() : null;
    }

    // ------------------------------------------------------------------ drawing

    public static void submit(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            frame++;
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        CameraRenderState cameraState = context.levelState().cameraRenderState;
        Vec3 camera = cameraState.pos;
        PoseStack poseStack = context.poseStack();
        for (Player p : level.players()) {
            Bound b = ModData.bound(p);
            if (b == null || p.getVehicle() instanceof Machine || p.position().distanceToSqr(camera) > 96.0 * 96.0) {
                continue;
            }
            Vec3 a = fresh(WRISTS, p.getId());
            if (a == null) {
                a = wristsGuess(p, partial);
            }
            Vec3 c;
            if (b.knot().isPresent()) {
                BlockPos post = b.knot().get();
                c = new Vec3(post.getX() + 0.5, post.getY() + 0.62, post.getZ() + 0.5);
                knot(context, poseStack, camera, post, level);
            } else {
                Player captor = level.getPlayerByUUID(b.captor());
                if (captor == null) {
                    continue;
                }
                c = fresh(HANDS, captor.getId());
                if (c == null) {
                    c = captor == mc.player && mc.options.getCameraType().isFirstPerson() ? firstPersonHand(cameraState) : handGuess(captor, partial);
                }
            }
            double dist = a.distanceTo(c);
            double slack = Math.max(0.0, Bonds.LENGTH - dist);
            double sag = 0.05 + slack * 0.32;
            int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(a.add(c).scale(0.5)));
            poseStack.pushPose();
            Ropes.rope(context.submitNodeCollector(), poseStack, camera, camera, Ropes.sag(a, c, sag, 14), 0.055F, light);
            poseStack.popPose();
        }
        frame++;
    }

    private static @Nullable Vec3 fresh(Map<Integer, End> ends, int id) {
        End e = ends.get(id);
        return e != null && e.frame >= frame - 1 ? e.pos : null;
    }

    /** The knot on the belly of a prisoner tied like a salami. */
    private static Vec3 wristsGuess(Player p, float partial) {
        float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 pos = p.getPosition(partial);
        double h = p.isCrouching() ? 0.75 : 0.98;
        return pos.add(-Mth.sin(yaw) * 0.19, h * p.getScale(), Mth.cos(yaw) * 0.19);
    }

    /** The main hand hanging at the side. */
    private static Vec3 handGuess(Player p, float partial) {
        float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 pos = p.getPosition(partial);
        double side = p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? -1.0 : 1.0;
        return pos.add(Mth.cos(yaw) * 0.38 * side - Mth.sin(yaw) * 0.12, 0.72 * p.getScale(), Mth.sin(yaw) * 0.38 * side + Mth.cos(yaw) * 0.12);
    }

    /** Your own hand in first person: low on the right of the view. */
    private static Vec3 firstPersonHand(CameraRenderState cameraState) {
        Matrix4f toWorld = new Matrix4f(cameraState.viewRotationMatrix).invert();
        Vector3f off = toWorld.transformDirection(new Vector3f(0.36F, -0.42F, -0.55F));
        return cameraState.pos.add(off.x, off.y, off.z);
    }

    /** The rope wound round the fence post. */
    private static void knot(LevelRenderContext context, PoseStack poseStack, Vec3 camera, BlockPos post, ClientLevel level) {
        if (knot == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(post.getX() + 0.5 - camera.x, post.getY() + 0.62 - camera.y, post.getZ() + 0.5 - camera.z);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        context.submitNodeCollector().submitModelPart(knot, poseStack, RenderTypes.entityCutout(BondsLayer.TEXTURE),
                LightCoordsUtil.getLightCoords(level, post), OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();
    }
}
