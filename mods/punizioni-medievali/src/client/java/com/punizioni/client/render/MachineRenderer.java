package com.punizioni.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.punizioni.Punizioni;
import com.punizioni.client.fx.VictimCamera;
import com.punizioni.machine.BrazenBull;
import com.punizioni.machine.ChoppingBlock;
import com.punizioni.machine.Gallows;
import com.punizioni.machine.Guillotine;
import com.punizioni.machine.IronMaiden;
import com.punizioni.machine.Machine;
import com.punizioni.machine.MachineKind;
import com.punizioni.machine.Pyre;
import com.punizioni.machine.QuarteringRack;
import com.punizioni.machine.RackGeometry;
import com.punizioni.machine.RatTable;
import com.punizioni.machine.Wheel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Draws any of the ten machines: its Blockbench model (MachineModel) and what goes with it: ropes to
 * the horses and from the gallows' beam, the stake's flames and the fire under the bull, the bull's
 * bronze glowing red, the pool of blood, the crows, and the remains (the head in the basket, the
 * hanged body, the bones).
 */
public class MachineRenderer<T extends Machine> extends EntityRenderer<T, MachineRenderState> {
    private static final Identifier BLOOD_POOL = Punizioni.id("textures/entity/blood_pool.png");
    private static final RenderType POOL = RenderTypes.entityTranslucent(BLOOD_POOL);

    private final MachineKind kind;
    private final MachineModel model;
    private final Identifier texture;
    private final RenderType glow;
    private final Remains remains;
    private final Crows crows;

    public MachineRenderer(EntityRendererProvider.Context context, MachineKind kind) {
        super(context);
        this.kind = kind;
        this.model = new MachineModel(BbModel.load(context.getResourceManager(), Punizioni.id("geometry/" + kind.id() + ".bbmodel")));
        this.texture = Punizioni.id("textures/entity/" + kind.id() + ".png");
        this.glow = kind == MachineKind.BRAZEN_BULL ? RenderTypes.eyes(Punizioni.id("textures/entity/" + kind.id() + "_glow.png")) : null;
        this.remains = new Remains(context);
        this.crows = kind == MachineKind.WHEEL || kind == MachineKind.GALLOWS ? new Crows(context.getResourceManager()) : null;
        this.shadowRadius = 0.0F;
    }

    @Override
    public MachineRenderState createRenderState() {
        return new MachineRenderState();
    }

    @Override
    public void extractRenderState(T m, MachineRenderState s, float partial) {
        super.extractRenderState(m, s, partial);
        s.kind = this.kind;
        s.yaw = m.getYRot();
        s.state = m.state();
        s.age = m.stateAge(partial);
        s.time = m.level().getGameTime() + partial;
        s.hasVictim = m.victimId() >= 0;
        s.choice = m.choice();
        s.x = m.getX();
        s.y = m.getY();
        s.z = m.getZ();
        s.a = s.b = s.c = s.d = 0.0F;
        switch (m) {
            case IronMaiden im -> {
                s.a = im.doors(partial);
                s.b = im.blood();
            }
            case QuarteringRack r -> s.a = r.stretch(partial);
            case RatTable t -> s.a = t.door(partial);
            case Pyre p -> s.a = p.fire(partial);
            case Guillotine g -> {
                s.a = g.blade(partial);
                s.b = g.lunette(partial);
            }
            case Wheel w -> {
                s.a = (float) w.hub(partial);
                s.b = w.spin(partial);
            }
            case BrazenBull b -> {
                s.a = b.hatch(partial);
                s.b = b.heat(partial);
                s.c = b.fire(partial);
            }
            case Gallows g -> {
                s.a = g.trapdoor(partial);
                s.b = g.drop(partial);
            }
            case ChoppingBlock c -> s.choice = c.limb().ordinal();
            default -> {
            }
        }
        s.remainsId = m.remains();
        s.remains = s.remainsId != null ? Remains.skin(s.remainsId) : null;
    }

    @Override
    public void submit(MachineRenderState s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180.0F - s.yaw);
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        int tint = -1;
        if (s.kind == MachineKind.PYRE) {
            // the faggots and the post blacken as they burn
            float charred = s.state == Pyre.REMAINS ? 1.0F : s.state == Pyre.BURNING ? Mth.clamp(s.age / 200.0F, 0.0F, 1.0F) : 0.0F;
            int c = (int) Mth.lerp(charred, 255.0F, 70.0F);
            tint = ARGB.color(255, c, (int) (c * 0.92F), (int) (c * 0.86F));
        }
        collector.submitModel(this.model, s, poseStack, this.model.renderType(this.texture), s.lightCoords, OverlayTexture.NO_OVERLAY, tint, null,
                s.outlineColor);
        if (this.glow != null && s.b > 0.01F) {
            collector.submitModel(this.model, s, poseStack, this.glow, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    ARGB.color(Mth.clamp(s.b, 0.0F, 1.0F), 0xFFFFFF), null, EntityRenderState.NO_OUTLINE);
        }
        poseStack.popPose();
        // the rest is drawn in the machine's frame, in blocks (see local): right, up, forward
        switch (s.kind) {
            case IRON_MAIDEN -> this.pool(collector, poseStack, s, 0.0F, 0.75F, 0.45F + 1.1F * s.b, s.b);
            case QUARTERING_RACK -> this.horseRopes(collector, poseStack, s, camera);
            case PYRE -> this.pyre(collector, poseStack, s);
            case RAT_TABLE -> {
                if (s.state == RatTable.REMAINS) {
                    this.bones(collector, poseStack, s, Remains.GNAWED, Remains.LYING, new Vec3(0.0, RatTable.TOP + 0.08, RatTable.HIP_Z));
                }
            }
            case GUILLOTINE -> this.guillotine(collector, poseStack, s, camera);
            case WHEEL -> this.wheel(collector, poseStack, s, camera);
            case BRAZEN_BULL -> {
                poseStack.pushPose();
                poseStack.translate(0.0F, 0.08F, 0.0F);
                Flames.ring(collector, poseStack, s.time, s.c, 9, 0.75F, 0.95F, 0.6F, 31L);
                poseStack.popPose();
            }
            case GALLOWS -> this.gallows(collector, poseStack, s, camera);
            case CHOPPING_BLOCK -> this.pool(collector, poseStack, s, 0.0F, 0.55F, 0.6F, 0.35F);
            case WHIPPING_POST -> this.pool(collector, poseStack, s, 0.0F, 0.75F, 0.5F, 0.25F);
            default -> {
            }
        }
        poseStack.popPose();
        super.submit(s, poseStack, collector, camera);
    }

    /**
     * A point of the machine's frame in the pose stack's space after rotateDegrees(YP, 180 - yaw):
     * there, as in vanilla's entity frame, +x is the machine's right and -z is the way it looks.
     */
    static Vec3 local(double right, double up, double forward) {
        return new Vec3(right, up, -forward);
    }

    /** The same point in the world. */
    private static Vec3 world(MachineRenderState s, double right, double up, double forward) {
        float yaw = s.yaw * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        double rx = -Mth.cos(yaw);
        double rz = -Mth.sin(yaw);
        return new Vec3(s.x + rx * right + fx * forward, s.y + up, s.z + rz * right + fz * forward);
    }

    // ------------------------------------------------------------------ blood

    private void pool(SubmitNodeCollector collector, PoseStack poseStack, MachineRenderState s, float right, float forward, float size, float amount) {
        if (amount <= 0.01F) {
            return;
        }
        Vec3 c = local(right, 0.012, forward);
        int color = ARGB.color(Mth.clamp(amount * 1.4F, 0.0F, 0.95F), 0xFFFFFF);
        collector.submitCustomGeometry(poseStack, POOL, (pose, buffer) -> {
            float h = size * 0.5F;
            float[][] q = {{-h, -h, 0, 0}, {-h, h, 0, 1}, {h, h, 1, 1}, {h, -h, 1, 0}};
            for (float[] p : q) {
                buffer.addVertex(pose, (float) c.x + p[0], (float) c.y, (float) c.z + p[1]).setColor(color).setUv(p[2], p[3])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(s.lightCoords).setNormal(pose, 0.0F, 1.0F, 0.0F);
            }
        });
    }

    // ------------------------------------------------------------------ quartering

    private void horseRopes(SubmitNodeCollector collector, PoseStack poseStack, MachineRenderState s, CameraRenderState camera) {
        boolean pulling = s.state == QuarteringRack.PULLING || s.state == QuarteringRack.TENSION;
        boolean ready = s.state == QuarteringRack.READY || s.state == QuarteringRack.STOPPED && s.age < 20.0F;
        if (!pulling && !ready) {
            return;
        }
        Vec3 origin = new Vec3(s.x, s.y, s.z);
        var level = Minecraft.getInstance().level;
        for (int limb = 0; limb < 4; limb++) {
            double[] a = RackGeometry.anchor(limb, s.a);
            double[] p = RackGeometry.pull(limb);
            double dist = switch (s.state) {
                case QuarteringRack.PULLING -> QuarteringRack.HORSE_DISTANCE + QuarteringRack.HORSE_PULL * s.a;
                case QuarteringRack.TENSION -> QuarteringRack.HORSE_DISTANCE + QuarteringRack.HORSE_PULL + 0.18 * Mth.sin((s.age + limb * 5) * 0.9F);
                case QuarteringRack.STOPPED -> QuarteringRack.HORSE_DISTANCE + QuarteringRack.HORSE_PULL * s.a;
                default -> QuarteringRack.HORSE_DISTANCE;
            };
            Vec3 wrist = world(s, a[0], RackGeometry.TOP + 0.08, a[1]);
            Vec3 horse = world(s, a[0] + p[0] * (dist - 0.75), 0.0, a[1] + p[1] * (dist - 0.75));
            double ground = s.y;
            if (level != null) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(horse.x), Mth.floor(horse.z));
                ground = Math.abs(top - s.y) < 4.0 ? top : s.y;
            }
            horse = new Vec3(horse.x, ground + 1.05, horse.z);
            double sag = s.state == QuarteringRack.TENSION ? 0.0 : pulling ? 0.18 * (1.0 - s.a) : 0.45;
            List<Vec3> pts = Ropes.sag(wrist, horse, sag, 10);
            // the rope in the pose stack's frame: undo the machine's rotation by drawing in world space
            poseStack.pushPose();
            poseStack.rotateDegrees(Axis.YP, -(180.0F - s.yaw));
            Ropes.rope(collector, poseStack, origin, camera.pos, pts, 0.07F, s.lightCoords);
            poseStack.popPose();
        }
    }

    // ------------------------------------------------------------------ stake

    private void pyre(SubmitNodeCollector collector, PoseStack poseStack, MachineRenderState s) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.25F, 0.0F);
        Flames.ring(collector, poseStack, s.time, s.a, 14, 1.05F, 3.6F, 0.95F, 17L);
        poseStack.popPose();
        if (s.state == Pyre.REMAINS) {
            this.bones(collector, poseStack, s, Remains.CHARRED, Remains.STAKE, new Vec3(0.0, 0.72, 0.28));
        }
    }

    private void bones(SubmitNodeCollector collector, PoseStack poseStack, MachineRenderState s, Identifier texture, int pose, Vec3 at) {
        Remains.BodyState b = new Remains.BodyState();
        b.pose = pose;
        b.time = s.time;
        poseStack.pushPose();
        Vec3 p = local(at.x, at.y, at.z);
        poseStack.translate(p.x, p.y, p.z);
        if (pose == Remains.LYING) {
            // on its back, the skull towards the back of the table
            poseStack.rotateDegrees(Axis.XP, 90.0F);
            poseStack.translate(0.0F, -0.7F, -0.12F);
        }
        poseStack.scale(-0.9375F, -0.9375F, 0.9375F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        collector.submitModel(this.remains.bones, b, poseStack, this.remains.bones.renderType(texture), s.lightCoords, OverlayTexture.NO_OVERLAY, EntityRenderState.NO_OUTLINE);
        poseStack.popPose();
    }

    // ------------------------------------------------------------------ guillotine

    private void guillotine(SubmitNodeCollector collector, PoseStack poseStack, MachineRenderState s, CameraRenderState camera) {
        boolean cut = s.state == Guillotine.DONE || s.state == Guillotine.RESET;
        this.pool(collector, poseStack, s, 0.0F, (float) Guillotine.BASKET.z - 0.15F, cut ? 1.3F : 0.6F, cut ? 0.9F : 0.25F);
        // the rope that holds the blade, over the crossbar and down to a cleat on the post
        Vec3 origin = new Vec3(s.x, s.y, s.z);
        Vec3 bladeTop = world(s, 0.0, s.a + 0.95, 0.0);
        Vec3 pulley = world(s, 0.0, 4.12, 0.0);
        Vec3 cleat = world(s, 0.62, 1.3, 0.1);
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, -(180.0F - s.yaw));
        Ropes.rope(collector, poseStack, origin, camera.pos, List.of(bladeTop, pulley), 0.05F, s.lightCoords);
        Ropes.rope(collector, poseStack, origin, camera.pos, List.of(pulley, world(s, 0.62, 4.12, 0.0), cleat), 0.05F, s.lightCoords);
        poseStack.popPose();
        if (cut && s.remains != null && s.state == Guillotine.DONE && !VictimCamera.seesFromHead(s.remainsId)) {
            // the head falls into the basket (HeadFall: the victim sees the same fall from inside it)
            Remains.BodyState b = new Remains.BodyState();
            poseStack.pushPose();
            Vec3 c = HeadFall.position(s.age);
            Vec3 p = local(c.x, c.y, c.z);
            poseStack.translate(p.x, p.y, p.z);
            poseStack.rotate(HeadFall.orientation(s.age));
            poseStack.translate(0.0F, -4.0F * 0.9375F / 16.0F, 0.0F);
            poseStack.scale(-0.9375F, -0.9375F, 0.9375F);
            collector.submitModel(this.remains.head, b, poseStack, RenderTypes.entityTranslucent(s.remains.body().texturePath()), s.lightCoords,
                    OverlayTexture.NO_OVERLAY, EntityRenderState.NO_OUTLINE);
            poseStack.popPose();
        }
    }

    // ------------------------------------------------------------------ wheel

    private void wheel(SubmitNodeCollector collector, PoseStack poseStack, MachineRenderState s, CameraRenderState camera) {
        Vec3 origin = new Vec3(s.x, s.y, s.z);
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, -(180.0F - s.yaw));
        Ropes.rope(collector, poseStack, origin, camera.pos, List.of(world(s, 0.0, 5.62, 0.22), world(s, 0.0, s.a + 0.2, 0.22)), 0.06F, s.lightCoords);
        poseStack.popPose();
        if (s.state == Wheel.EMPTY && s.remains != null) {
            Remains.BodyState b = new Remains.BodyState();
            b.pose = Remains.SPREAD;
            b.broken = true;
            poseStack.pushPose();
            Vec3 hub = local(0.0, s.a, 0.42);
            poseStack.translate(hub.x, hub.y, hub.z);
            poseStack.rotateDegrees(Axis.ZP, s.b);
            poseStack.translate(0.0F, -0.72F, 0.0F);
            poseStack.scale(-0.9375F, -0.9375F, 0.9375F);
            poseStack.translate(0.0F, -1.501F, 0.0F);
            Remains.Corpse corpse = this.remains.corpse(s.remains);
            collector.submitModel(corpse, b, poseStack, RenderTypes.entityTranslucent(s.remains.body().texturePath()), s.lightCoords,
                    OverlayTexture.NO_OVERLAY, EntityRenderState.NO_OUTLINE);
            poseStack.popPose();
        }
        if (this.crows != null && (s.state == Wheel.RAISED || s.state == Wheel.EMPTY)) {
            float since = s.state == Wheel.RAISED ? s.age - Wheel.CROWS_AFTER : s.age + 200.0F;
            Vec3[] perches = {local(0.0, 5.86, 0.0), local(0.55, s.a + 1.15, 0.3), local(-0.6, s.a + 1.1, 0.3), local(0.15, s.a + 0.55, 0.62),
                    local(-0.25, s.a - 0.2, 0.6)};
            this.crows.submit(collector, poseStack, since, s.time, perches, local(0.0, s.a + 0.5, 0.4), s.lightCoords);
        }
    }

    // ------------------------------------------------------------------ gallows

    /** The body sways on the rope (same numbers as the victim's own drawing, see Poses). */
    public static float swayX(float time, float age) {
        float k = Math.min(1.0F, age / 10.0F);
        return (Mth.sin(time * 0.08F) * 7.0F + Mth.sin(time * 0.21F) * 2.0F) * k;
    }

    public static float swayZ(float time) {
        return Mth.sin(time * 0.055F + 1.3F) * 4.0F;
    }

    private void gallows(SubmitNodeCollector collector, PoseStack poseStack, MachineRenderState s, CameraRenderState camera) {
        Vec3 origin = new Vec3(s.x, s.y, s.z);
        Vec3 beam = world(s, 0.0, Gallows.BEAM, 0.0);
        boolean hanging = s.state == Gallows.HANGING || s.state == Gallows.CORPSE && s.remains != null;
        double neckY = Gallows.FLOOR - (s.state == Gallows.CORPSE ? Gallows.DROP : s.b) + 1.45;
        Vec3 neck;
        if (s.state == Gallows.IDLE || s.state == Gallows.CORPSE && s.remains == null) {
            neck = world(s, 0.0, Gallows.FLOOR + 1.55, 0.0);
        } else {
            // the rope follows the sway of the body
            double len = Gallows.BEAM - neckY;
            float sx = hanging ? swayX(s.time, s.state == Gallows.CORPSE ? 100.0F : s.age) * Mth.DEG_TO_RAD : 0.0F;
            float sz = hanging ? swayZ(s.time) * Mth.DEG_TO_RAD : 0.0F;
            neck = world(s, Math.sin(sz) * len, Gallows.BEAM - len * Math.cos(sx) * Math.cos(sz), Math.sin(sx) * len);
        }
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, -(180.0F - s.yaw));
        Ropes.rope(collector, poseStack, origin, camera.pos, List.of(beam, neck), 0.06F, s.lightCoords);
        if (s.state == Gallows.IDLE) {
            // the empty noose: a loop
            List<Vec3> loop = new java.util.ArrayList<>();
            for (int i = 0; i <= 12; i++) {
                double a = i / 12.0 * Mth.TWO_PI;
                loop.add(neck.add(Math.sin(a) * 0.16, -0.16 - Math.cos(a) * 0.16, 0.0));
            }
            Ropes.rope(collector, poseStack, origin, camera.pos, loop, 0.05F, s.lightCoords);
        }
        poseStack.popPose();
        if (s.state == Gallows.CORPSE && s.remains != null) {
            Remains.BodyState b = new Remains.BodyState();
            b.pose = Remains.HANGED;
            poseStack.pushPose();
            Vec3 top = local(0.0, Gallows.BEAM, 0.0);
            poseStack.translate(top.x, top.y, top.z);
            poseStack.rotateDegrees(Axis.XP, swayX(s.time, 100.0F));
            poseStack.rotateDegrees(Axis.ZP, swayZ(s.time));
            poseStack.rotateDegrees(Axis.YP, Mth.sin(s.time * 0.013F) * 35.0F);
            poseStack.translate(0.0F, (float) -(Gallows.BEAM - (Gallows.FLOOR - Gallows.DROP)), 0.0F);
            poseStack.scale(-0.9375F, -0.9375F, 0.9375F);
            poseStack.translate(0.0F, -1.501F, 0.0F);
            Remains.Corpse corpse = this.remains.corpse(s.remains);
            collector.submitModel(corpse, b, poseStack, RenderTypes.entityTranslucent(s.remains.body().texturePath()), s.lightCoords,
                    OverlayTexture.NO_OVERLAY, EntityRenderState.NO_OUTLINE);
            poseStack.popPose();
            if (this.crows != null) {
                double body = Gallows.FLOOR - Gallows.DROP;
                Vec3[] perches = {local(0.9, Gallows.BEAM + 0.1, 0.0), local(-1.05, Gallows.BEAM + 0.1, 0.0), local(0.24, body + 1.42, 0.02),
                        local(0.0, body + 1.95, 0.0)};
                this.crows.submit(collector, poseStack, s.age - 60.0F, s.time, perches, local(0.0, Gallows.BEAM - 1.0, 0.0), s.lightCoords);
            }
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(T m, float partial) {
        AABB box = super.getBoundingBoxForCulling(m, partial);
        return switch (this.kind) {
            case QUARTERING_RACK -> box.inflate(9.0, 1.5, 9.0);
            case PYRE -> box.inflate(1.0, 0.0, 1.0).expandTowards(0.0, 3.0, 0.0);
            case WHEEL, GALLOWS -> box.inflate(3.0, 1.0, 3.0);
            default -> box.inflate(1.0);
        };
    }
}
