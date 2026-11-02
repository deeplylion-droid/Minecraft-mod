package com.punizioni.client.royal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.punizioni.ModParticles;
import com.punizioni.ModSounds;
import com.punizioni.Punizioni;
import com.punizioni.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Every overseer's whip in sight: the lash of each player holding one, the crack when they swing
 * (heard by everyone around, even without seeing it), and the drawing of the lash as a braided,
 * tapering leather tube.
 */
public final class Lashes {
    private Lashes() {
    }

    public static final Identifier TEXTURE = Punizioni.id("textures/entity/overseer_lash.png");
    public static final RenderType TYPE = RenderTypes.entityCutoutCull(TEXTURE);
    private static final int SIDES = 6;
    private static final int SUB = 3;
    private static final float ROOT_RADIUS = 0.04F;
    private static final float TIP_RADIUS = 0.011F;

    private static final Map<Integer, Lash> LASHES = new HashMap<>();
    /** Per player: swinging last tick, and the swing's progress. */
    private static final Map<Integer, float[]> SWINGS = new HashMap<>();

    public static Lash of(int entityId) {
        return LASHES.computeIfAbsent(entityId, id -> new Lash());
    }

    public static void reset() {
        LASHES.clear();
        SWINGS.clear();
    }

    /** Every client tick: who starts a crack, and the crack's sound when its tip goes off. */
    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        long now = level.getGameTime();
        for (Player p : level.players()) {
            if (!p.getMainHandItem().is(ModItems.OVERSEER_WHIP) || p.distanceToSqr(mc.player) > 64.0 * 64.0) {
                SWINGS.remove(p.getId());
                continue;
            }
            float[] last = SWINGS.computeIfAbsent(p.getId(), id -> new float[]{0.0F, 0.0F});
            boolean swinging = p.isSwinging() && p.getCurrentSwing() != null && p.getCurrentSwing().hand() == InteractionHand.MAIN_HAND;
            float anim = p.getSwingAnimation(1.0F);
            if (swinging && (last[0] == 0.0F || anim < last[1] - 0.01F)) {
                of(p.getId()).crack(now);
            }
            last[0] = swinging ? 1.0F : 0.0F;
            last[1] = anim;
            Lash lash = LASHES.get(p.getId());
            if (lash != null && now - lash.crackStart == (long) Math.floor(Lash.CRACK)) {
                // where the tip is about to go off: out along the look
                Vec3 at = p.getEyePosition().add(p.getViewVector(1.0F).scale(Lash.LENGTH + 0.4)).add(0.0, -0.2, 0.0);
                level.playLocalSound(at.x, at.y, at.z, ModSounds.CRACK, SoundSource.PLAYERS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.25F,
                        false);
            }
        }
        for (Iterator<Map.Entry<Integer, Lash>> it = LASHES.entrySet().iterator(); it.hasNext(); ) {
            Lash lash = it.next().getValue();
            if (now - lash.lastSeen > 200L && now - lash.crackStart > 200L) {
                it.remove();
            }
        }
    }

    /** The tip's burst: called once per crack, where the drawn tip is. */
    static void burst(ClientLevel level, Lash lash) {
        double x = lash.nx(Lash.NODES - 1);
        double y = lash.ny(Lash.NODES - 1);
        double z = lash.nz(Lash.NODES - 1);
        for (int i = 0; i < 8; i++) {
            double a = i / 8.0 * Math.PI * 2.0;
            level.addParticle(ModParticles.CRACK, x, y, z, Math.cos(a) * 0.08, (level.getRandom().nextFloat() - 0.3) * 0.06, Math.sin(a) * 0.08);
        }
        level.addParticle(ParticleTypes.CRIT, x, y, z, 0.0, 0.05, 0.0);
    }

    /** After the lash moved this frame: the tip's burst, once, when the crack goes off. */
    static void afterUpdate(ClientLevel level, Lash lash, double time) {
        if (lash.crackAge(time) >= Lash.CRACK && lash.crackAge(time) < Lash.FREE + 2.0 && lash.crackDone != lash.crackStart) {
            lash.crackDone = lash.crackStart;
            burst(level, lash);
        }
    }

    /**
     * Draws the lash as a tube: through {@code toSubmit} (world point minus {@code base}, then that
     * matrix) so the same lash can be drawn in the world pass and in the first-person hand pass.
     */
    public static void submit(SubmitNodeCollector collector, Lash lash, Matrix4f toSubmit, double baseX, double baseY, double baseZ, int light) {
        int n = (Lash.NODES - 1) * SUB + 1;
        float[] px = new float[n];
        float[] py = new float[n];
        float[] pz = new float[n];
        for (int i = 0; i < Lash.NODES - 1; i++) {
            int i0 = Math.max(0, i - 1);
            int i3 = Math.min(Lash.NODES - 1, i + 2);
            for (int s = 0; s < SUB; s++) {
                float t = (float) s / SUB;
                int k = i * SUB + s;
                px[k] = (float) (cr(t, lash.nx(i0), lash.nx(i), lash.nx(i + 1), lash.nx(i3)) - baseX);
                py[k] = (float) (cr(t, lash.ny(i0), lash.ny(i), lash.ny(i + 1), lash.ny(i3)) - baseY);
                pz[k] = (float) (cr(t, lash.nz(i0), lash.nz(i), lash.nz(i + 1), lash.nz(i3)) - baseZ);
            }
        }
        px[n - 1] = (float) (lash.nx(Lash.NODES - 1) - baseX);
        py[n - 1] = (float) (lash.ny(Lash.NODES - 1) - baseY);
        pz[n - 1] = (float) (lash.nz(Lash.NODES - 1) - baseZ);

        PoseStack ps = new PoseStack();
        ps.mulPose(toSubmit);
        collector.submitCustomGeometry(ps, TYPE, (pose, buffer) -> tube(pose, buffer, px, py, pz, light));
    }

    private static double cr(double t, double p0, double p1, double p2, double p3) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * (2.0 * p1 + (-p0 + p2) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    /** A braided tube along the points, thinner towards the tip; a frame carried along so it never twists. */
    private static void tube(PoseStack.Pose pose, VertexConsumer buffer, float[] px, float[] py, float[] pz, int light) {
        int n = px.length;
        Vector3f tangent = new Vector3f();
        Vector3f normal = new Vector3f();
        Vector3f binormal = new Vector3f();
        Vector3f[] ringN = new Vector3f[n];
        Vector3f[] ringB = new Vector3f[n];
        float[] radius = new float[n];
        float[] vAlong = new float[n];
        float along = 0.0F;
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1);
            int b = Math.min(n - 1, i + 1);
            tangent.set(px[b] - px[a], py[b] - py[a], pz[b] - pz[a]);
            if (tangent.lengthSquared() < 1.0E-10F) {
                tangent.set(0.0F, -1.0F, 0.0F);
            }
            tangent.normalize();
            if (i == 0) {
                normal.set(Math.abs(tangent.y) < 0.9F ? 0.0F : 1.0F, Math.abs(tangent.y) < 0.9F ? 1.0F : 0.0F, 0.0F);
            }
            // parallel transport: keep the last normal, minus its part along the new tangent
            normal.sub(new Vector3f(tangent).mul(normal.dot(tangent)));
            if (normal.lengthSquared() < 1.0E-8F) {
                normal.set(1.0F, 0.0F, 0.0F).sub(new Vector3f(tangent).mul(tangent.x));
            }
            normal.normalize();
            tangent.cross(normal, binormal).normalize();
            ringN[i] = new Vector3f(normal);
            ringB[i] = new Vector3f(binormal);
            float t = (float) i / (n - 1);
            radius[i] = Mth.lerp((float) Math.pow(t, 0.75), ROOT_RADIUS, TIP_RADIUS);
            if (i > 0) {
                along += (float) Math.sqrt(sq(px[i] - px[i - 1]) + sq(py[i] - py[i - 1]) + sq(pz[i] - pz[i - 1]));
            }
            vAlong[i] = along;
        }
        for (int i = 0; i + 1 < n; i++) {
            // the texture: around the tube, the braid repeating every 0.4 blocks; the last bit is the pale cracker
            float v0 = vAlong[i] / 0.4F;
            v0 -= (float) Math.floor(v0);
            float v1 = Math.min(1.0F, v0 + (vAlong[i + 1] - vAlong[i]) / 0.4F);
            int color = i + 1 >= n - 2 * SUB ? 0xFFEFE3C8 : -1;
            for (int s = 0; s < SIDES; s++) {
                float a0 = (float) s / SIDES * Mth.TWO_PI;
                float a1 = (float) (s + 1) / SIDES * Mth.TWO_PI;
                float u0 = (float) s / SIDES;
                float u1 = (float) (s + 1) / SIDES;
                // counter-clockwise seen from outside
                vertex(pose, buffer, px, py, pz, ringN, ringB, radius, i, a0, u0, v0, light, color);
                vertex(pose, buffer, px, py, pz, ringN, ringB, radius, i, a1, u1, v0, light, color);
                vertex(pose, buffer, px, py, pz, ringN, ringB, radius, i + 1, a1, u1, v1, light, color);
                vertex(pose, buffer, px, py, pz, ringN, ringB, radius, i + 1, a0, u0, v1, light, color);
            }
        }
    }

    private static float sq(float v) {
        return v * v;
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float[] px, float[] py, float[] pz, Vector3f[] ringN, Vector3f[] ringB,
                               float[] radius, int i, float angle, float u, float v, int light, int color) {
        float c = Mth.cos(angle);
        float s = Mth.sin(angle);
        float nx = ringN[i].x * c + ringB[i].x * s;
        float ny = ringN[i].y * c + ringB[i].y * s;
        float nz = ringN[i].z * c + ringB[i].z * s;
        float r = radius[i];
        buffer.addVertex(pose, px[i] + nx * r, py[i] + ny * r, pz[i] + nz * r).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
    }
}
