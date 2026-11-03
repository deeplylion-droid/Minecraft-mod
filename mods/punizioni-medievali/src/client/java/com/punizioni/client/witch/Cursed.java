package com.punizioni.client.witch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.punizioni.client.fx.VictimCamera;
import com.punizioni.client.render.Remains;
import com.punizioni.data.Curse;
import com.punizioni.data.ModData;
import com.punizioni.server.Curses;
import com.punizioni.witch.CurseKind;
import com.punizioni.witch.CurseShapes;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The curses on this client: the bodies change size when a curse comes or goes (every player's
 * hitbox and eyes), the local player's own movement (a toad moves by hops; a balloon floats over
 * the ground, drifting with the wind), the camera of a popped balloon (it whizzes round with it),
 * and the empty skins of popped balloons left lying on the ground.
 */
public final class Cursed {
    private Cursed() {
    }

    /** The curse each player had last tick (kind and start), to notice changes. */
    private static final Map<Integer, Long> SEEN = new HashMap<>();
    /** Popped balloons still whizzing (entity id -> owner, where it popped, when). */
    private static final Map<Integer, Zip> ZIPS = new HashMap<>();
    private static final List<Skin> SKINS = new ArrayList<>();
    private static int hopCooldown;
    /** An empty balloon lies on the ground this long (ticks). */
    private static final int SKIN_LIFE = 20 * 40;

    private record Zip(UUID owner, Vec3 at, long popAt, int seed, double drop) {
    }

    private record Skin(UUID owner, Vec3 at, float yaw, long since) {
    }

    public static void reset() {
        SEEN.clear();
        ZIPS.clear();
        SKINS.clear();
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        for (AbstractClientPlayer p : level.players()) {
            Curse c = ModData.curse(p);
            long sig = c == null ? -1L : c.kind() * 1_000_000_000_000L + c.since();
            Long old = SEEN.put(p.getId(), sig);
            if (old == null || old != sig) {
                p.refreshDimensions();
            }
            if (c != null && c.is(CurseKind.BALLOON) && c.popping() && !ZIPS.containsKey(p.getId())) {
                ZIPS.put(p.getId(), new Zip(p.getUUID(), p.position(), c.popAt(), p.getUUID().hashCode(), drop(level, p.position())));
            }
        }
        // whizzing balloons that are over: an empty skin stays on the ground
        Iterator<Map.Entry<Integer, Zip>> it = ZIPS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Zip> e = it.next();
            Zip z = e.getValue();
            Entity ent = level.getEntity(e.getKey());
            boolean over = now - z.popAt() >= Curses.ZIP_TICKS;
            if (ent == null || !ent.isAlive() || over) {
                if (over || ent == null) {
                    Vec3 end = z.at().add(BalloonShape.zip(z.seed(), Curses.ZIP_TICKS, z.drop()));
                    SKINS.add(new Skin(z.owner(), new Vec3(end.x, z.at().y - z.drop() + 0.03, end.z), (z.seed() & 0xFF) * 1.4F, now));
                }
                it.remove();
            }
        }
        SKINS.removeIf(s -> now - s.since() > SKIN_LIFE);
        SEEN.keySet().removeIf(id -> level.getEntity(id) == null);

        LocalPlayer me = mc.player;
        if (me != null && me.isAlive() && !me.isSpectator() && !me.isPassenger() && !me.getAbilities().flying) {
            Curse c = ModData.curse(me);
            if (c != null && c.is(CurseKind.BALLOON)) {
                floatTick(me, c, now);
            } else if (c != null && c.is(CurseKind.TOAD)) {
                hopTick(me);
            }
        }
    }

    private static double drop(net.minecraft.world.level.Level level, Vec3 at) {
        Vec3 from = at.add(0.0, 0.5, 0.0);
        var hit = level.clip(new ClipContext(from, from.add(0.0, -24.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? CurseShapes.BALLOON_HOVER : Math.max(0.0, at.y - hit.getLocation().y);
    }

    // ------------------------------------------------------------------ the local player's body

    /** A balloon floats over the ground (the feet a couple of blocks up), bobbing, drifting with the wind. */
    private static void floatTick(LocalPlayer p, Curse c, long now) {
        Vec3 v = p.getDeltaMovement();
        if (c.popping()) {
            p.setDeltaMovement(v.x * 0.5, v.y * 0.5, v.z * 0.5);
            return;
        }
        float k = BalloonShape.inflation(now - c.since());
        double ground = drop(p.level(), p.position());
        double hover = CurseShapes.BALLOON_HOVER * Mth.clamp(k, 0.0F, 1.0F) + Math.sin(now * 0.07 + p.getId()) * 0.2;
        double want = (hover - ground) * 0.07;
        double vy = Mth.clamp(want, -0.1, 0.085);
        vy = v.y + (vy - v.y) * 0.25;
        // the wind: slow, the same for every balloon at the same time
        double a = now * 0.0019 + 1.3;
        double wx = Math.cos(a) * 0.0035;
        double wz = Math.sin(a * 0.7) * 0.0035;
        p.setDeltaMovement(v.x + wx, vy, v.z + wz);
        p.resetFallDistance();
    }

    /** A toad does not walk: it hops, every time it is on the ground and you push a direction. */
    private static void hopTick(LocalPlayer p) {
        if (hopCooldown > 0) {
            hopCooldown--;
        }
        if (p.isInWater() || p.isInLava()) {
            return;
        }
        Vec2 move = p.input.getMoveVector();
        if (p.onGround() && hopCooldown == 0 && move.lengthSquared() > 0.01F) {
            p.jumpFromGround();
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
            Vec3 left = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
            Vec3 dir = forward.scale(move.y).add(left.scale(move.x));
            if (dir.lengthSqr() > 1.0E-4) {
                dir = dir.normalize().scale(p.isShiftKeyDown() ? 0.12 : 0.3);
                p.setDeltaMovement(p.getDeltaMovement().add(dir.x, 0.0, dir.z));
            }
            hopCooldown = 3;
        }
    }

    // ------------------------------------------------------------------ the popped balloon's camera

    /** While its balloon whizzes round, the popped player sees from it, spinning. */
    public static VictimCamera.@Nullable View zipView(Entity entity, float partial) {
        if (!(entity instanceof Avatar avatar)) {
            return null;
        }
        Curse c = ModData.curse(avatar);
        Zip z = ZIPS.get(entity.getId());
        if (c == null || !c.popping() || z == null) {
            return null;
        }
        float t = entity.level().getGameTime() - c.popAt() + partial;
        if (t > Curses.ZIP_TICKS + 20) {
            return null;
        }
        Vec3 d = BalloonShape.zip(z.seed(), Math.min(t, Curses.ZIP_TICKS), z.drop());
        Vec3 at = z.at().add(d).add(0.0, 1.2 * BalloonShape.air(Math.min(t, Curses.ZIP_TICKS)) + 0.25, 0.0);
        float spin = Math.min(t, Curses.ZIP_TICKS);
        // as vanilla turns the camera (yaw, pitch), spinning ever faster, looking more and more down
        Quaternionf q = new Quaternionf().rotationYXZ(Mth.PI - (entity.getYRot() + spin * spin * 0.9F) * Mth.DEG_TO_RAD,
                -(15.0F + spin * 1.8F) * Mth.DEG_TO_RAD, Mth.sin(spin * 0.6F) * 0.7F);
        return new VictimCamera.View(at, q);
    }

    // ------------------------------------------------------------------ empty skins on the ground

    public static void submit(LevelRenderContext context) {
        if (SKINS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack poseStack = context.poseStack();
        for (Skin s : SKINS) {
            int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(s.at()));
            float fade = Mth.clamp((SKIN_LIFE - (now - s.since())) / 60.0F, 0.0F, 1.0F);
            poseStack.pushPose();
            poseStack.translate(s.at().x - camera.x, s.at().y - camera.y - 0.15F * (1.0F - fade), s.at().z - camera.z);
            poseStack.rotate(new Quaternionf().rotationY(s.yaw()));
            poseStack.scale(-0.9375F, -0.9375F, 0.9375F);
            Balloons.submitEmpty(poseStack, context.submitNodeCollector(), Remains.skin(s.owner()).body().texturePath(), light,
                    OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }
}
