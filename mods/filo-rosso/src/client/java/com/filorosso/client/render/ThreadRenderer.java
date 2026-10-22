package com.filorosso.client.render;

import com.filorosso.FiloRosso;
import com.filorosso.client.ClientState;
import com.filorosso.client.ThreadLinks;
import com.filorosso.data.BondLevel;
import com.filorosso.data.Link;
import com.filorosso.data.ModData;
import com.filorosso.net.Net;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Draws the red threads between the little fingers of the partners: a ribbon that always faces
 * the camera, hanging when there is slack, straight when it is pulled, lying on the ground when the
 * two are close. Its material changes with the bond level (texture: one 16x16 tile per level).
 */
public final class ThreadRenderer {
    private ThreadRenderer() {
    }

    private static final Identifier TEXTURE = FiloRosso.id("textures/entity/thread.png");
    private static final float[] WIDTH = {0.04F, 0.075F, 0.08F, 0.085F, 0.08F, 0.085F, 0.09F, 0.09F, 0.095F, 0.1F};
    /** Tiles in thread.png, one 16x16 per level. */
    private static final int TILES = 10;
    private static final int GROW_TICKS = 30;

    /** A short animation on a thread (by entity ids, so it outlives the link: snapping). */
    private record Effect(int kind, int a, int b, long start, Vec3 lastA, Vec3 lastB, float lastLength) {
    }

    private static final List<Effect> EFFECTS = new ArrayList<>();
    /** Recent flashes per pair of entity ids (level up, pull, combo). */
    private static final Map<Long, Effect> PULSES = new HashMap<>();

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(ThreadRenderer::submit);
    }

    public static void clear() {
        EFFECTS.clear();
        PULSES.clear();
    }

    private static long pairKey(int a, int b) {
        return a < b ? ((long) a << 32) | (b & 0xFFFFFFFFL) : ((long) b << 32) | (a & 0xFFFFFFFFL);
    }

    /** The server tells us something happened to a thread. */
    public static void onFx(Net.ThreadFx fx) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        Entity a = level.getEntity(fx.a());
        Entity b = level.getEntity(fx.b());
        switch (fx.kind()) {
            case Net.ThreadFx.SNAP, Net.ThreadFx.CUT -> {
                if (a != null && b != null) {
                    float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                    Vec3 pa = a.getRopeHoldPosition(partial);
                    Vec3 pb = b.getRopeHoldPosition(partial);
                    EFFECTS.add(new Effect(fx.kind(), fx.a(), fx.b(), now, pa, pb, (float) pa.distanceTo(pb)));
                    Vec3 mid = pa.add(pb).scale(0.5);
                    for (int i = 0; i < 12; i++) {
                        level.addParticle(fx.kind() == Net.ThreadFx.SNAP ? net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR
                                        : net.minecraft.core.particles.ParticleTypes.CRIT, mid.x, mid.y, mid.z,
                                (level.getRandom().nextDouble() - 0.5) * 0.4, level.getRandom().nextDouble() * 0.3, (level.getRandom().nextDouble() - 0.5) * 0.4);
                    }
                }
            }
            case Net.ThreadFx.COMBO -> {
                // b is the target: the thread of a's couple flashes
                if (a instanceof Player p) {
                    Link link = ModData.link(p);
                    if (link.partner().isPresent()) {
                        Player q = level.getPlayerByUUID(link.partner().get());
                        if (q != null) {
                            PULSES.put(pairKey(p.getId(), q.getId()), new Effect(fx.kind(), p.getId(), q.getId(), now, null, null, 0.0F));
                        }
                    }
                }
            }
            case Net.ThreadFx.LASSO -> PowerFx.onFx(fx);
            case Net.ThreadFx.KISS, Net.ThreadFx.ETERNAL -> {
                PowerFx.onFx(fx);
                PULSES.put(pairKey(fx.a(), fx.b()), new Effect(Net.ThreadFx.LEVEL_UP, fx.a(), fx.b(), now, null, null, 0.0F));
            }
            case Net.ThreadFx.SLING -> {
                PowerFx.onFx(fx);
                PULSES.put(pairKey(fx.a(), fx.b()), new Effect(Net.ThreadFx.PULL, fx.a(), fx.b(), now, null, null, 0.0F));
            }
            default -> PULSES.put(pairKey(fx.a(), fx.b()), new Effect(fx.kind(), fx.a(), fx.b(), now, null, null, 0.0F));
        }
    }

    private static void submit(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;
        long now = level.getGameTime();
        float time = now + partial;
        PoseStack poseStack = context.poseStack();
        float length = ClientState.length();

        for (ThreadLinks.Tie tie : ThreadLinks.all(level)) {
            Vec3 a = tie.a().getRopeHoldPosition(partial);
            Vec3 b = tie.b().getRopeHoldPosition(partial);
            if (a.distanceToSqr(camera) > 160 * 160 && b.distanceToSqr(camera) > 160 * 160) {
                continue;
            }
            float grow = 1.0F;
            if (!tie.extra() && tie.bondedAt() > 0 && now - tie.bondedAt() < GROW_TICKS && now >= tie.bondedAt()) {
                grow = Mth.clamp((time - tie.bondedAt()) / GROW_TICKS, 0.0F, 1.0F);
            }
            Effect pulse = PULSES.get(pairKey(tie.a().getId(), tie.b().getId()));
            float pulseT = -1.0F;
            int pulseKind = -1;
            boolean reversed = false;
            if (pulse != null) {
                float age = time - pulse.start();
                int duration = pulse.kind() == Net.ThreadFx.LEVEL_UP ? 40 : 20;
                if (age > duration) {
                    PULSES.remove(pairKey(tie.a().getId(), tie.b().getId()));
                } else {
                    pulseT = age / duration;
                    pulseKind = pulse.kind();
                    reversed = pulse.a() == tie.b().getId();
                }
            }
            int tile = tie.level();
            int color = tie.extra() ? 0xFFFFD870 : 0xFFFFFFFF;
            // "Eterni": the thread burns
            boolean blazing = !tie.extra() && (PowerFx.blazing(tie.a(), now) || PowerFx.blazing(tie.b(), now));
            List<Vec3> points = curve(level, a, b, length, time, tie.a().getId() * 31 + tie.b().getId(), pulseKind == Net.ThreadFx.PULL || blazing);
            drawRibbon(context, poseStack, level, camera, points, tile, color, grow, pulseT, pulseKind, reversed, time, blazing);
        }

        // threads that snapped or were cut: the halves fly back to their owners
        Iterator<Effect> it = EFFECTS.iterator();
        while (it.hasNext()) {
            Effect e = it.next();
            float age = time - e.start();
            if (age > 20.0F) {
                it.remove();
                continue;
            }
            Entity ea = level.getEntity(e.a());
            Entity eb = level.getEntity(e.b());
            Vec3 a = ea != null ? ea.getRopeHoldPosition(partial) : e.lastA();
            Vec3 b = eb != null ? eb.getRopeHoldPosition(partial) : e.lastB();
            float k = age / 20.0F;
            float half = e.lastLength() * 0.5F * (1.0F - k);
            int tile = e.kind() == Net.ThreadFx.SNAP ? 0 : levelOf(ea);
            drawStub(context, poseStack, level, camera, a, b, half, k, tile, time);
            drawStub(context, poseStack, level, camera, b, a, half, k, tile, time);
        }

        // cut threads: two short ends hang from the fingers until they tie up again
        for (Player p : level.players()) {
            Link link = ModData.link(p);
            if (!link.bound() || !link.cut(now) || p.isSpectator()) {
                continue;
            }
            Vec3 a = p.getRopeHoldPosition(partial);
            if (a.distanceToSqr(camera) > 96 * 96) {
                continue;
            }
            Player q = level.getPlayerByUUID(link.partner().get());
            Vec3 toward = q != null ? q.getRopeHoldPosition(partial) : a.add(p.getLookAngle());
            drawDangling(context, poseStack, level, camera, a, toward, link.level().ordinal(), time + p.getId() * 7.0F);
        }
    }

    private static int levelOf(Entity e) {
        return e instanceof Player p ? ModData.link(p).level().ordinal() : 0;
    }

    /**
     * Points along the thread. With slack it hangs (the depth of a rope of this length between
     * these two points), and rests on blocks instead of going through them.
     */
    private static List<Vec3> curve(ClientLevel level, Vec3 a, Vec3 b, float length, float time, int seed, boolean straight) {
        double d = a.distanceTo(b);
        double slack = Math.max(0.0, length - d);
        double sag = straight ? 0.0 : Math.max(Math.sqrt(3.0 * d * slack / 8.0), slack * 0.5 * Mth.clamp(1.0 - d / 2.0, 0.0, 1.0));
        sag = Math.min(sag, length * 0.5);
        int segments = Mth.clamp((int) Math.ceil((d + sag) / 0.25), 10, 48);
        Vec3 dir = b.subtract(a);
        Vec3 horizontal = new Vec3(dir.x, 0.0, dir.z);
        Vec3 side = horizontal.lengthSqr() > 1.0E-4 ? new Vec3(-horizontal.z, 0.0, horizontal.x).normalize() : new Vec3(1.0, 0.0, 0.0);
        // a little breeze when it is loose, a quiver when it is pulled tight
        double wind = slack > 0.3 ? 0.06 * Math.min(1.0, slack) : 0.0;
        double quiver = d > length - 0.2 && !straight ? 0.012 : 0.0;
        List<Vec3> points = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double bell = 4.0 * t * (1.0 - t);
            Vec3 p = a.add(dir.scale(t)).add(0.0, -sag * bell, 0.0);
            if (wind > 0.0) {
                p = p.add(side.scale(wind * bell * Math.sin(time * 0.05 + seed + t * 3.0)));
            }
            if (quiver > 0.0) {
                p = p.add(0.0, quiver * bell * Math.sin(time * 1.7 + t * 9.0), 0.0);
            }
            if (sag > 0.05 && i > 0 && i < segments) {
                p = rest(level, p);
            }
            points.add(p);
        }
        return points;
    }

    /** Lifts a point that ended up inside a block onto its top. */
    private static Vec3 rest(ClientLevel level, Vec3 p) {
        BlockPos pos = BlockPos.containing(p);
        for (int i = 0; i < 3; i++) {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                return p;
            }
            double top = pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) + 0.03;
            if (p.y >= top) {
                return p;
            }
            p = new Vec3(p.x, top, p.z);
            pos = pos.above();
        }
        return p;
    }

    private static int light(ClientLevel level, Vec3 p, int tile) {
        if (tile >= BondLevel.STARS.ordinal()) {
            return LightCoordsUtil.FULL_BRIGHT;
        }
        return LightCoordsUtil.getLightCoords(level, BlockPos.containing(p));
    }

    private static void drawRibbon(LevelRenderContext context, PoseStack poseStack, ClientLevel level, Vec3 camera, List<Vec3> points, int tile,
                                   int color, float grow, float pulseT, int pulseKind, boolean reversed, float time) {
        drawRibbon(context, poseStack, level, camera, points, tile, color, grow, pulseT, pulseKind, reversed, time, false);
    }

    private static void drawRibbon(LevelRenderContext context, PoseStack poseStack, ClientLevel level, Vec3 camera, List<Vec3> points, int tile,
                                   int color, float grow, float pulseT, int pulseKind, boolean reversed, float time, boolean blazing) {
        int n = points.size() - 1;
        tile = Mth.clamp(tile, 0, TILES - 1);
        float v0 = tile / (float) TILES;
        float v1 = (tile + 1) / (float) TILES;
        float baseWidth = WIDTH[Mth.clamp(tile, 0, WIDTH.length - 1)];
        float[] widths = new float[n + 1];
        int[] colors = new int[n + 1];
        int[] lights = new int[n + 1];
        boolean[] visible = new boolean[n + 1];
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            visible[i] = grow >= 1.0F || t <= grow * 0.5F || t >= 1.0F - grow * 0.5F;
            float w = baseWidth;
            int c = color;
            if (pulseT >= 0.0F) {
                float along = reversed ? 1.0F - t : t;
                float wave = switch (pulseKind) {
                    case Net.ThreadFx.LEVEL_UP -> {
                        // a bright knot runs to the partner and back
                        float pos = pulseT < 0.5F ? pulseT * 2.0F : 2.0F - pulseT * 2.0F;
                        yield (float) Math.exp(-Math.pow((along - pos) / 0.08, 2.0));
                    }
                    case Net.ThreadFx.PULL -> (float) Math.exp(-Math.pow((along - pulseT * 1.3F) / 0.1, 2.0));
                    default -> 1.0F - pulseT;
                };
                w *= 1.0F + 1.4F * wave;
                c = ARGB.srgbLerp(Math.min(1.0F, wave), c, 0xFFFFF0F8);
            }
            c = ARGB.multiply(c, levelTint(tile, t, i, time));
            if (blazing) {
                // a thread on fire: wider, white-gold, flickering
                float fl = 0.5F + 0.5F * Mth.sin(time * 1.3F + i * 2.1F) * Mth.sin(time * 0.7F + i * 0.6F);
                w *= 1.7F + 0.4F * fl;
                c = ARGB.srgbLerp(0.35F + 0.35F * fl, c, 0xFFFFE8A0);
            }
            widths[i] = w;
            colors[i] = c;
            lights[i] = light(level, points.get(i), tile);
        }
        poseStack.pushPose();
        context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> {
            Vec3[] sides = new Vec3[n + 1];
            for (int i = 0; i <= n; i++) {
                Vec3 p = points.get(i);
                Vec3 tangent = points.get(Math.min(n, i + 1)).subtract(points.get(Math.max(0, i - 1)));
                Vec3 toCamera = camera.subtract(p);
                Vec3 s = tangent.cross(toCamera);
                if (s.lengthSqr() < 1.0E-8) {
                    s = tangent.cross(new Vec3(0.0, 1.0, 0.0));
                }
                if (s.lengthSqr() < 1.0E-8) {
                    s = new Vec3(1.0, 0.0, 0.0);
                }
                sides[i] = s.normalize();
            }
            for (int i = 0; i < n; i++) {
                if (!visible[i] || !visible[i + 1]) {
                    continue;
                }
                Vec3 p0 = points.get(i);
                Vec3 p1 = points.get(i + 1);
                // every segment (about a quarter of a block) shows the whole tile: it never bleeds into the next level's tile
                Vec3 s0 = sides[i].scale(widths[i] * 0.5);
                Vec3 s1 = sides[i + 1].scale(widths[i + 1] * 0.5);
                vertex(buffer, pose, p0.subtract(s0).subtract(camera), 0.0F, v0, colors[i], lights[i]);
                vertex(buffer, pose, p0.add(s0).subtract(camera), 1.0F, v0, colors[i], lights[i]);
                vertex(buffer, pose, p1.add(s1).subtract(camera), 1.0F, v1, colors[i + 1], lights[i + 1]);
                vertex(buffer, pose, p1.subtract(s1).subtract(camera), 0.0F, v1, colors[i + 1], lights[i + 1]);
            }
        });
        poseStack.popPose();
    }

    /**
     * How each level's thread moves: the stars twinkle, the embers flicker, the crystal sparkles, the
     * aurora shifts colour, a comet runs along it, the thread of destiny beats like a heart.
     */
    private static int levelTint(int tile, float t, int i, float time) {
        if (tile == BondLevel.STARS.ordinal()) {
            float tw = 0.5F + 0.5F * Mth.sin(time * 0.3F + i * 1.7F);
            return ARGB.srgbLerp(0.25F * tw, 0xFFFFFFFF, 0xFFFFE0A0);
        }
        if (tile == BondLevel.EMBER.ordinal()) {
            float fl = 0.5F + 0.5F * Mth.sin(time * 0.9F + i * 2.3F) * Mth.sin(time * 0.37F + i * 0.9F);
            return ARGB.srgbLerp(0.45F * fl, 0xFFE0C0B0, 0xFFFFFFFF);
        }
        if (tile == BondLevel.CRYSTAL.ordinal()) {
            float sp = Mth.sin(time * 0.5F + i * 3.7F);
            return sp > 0.93F ? 0xFFFFFFFF : 0xFFD8F4FF;
        }
        if (tile == BondLevel.AURORA.ordinal()) {
            float h = (time * 0.006F + t * 0.6F) % 1.0F;
            int a = 0xFF80FFB0;
            int b = 0xFF80B0FF;
            int c = 0xFFD080FF;
            return h < 0.33F ? ARGB.srgbLerp(h / 0.33F, a, b) : h < 0.66F ? ARGB.srgbLerp((h - 0.33F) / 0.33F, b, c)
                    : ARGB.srgbLerp((h - 0.66F) / 0.34F, c, a);
        }
        if (tile == BondLevel.COMET.ordinal()) {
            float pos = (time * 0.02F) % 1.4F - 0.2F;
            float d = t - pos;
            float head = d < 0.0F ? (float) Math.exp(-d * d / 0.0008) : (float) Math.exp(-d / 0.12) * (d < 0.3F ? 1.0F : 0.0F);
            return ARGB.srgbLerp(Math.min(1.0F, head), 0xFFC8C0FF, 0xFFFFFFFF);
        }
        if (tile == BondLevel.DESTINY.ordinal()) {
            float ph = (time % 24.0F) / 24.0F;
            float beat = Math.max((float) Math.exp(-Math.pow((ph - 0.1F) / 0.04F, 2)), 0.7F * (float) Math.exp(-Math.pow((ph - 0.28F) / 0.04F, 2)));
            float gold = Mth.sin(time * 0.2F + i * 1.3F) > 0.8F ? 0.6F : 0.0F;
            return ARGB.srgbLerp(Math.max(beat * 0.6F, gold), 0xFFE8D0D0, 0xFFFFE6A0);
        }
        return 0xFFFFFFFF;
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, float u, float v, int color, int light) {
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** Half of a broken thread whipping back to its owner, falling as it goes. */
    private static void drawStub(LevelRenderContext context, PoseStack poseStack, ClientLevel level, Vec3 camera, Vec3 from, Vec3 to, float half,
                                 float k, int tile, float time) {
        if (half < 0.05F) {
            return;
        }
        Vec3 dir = to.subtract(from);
        double d = dir.length();
        if (d < 1.0E-3) {
            return;
        }
        Vec3 n = dir.scale(1.0 / d);
        int segments = 8;
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double s = half * t;
            // the free end curls down more and more
            Vec3 p = from.add(n.scale(s)).add(0.0, -t * t * (0.3 + k * 1.2), 0.0);
            p = p.add(0.0, 0.05 * Math.sin(time * 0.9 + t * 6.0) * t, 0.0);
            points.add(p);
        }
        drawRibbon(context, poseStack, level, camera, points, tile, 0xFFFFFFFF, 1.0F, -1.0F, -1, false, time);
    }

    /** The end of a cut thread, hanging from the finger and swaying. */
    private static void drawDangling(LevelRenderContext context, PoseStack poseStack, ClientLevel level, Vec3 camera, Vec3 from, Vec3 toward,
                                     int tile, float time) {
        Vec3 dir = toward.subtract(from);
        Vec3 h = new Vec3(dir.x, 0.0, dir.z);
        h = h.lengthSqr() > 1.0E-4 ? h.normalize() : new Vec3(1.0, 0.0, 0.0);
        int segments = 6;
        List<Vec3> points = new ArrayList<>();
        double sway = Math.sin(time * 0.12) * 0.15;
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            Vec3 p = from.add(h.scale(0.18 * t + sway * t * t)).add(0.0, -0.55 * t, 0.0);
            points.add(p);
        }
        drawRibbon(context, poseStack, level, camera, points, tile, 0xFFFFFFFF, 1.0F, -1.0F, -1, false, time);
    }
}
