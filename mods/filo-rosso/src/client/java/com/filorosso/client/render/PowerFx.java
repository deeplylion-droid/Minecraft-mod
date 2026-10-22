package com.filorosso.client.render;

import com.filorosso.FiloRosso;
import com.filorosso.data.BondLevel;
import com.filorosso.data.Link;
import com.filorosso.data.ModData;
import com.filorosso.data.Palette;
import com.filorosso.data.Powers;
import com.filorosso.net.Net;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * What the powers of levels 6-10 look like: the dome of woven threads, the lasso and its coils, the
 * wings of "Eterni", the wave of the kiss. Everyone sees them (the server tells every client).
 */
public final class PowerFx {
    private PowerFx() {
    }

    /** A soft glowing line, white: tinted with the colour of each piece. */
    private static final Identifier GLOW = FiloRosso.id("textures/entity/glow_line.png");
    private static final RenderType GLOW_TYPE = RenderTypes.eyes(GLOW);
    private static final Identifier THREAD = FiloRosso.id("textures/entity/thread.png");

    private static final Map<Integer, Net.Dome> DOMES = new HashMap<>();
    private static final Map<Integer, Net.Tied> TIED = new HashMap<>();

    /** A short animation started by the server (kind from {@link Net.ThreadFx}). */
    private record Wave(int kind, int a, int b, long start, Vec3 at) {
    }

    private static final List<Wave> WAVES = new ArrayList<>();
    /** A pink (or golden) flash over the whole screen, 0-1. */
    public static float flash;
    public static int flashColor = 0xFF80B0;

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(PowerFx::submit);
    }

    public static void clear() {
        DOMES.clear();
        TIED.clear();
        WAVES.clear();
        flash = 0.0F;
    }

    public static void onDome(Net.Dome dome) {
        DOMES.put(dome.id(), dome);
    }

    public static void onTied(Net.Tied tied) {
        if (tied.until() == 0L) {
            TIED.remove(tied.target());
        } else {
            TIED.put(tied.target(), tied);
        }
    }

    /** From ThreadRenderer.onFx: the waves of the powers. */
    public static void onFx(Net.ThreadFx fx) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Entity a = level.getEntity(fx.a());
        Entity b = level.getEntity(fx.b());
        if (a == null || b == null) {
            return;
        }
        Vec3 mid = a.position().add(b.position()).scale(0.5);
        WAVES.add(new Wave(fx.kind(), fx.a(), fx.b(), level.getGameTime(), mid));
        Player me = mc.player;
        if (me == null) {
            return;
        }
        if (fx.kind() == Net.ThreadFx.KISS && me.position().distanceTo(mid) < 14.0) {
            flash = Math.max(flash, me.getId() == fx.a() || me.getId() == fx.b() ? 0.75F : 0.45F);
            flashColor = 0xFF70A8;
        } else if (fx.kind() == Net.ThreadFx.ETERNAL && (me.getId() == fx.a() || me.getId() == fx.b())) {
            flash = Math.max(flash, 0.6F);
            flashColor = 0xFFD060;
        }
    }

    public static void tick(Minecraft mc) {
        flash = Math.max(0.0F, flash - 0.05F);
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        DOMES.values().removeIf(d -> now > d.end() + 20);
        TIED.values().removeIf(t -> now > t.until() + 2);
        WAVES.removeIf(w -> now - w.start() > 40);
        // the dome glitters where monsters bump into it
        for (Net.Dome d : DOMES.values()) {
            if (now < d.end() && level.getRandom().nextInt(3) == 0) {
                double th = level.getRandom().nextDouble() * Math.PI * 2.0;
                double ph = level.getRandom().nextDouble() * Math.PI * 0.5;
                double r = d.radius();
                level.addParticle(ParticleTypes.END_ROD, d.x() + Math.cos(th) * Math.cos(ph) * r, d.y() + Math.sin(ph) * r,
                        d.z() + Math.sin(th) * Math.cos(ph) * r, 0.0, 0.0, 0.0);
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    private static void submit(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long now = level.getGameTime();
        float time = now + partial;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack poseStack = context.poseStack();

        for (Net.Dome d : DOMES.values()) {
            dome(context, poseStack, camera, d, time);
        }
        Iterator<Net.Tied> it = TIED.values().iterator();
        while (it.hasNext()) {
            Net.Tied t = it.next();
            Entity target = level.getEntity(t.target());
            Entity by = level.getEntity(t.by());
            if (target == null || by == null) {
                continue;
            }
            lasso(context, poseStack, level, camera, by, target, time, partial, 1.0F);
        }
        for (Wave w : WAVES) {
            float age = time - w.start();
            switch (w.kind()) {
                case Net.ThreadFx.LASSO -> {
                    Entity by = level.getEntity(w.a());
                    Entity target = level.getEntity(w.b());
                    if (by != null && target != null && age < 6.0F && !TIED.containsKey(w.b())) {
                        lasso(context, poseStack, level, camera, by, target, time, partial, age / 6.0F);
                    }
                }
                case Net.ThreadFx.KISS -> ring(context, poseStack, camera, w.at(), age / 16.0F, 12.0F, 0xFF70A8, 0.32F);
                case Net.ThreadFx.ETERNAL -> ring(context, poseStack, camera, w.at(), age / 14.0F, 7.0F, 0xFFD060, 0.25F);
                case Net.ThreadFx.SLING -> ring(context, poseStack, camera, w.at(), age / 8.0F, 3.0F, 0xFF9050, 0.15F);
                default -> {
                }
            }
        }
        for (Player p : level.players()) {
            Powers powers = ModData.powers(p);
            if (powers.eternal(now) && !p.isInvisible() && !p.isSpectator()) {
                boolean firstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
                if (!firstPerson) {
                    float left = (powers.eternalUntil() - time) / 20.0F;
                    wings(context, poseStack, camera, p, time, partial, Mth.clamp(left, 0.0F, 1.0F));
                }
            }
        }
    }

    /** A ribbon through these points that always faces the camera. */
    private static void ribbon(LevelRenderContext context, PoseStack poseStack, RenderType type, Vec3 camera, List<Vec3> points, float[] widths,
                               int[] colors, int light, float v0, float v1) {
        int n = points.size() - 1;
        if (n < 1) {
            return;
        }
        context.submitNodeCollector().submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            Vec3[] sides = new Vec3[n + 1];
            for (int i = 0; i <= n; i++) {
                Vec3 p = points.get(i);
                Vec3 tangent = points.get(Math.min(n, i + 1)).subtract(points.get(Math.max(0, i - 1)));
                Vec3 s = tangent.cross(camera.subtract(p));
                if (s.lengthSqr() < 1.0E-8) {
                    s = tangent.cross(new Vec3(0.0, 1.0, 0.0));
                }
                if (s.lengthSqr() < 1.0E-8) {
                    s = new Vec3(1.0, 0.0, 0.0);
                }
                sides[i] = s.normalize();
            }
            for (int i = 0; i < n; i++) {
                Vec3 p0 = points.get(i);
                Vec3 p1 = points.get(i + 1);
                Vec3 s0 = sides[i].scale(widths[i] * 0.5);
                Vec3 s1 = sides[i + 1].scale(widths[i + 1] * 0.5);
                vertex(buffer, pose, p0.subtract(s0).subtract(camera), 0.0F, v0, colors[i], light);
                vertex(buffer, pose, p0.add(s0).subtract(camera), 1.0F, v0, colors[i], light);
                vertex(buffer, pose, p1.add(s1).subtract(camera), 1.0F, v1, colors[i + 1], light);
                vertex(buffer, pose, p1.subtract(s1).subtract(camera), 0.0F, v1, colors[i + 1], light);
            }
        });
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, float u, float v, int color, int light) {
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void glowLine(LevelRenderContext context, PoseStack poseStack, Vec3 camera, List<Vec3> points, float width, int rgb, float alpha) {
        float[] w = new float[points.size()];
        int[] c = new int[points.size()];
        int color = ARGB.color(Mth.clamp(alpha, 0.0F, 1.0F), rgb);
        // the eyes render type adds light: a dimmer colour is a fainter line
        color = ARGB.scaleRGB(color, Mth.clamp(alpha, 0.0F, 1.0F));
        for (int i = 0; i < w.length; i++) {
            w[i] = width;
            c[i] = color;
        }
        ribbon(context, poseStack, GLOW_TYPE, camera, points, w, c, LightCoordsUtil.FULL_BRIGHT, 0.0F, 1.0F);
    }

    /** The dome: meridians and parallels of thread that weave up from the ground, then fade. */
    private static void dome(LevelRenderContext context, PoseStack poseStack, Vec3 camera, Net.Dome d, float time) {
        float age = time - d.start();
        float left = d.end() - time;
        float grow = Mth.clamp(age / 12.0F, 0.0F, 1.0F);
        float fade = Mth.clamp(left / 20.0F, 0.0F, 1.0F);
        if (fade <= 0.0F) {
            return;
        }
        Vec3 c = new Vec3(d.x(), d.y(), d.z());
        if (c.distanceToSqr(camera) > 128 * 128) {
            return;
        }
        double r = d.radius();
        int rgb = d.color() & 0xFFFFFF;
        int meridians = 14;
        int steps = 12;
        float pulse = 0.75F + 0.25F * Mth.sin(time * 0.3F);
        for (int m = 0; m < meridians; m++) {
            double th = m * Math.PI * 2.0 / meridians + time * 0.004;
            List<Vec3> pts = new ArrayList<>();
            for (int i = 0; i <= steps; i++) {
                double k = i / (double) steps;
                if (k > grow) {
                    break;
                }
                double ph = k * Math.PI * 0.5;
                pts.add(c.add(Math.cos(th) * Math.cos(ph) * r, Math.sin(ph) * r, Math.sin(th) * Math.cos(ph) * r));
            }
            if (pts.size() > 1) {
                glowLine(context, poseStack, camera, pts, 0.09F, rgb, 0.75F * fade * pulse);
            }
        }
        for (int p = 1; p <= 4; p++) {
            double ph = p * Math.PI * 0.5 / 5.0;
            if (ph / (Math.PI * 0.5) > grow) {
                continue;
            }
            List<Vec3> pts = new ArrayList<>();
            int seg = 36;
            for (int i = 0; i <= seg; i++) {
                double th = i * Math.PI * 2.0 / seg - time * 0.006 * (p % 2 == 0 ? 1 : -1);
                pts.add(c.add(Math.cos(th) * Math.cos(ph) * r, Math.sin(ph) * r, Math.sin(th) * Math.cos(ph) * r));
            }
            glowLine(context, poseStack, camera, pts, 0.07F, rgb, 0.6F * fade * pulse);
        }
        // a bright ring running up the dome over and over
        double run = ((time * 0.04) % 1.0) * Math.PI * 0.5;
        List<Vec3> band = new ArrayList<>();
        for (int i = 0; i <= 40; i++) {
            double th = i * Math.PI * 2.0 / 40;
            band.add(c.add(Math.cos(th) * Math.cos(run) * r, Math.sin(run) * r, Math.sin(th) * Math.cos(run) * r));
        }
        glowLine(context, poseStack, camera, band, 0.16F, 0xFFFFFF, 0.5F * fade * grow);
        // and its foot on the ground
        List<Vec3> foot = new ArrayList<>();
        for (int i = 0; i <= 48; i++) {
            double th = i * Math.PI * 2.0 / 48;
            foot.add(c.add(Math.cos(th) * r, 0.05, Math.sin(th) * r));
        }
        glowLine(context, poseStack, camera, foot, 0.22F, rgb, 0.9F * fade * grow);
    }

    /** The lasso: a rope from the hand that threw it to the coils round whatever it caught. */
    private static void lasso(LevelRenderContext context, PoseStack poseStack, ClientLevel level, Vec3 camera, Entity by, Entity target, float time,
                              float partial, float extend) {
        Vec3 from = by.getRopeHoldPosition(partial);
        double h = target.getBbHeight();
        Vec3 base = target.getPosition(partial);
        Vec3 to = base.add(0.0, h * 0.55, 0.0);
        if (from.distanceToSqr(camera) > 128 * 128 && to.distanceToSqr(camera) > 128 * 128) {
            return;
        }
        int tile = by instanceof Player p ? ModData.link(p).level().ordinal() : BondLevel.CRYSTAL.ordinal();
        int rgb = by instanceof Player p ? Palette.rgb(ModData.link(p).color()) : 0xD8202A;
        double d = from.distanceTo(to);
        int seg = Mth.clamp((int) (d / 0.3), 8, 60);
        List<Vec3> pts = new ArrayList<>();
        for (int i = 0; i <= seg; i++) {
            double t = i / (double) seg;
            if (t > extend) {
                break;
            }
            double sag = Math.min(1.2, d * 0.06) * 4.0 * t * (1.0 - t) * (extend < 1.0F ? 0.3 : 1.0);
            Vec3 p = from.lerp(to, t).add(0.0, -sag + 0.04 * Math.sin(time * 0.6 + t * 12.0), 0.0);
            pts.add(p);
        }
        float[] w = new float[pts.size()];
        int[] c = new int[pts.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = 0.07F;
            c[i] = 0xFFFFFFFF;
        }
        float v0 = tile / 10.0F;
        float v1 = (tile + 1) / 10.0F;
        ribbon(context, poseStack, RenderTypes.entityCutout(THREAD), camera, pts, w, c, LightCoordsUtil.FULL_BRIGHT, v0, v1);
        if (extend < 1.0F) {
            // the loop at the end of the rope as it flies
            Vec3 tip = pts.get(pts.size() - 1);
            List<Vec3> loop = new ArrayList<>();
            for (int i = 0; i <= 16; i++) {
                double a = i * Math.PI * 2.0 / 16 + time * 0.8;
                loop.add(tip.add(Math.cos(a) * 0.45, 0.25 + Math.sin(a) * 0.12, Math.sin(a) * 0.45));
            }
            glowLine(context, poseStack, camera, loop, 0.08F, rgb, 0.9F);
            return;
        }
        // coils round the body, tight and glowing a little
        double radius = target.getBbWidth() * 0.55 + 0.08;
        for (int k = 0; k < 3; k++) {
            double y = base.y + h * (0.35 + 0.15 * k);
            List<Vec3> ring = new ArrayList<>();
            for (int i = 0; i <= 20; i++) {
                double a = i * Math.PI * 2.0 / 20;
                ring.add(new Vec3(base.x + Math.cos(a) * radius, y + Math.sin(a + k) * 0.06, base.z + Math.sin(a) * radius));
            }
            float[] rw = new float[ring.size()];
            int[] rc = new int[ring.size()];
            for (int i = 0; i < rw.length; i++) {
                rw[i] = 0.08F;
                rc[i] = 0xFFFFFFFF;
            }
            ribbon(context, poseStack, RenderTypes.entityCutout(THREAD), camera, ring, rw, rc, LightCoordsUtil.FULL_BRIGHT, v0, v1);
            glowLine(context, poseStack, camera, ring, 0.14F, rgb, 0.35F + 0.15F * Mth.sin(time * 0.4F + k));
        }
    }

    /** A wave on the ground: the kiss, Eterni, the slingshot's take-off. */
    private static void ring(LevelRenderContext context, PoseStack poseStack, Vec3 camera, Vec3 at, float k, float radius, int rgb, float width) {
        if (k >= 1.0F) {
            return;
        }
        float e = 1.0F - (1.0F - k) * (1.0F - k);
        double r = Math.max(0.2, radius * e);
        for (int layer = 0; layer < 2; layer++) {
            List<Vec3> pts = new ArrayList<>();
            int seg = 64;
            for (int i = 0; i <= seg; i++) {
                double a = i * Math.PI * 2.0 / seg;
                double rr = r * (layer == 0 ? 1.0 : 0.82);
                pts.add(at.add(Math.cos(a) * rr, 0.15 + layer * 0.5 * (1.0 - k), Math.sin(a) * rr));
            }
            glowLine(context, poseStack, camera, pts, width * (layer == 0 ? 2.0F : 1.0F) * (1.0F - k * 0.5F), rgb, (1.0F - k) * (layer == 0 ? 1.0F : 0.6F));
        }
    }

    /** "Eterni": two wings of glowing thread on the back, beating slowly. */
    private static void wings(LevelRenderContext context, PoseStack poseStack, Vec3 camera, Player p, float time, float partial, float fade) {
        Vec3 pos = p.getPosition(partial);
        if (pos.distanceToSqr(camera) > 96 * 96) {
            return;
        }
        float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        double height = p.isCrouching() ? 1.05 : 1.3;
        boolean flying = p.getAbilities().flying || !p.onGround();
        float beat = Mth.sin(time * (flying ? 0.32F : 0.12F) + p.getId());
        float lift = (flying ? 0.45F : 0.15F) * beat;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 root = pos.add(up.scale(height)).add(fwd.scale(-0.2)).add(right.scale(side * 0.12));
            for (int k = 0; k < 6; k++) {
                double span = 0.9 + 0.22 * k;
                double drop = -0.15 + 0.32 * k;
                List<Vec3> pts = new ArrayList<>();
                int seg = 8;
                for (int i = 0; i <= seg; i++) {
                    double t = i / (double) seg;
                    double out = span * t;
                    double y = (0.55 - drop) * t + 0.35 * Math.sin(t * Math.PI) * (1.0 - k * 0.12) + lift * t * t * (1.0 + k * 0.1);
                    double back = 0.35 * t + 0.1 * k * t;
                    pts.add(root.add(right.scale(side * out)).add(up.scale(y)).add(fwd.scale(-back)));
                }
                int rgb = ARGB.srgbLerp(k / 5.0F, 0xFFFF3050, 0xFFFFD070) & 0xFFFFFF;
                float[] w = new float[pts.size()];
                int[] c = new int[pts.size()];
                for (int i = 0; i < w.length; i++) {
                    double t = i / (double) (w.length - 1);
                    w[i] = (float) (0.13 * (1.0 - t * 0.7));
                    float alpha = fade * (0.85F - 0.3F * (float) t);
                    c[i] = ARGB.scaleRGB(ARGB.color(1.0F, rgb), alpha);
                }
                ribbon(context, poseStack, GLOW_TYPE, camera, pts, w, c, LightCoordsUtil.FULL_BRIGHT, 0.0F, 1.0F);
            }
        }
    }

    /** Is this player's thread burning right now ("Eterni")? */
    public static boolean blazing(Player p, long now) {
        return ModData.powers(p).eternal(now);
    }

    /** The colour of a couple, for the HUD. */
    public static int coupleColor(Link link) {
        return 0xFF000000 | Palette.rgb(link.color());
    }

    /** Lassoed by someone right now (for the HUD of whoever is caught). */
    public static boolean tied(LivingEntity e) {
        return TIED.containsKey(e.getId());
    }
}
