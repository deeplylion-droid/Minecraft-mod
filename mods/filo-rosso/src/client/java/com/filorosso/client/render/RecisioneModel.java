package com.filorosso.client.render;

import com.filorosso.entity.Recisione;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Recisione's body, posed by hand every frame. It mostly stands still in a way nothing alive does:
 * the head snaps to a crooked angle and stays there, a finger jerks now and then, whole frames
 * skip. Tendrils with eyes grow from its shoulders in the second form, the eyes on its spine and
 * neck open in the last one, and black boxes glitch around it.
 */
public class RecisioneModel extends EntityModel<RecisioneRenderState> {
    private static final float D = Mth.DEG_TO_RAD;
    private static final int SEGMENTS = 9;
    private static final int BOXES = 10;

    private final ModelPart hips;
    private final ModelPart torso;
    private final ModelPart spineEyes;
    private final ModelPart neck;
    private final ModelPart neckEyes;
    private final ModelPart head;
    private final ModelPart eyes;
    private final ModelPart[] arm = new ModelPart[2];
    private final ModelPart[] forearm = new ModelPart[2];
    private final ModelPart[] hand = new ModelPart[2];
    private final ModelPart[][] fingers = new ModelPart[2][4];
    private final ModelPart[] leg = new ModelPart[2];
    private final ModelPart[] shin = new ModelPart[2];
    private final ModelPart[] foot = new ModelPart[2];
    private final ModelPart[][][] tendril = new ModelPart[2][2][SEGMENTS];
    private final ModelPart[] box = new ModelPart[BOXES];
    /** Parts that may drop out for a glitch frame or while it falls apart (never the trunk). */
    private final ModelPart[] loose;
    /** Every part: resetPose() does not touch visibility, so each frame starts with all of them shown. */
    private final ModelPart[] all;

    /** How hard the tendrils bend forward (strikes) and how much they curl (0 = straight up). */
    private float tendrilBend;
    private float tendrilCurl;
    private float tendrilSpread;
    private float tendrilQuiver;

    public RecisioneModel(CubeModels.Baked baked) {
        super(baked.root(), RenderTypes::entityCutout);
        this.hips = baked.part("hips");
        this.torso = baked.part("torso");
        this.spineEyes = baked.part("spine_eyes");
        this.neck = baked.part("neck");
        this.neckEyes = baked.part("neck_eyes");
        this.head = baked.part("head");
        this.eyes = baked.part("eyes");
        List<ModelPart> loose = new ArrayList<>();
        String[] sides = {"r", "l"};
        for (int i = 0; i < 2; i++) {
            this.arm[i] = baked.part("arm_" + sides[i]);
            this.forearm[i] = baked.part("forearm_" + sides[i]);
            this.hand[i] = baked.part("hand_" + sides[i]);
            this.leg[i] = baked.part("leg_" + sides[i]);
            this.shin[i] = baked.part("shin_" + sides[i]);
            this.foot[i] = baked.part("foot_" + sides[i]);
            loose.add(this.forearm[i]);
            loose.add(this.hand[i]);
            loose.add(this.foot[i]);
            for (int j = 0; j < 4; j++) {
                this.fingers[i][j] = baked.part("finger_" + sides[i] + j);
                loose.add(this.fingers[i][j]);
            }
            for (int t = 0; t < 2; t++) {
                for (int s = 0; s < SEGMENTS; s++) {
                    this.tendril[i][t][s] = baked.part("tendril_" + sides[i] + t + "_" + s);
                    if (s >= 3) {
                        loose.add(this.tendril[i][t][s]);
                    }
                }
            }
        }
        for (int i = 0; i < BOXES; i++) {
            this.box[i] = baked.part("box_" + i);
        }
        loose.add(this.eyes);
        loose.add(this.arm[0]);
        loose.add(this.arm[1]);
        loose.add(this.shin[0]);
        loose.add(this.shin[1]);
        this.loose = loose.toArray(new ModelPart[0]);
        this.all = baked.parts().values().toArray(new ModelPart[0]);
    }

    // ------------------------------------------------------------------ helpers

    /** 0 before a, 1 after b, smooth in between. */
    private static float ease(float t, float a, float b) {
        float k = Mth.clamp((t - a) / (b - a), 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    /** Goes 0 -> 1 between a and b, stays, then 1 -> 0 between c and d. */
    private static float hold(float t, float a, float b, float c, float d) {
        return ease(t, a, b) * (1.0F - ease(t, c, d));
    }

    /** A random number in [0, 1) that only depends on its inputs. */
    static float hash(int a, int b) {
        int h = a * 0x27D4EB2D ^ b * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    /** A value in [-1, 1] that holds still, then snaps to a new one in a tick and a half. */
    private static float snap(int seed, float age, float period, int salt) {
        float slot = age / period + hash(seed, salt) * 7.0F;
        int k = Mth.floor(slot);
        float since = (slot - k) * period;
        float a = hash(seed + salt * 31, k - 1) * 2.0F - 1.0F;
        float b = hash(seed + salt * 31, k) * 2.0F - 1.0F;
        return Mth.lerp(Mth.clamp(since / 1.5F, 0.0F, 1.0F), a, b);
    }

    /** Now and then (a chance per period) a short jerk: 0 most of the time, up to 1 for four ticks. */
    private static float jerk(int seed, float age, float period, float chance, int salt) {
        float slot = age / period;
        int k = Mth.floor(slot);
        if (hash(seed + salt * 131, k) > chance) {
            return 0.0F;
        }
        float at = hash(seed + salt * 17, k) * (period - 4.0F);
        float f = (slot - k) * period - at;
        return f < 0.0F || f > 4.0F ? 0.0F : Mth.sin(f / 4.0F * Mth.PI);
    }

    /** Is this frame one that skips? (the renderer asks too, for the same frame) */
    static boolean glitchFrame(RecisioneRenderState s) {
        return hash(s.seed, Mth.floor(s.ageInTicks)) < s.glitch;
    }

    // ------------------------------------------------------------------ pose

    @Override
    public void setupAnim(RecisioneRenderState s) {
        super.setupAnim(s);
        for (ModelPart part : this.all) {
            part.visible = true;
        }
        float age = s.ageInTicks;
        int seed = s.seed;
        float walk = s.walkAnimationPos * 0.33F;
        float speed = Math.min(1.0F, s.walkAnimationSpeed * 1.4F);
        float breath = Mth.sin(age * 0.05F);
        this.tendrilBend = 0.0F;
        this.tendrilCurl = 1.0F;
        this.tendrilSpread = 1.0F;
        this.tendrilQuiver = 0.0F;

        // --- standing: hunched a little, arms hanging down to its knees
        this.torso.xRot = (7.0F + breath) * D;
        this.torso.zRot = snap(seed, age, 90.0F, 1) * 3.0F * D;
        this.neck.xRot = 10.0F * D;
        // the head snaps to a crooked angle and stays there; sometimes it jerks
        float twitch = jerk(seed, age, 50.0F, 0.35F, 5);
        this.head.zRot = (snap(seed, age, 47.0F, 2) * 28.0F + twitch * 22.0F) * D;
        this.head.xRot = (snap(seed, age, 61.0F, 3) * 10.0F + 4.0F) * D + Mth.clamp(s.xRot, -30.0F, 30.0F) * 0.5F * D;
        this.head.yRot = snap(seed, age, 53.0F, 4) * 18.0F * D + Mth.clamp(s.yRot, -50.0F, 50.0F) * 0.6F * D;
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? 1.0F : -1.0F;
            float swing = Mth.cos(walk + i * Mth.PI) * 0.2F * speed;
            this.arm[i].xRot = -3.0F * D + swing;
            this.arm[i].zRot = side * (4.0F + Math.max(0.0F, breath) * 1.5F) * D;
            this.forearm[i].xRot = -8.0F * D - Math.max(0.0F, -swing) * 0.5F;
            this.hand[i].xRot = -5.0F * D;
            for (int j = 0; j < 4; j++) {
                // a slow curl, and now and then one finger jerks
                float jerk = jerk(seed + i * 7 + j, age, 26.0F + j * 6.0F, 0.3F, 10 + j);
                this.fingers[i][j].xRot = (-10.0F - 4.0F * Mth.sin(age * 0.04F + j) - 55.0F * jerk) * D;
                this.fingers[i][j].zRot = side * (j - 1.5F) * 4.0F * D;
            }
            float step = i == 0 ? Mth.PI : 0.0F;
            this.leg[i].xRot = Mth.cos(walk + step) * 0.42F * speed;
            this.shin[i].xRot = Math.max(0.0F, Mth.sin(walk + step)) * 0.6F * speed;
            this.foot[i].xRot = -this.shin[i].xRot * 0.5F;
        }
        this.hips.y += Math.abs(Mth.sin(walk)) * 1.5F * speed;

        // --- the eyes: now and then they go out for a moment
        this.eyes.visible = jerk(seed, age, 80.0F, 0.6F, 40) < 0.5F;
        // eyes on its spine and neck, opening (flickering on) in the last form
        boolean on = false;
        if (s.phase >= 3) {
            float open = ease(s.phaseTime, 10.0F, 40.0F);
            on = hash(seed + 41, Mth.floor(age * 0.5F)) < open && jerk(seed, age, 37.0F, 0.5F, 41) < 0.5F;
        }
        this.spineEyes.visible = on;
        this.neckEyes.visible = on;

        // --- what it is doing
        float t = s.actionTime;
        switch (s.action) {
            case Recisione.SLAM -> this.slam(t);
            case Recisione.ROOTS -> this.roots(t);
            case Recisione.MISSILES -> this.missiles(t, age);
            case Recisione.GRAB -> this.grab(t);
            case Recisione.BELL -> this.bell(t, age);
            case Recisione.RISE -> this.rise(t);
            default -> {
            }
        }
        if (s.sinceHurt < 6.0F) {
            // hit: it jerks back
            float k = 1.0F - s.sinceHurt / 6.0F;
            this.torso.xRot -= 8.0F * k * D;
            this.head.xRot -= 14.0F * k * D;
        }
        this.tendrils(s, age);
        this.boxes(s, age);
        if (s.deathTime > 0.0F) {
            this.die(s.deathTime, seed);
        }
        if (glitchFrame(s)) {
            this.glitch(seed, Mth.floor(age));
        }
    }

    /**
     * Bends both legs (degrees: thigh forward, knee back) and lowers the body by as much as the legs
     * got shorter, so the feet stay on the ground.
     */
    private void crouch(float thigh, float knee) {
        float a = thigh * D;
        float b = (knee - thigh) * D;
        float drop = 36.0F * (1.0F - Mth.cos(a)) + 36.0F * (1.0F - Mth.cos(b));
        for (int i = 0; i < 2; i++) {
            this.leg[i].xRot = -a;
            this.shin[i].xRot = knee * D;
            this.foot[i].xRot = -b;
            this.leg[i].y += drop;
        }
        this.hips.y += drop;
    }

    /** Right arm high over its head, then down in front like a whip (hit at tick 11). */
    private void slam(float t) {
        float up = ease(t, 0.0F, 8.0F) * (1.0F - ease(t, 9.0F, 11.0F));
        float down = ease(t, 9.0F, 11.0F) * (1.0F - ease(t, 15.0F, 24.0F));
        this.arm[0].xRot = Mth.lerp(up, this.arm[0].xRot, -168.0F * D);
        this.arm[0].zRot = Mth.lerp(up, this.arm[0].zRot, -6.0F * D);
        this.arm[0].xRot = Mth.lerp(down, this.arm[0].xRot, -55.0F * D);
        this.forearm[0].xRot = Mth.lerp(up, this.forearm[0].xRot, -35.0F * D);
        this.forearm[0].xRot = Mth.lerp(down, this.forearm[0].xRot, -6.0F * D);
        for (int j = 0; j < 4; j++) {
            this.fingers[0][j].xRot = Mth.lerp(down, this.fingers[0][j].xRot, 18.0F * D);
        }
        this.crouch(40.0F * down, 80.0F * down);
        this.torso.xRot += (-9.0F * up + 28.0F * down) * D;
        this.torso.yRot += (14.0F * up - 10.0F * down) * D;
        this.head.xRot += 10.0F * down * D;
        this.tendrilBend = 30.0F * down - 10.0F * up;
    }

    /** Both hands into the ground (tick 14): the ring runs out from there. */
    private void roots(float t) {
        float raise = ease(t, 0.0F, 10.0F) * (1.0F - ease(t, 11.0F, 15.0F));
        float in = ease(t, 11.0F, 15.0F) * (1.0F - ease(t, 34.0F, 44.0F));
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? 1.0F : -1.0F;
            this.arm[i].xRot = Mth.lerp(raise, this.arm[i].xRot, -150.0F * D);
            this.arm[i].zRot = Mth.lerp(raise, this.arm[i].zRot, side * 28.0F * D);
            this.arm[i].xRot = Mth.lerp(in, this.arm[i].xRot, -42.0F * D);
            this.arm[i].zRot = Mth.lerp(in, this.arm[i].zRot, side * 10.0F * D);
            this.forearm[i].xRot = Mth.lerp(Math.max(raise, in), this.forearm[i].xRot, 0.0F);
            for (int j = 0; j < 4; j++) {
                this.fingers[i][j].xRot = Mth.lerp(in, this.fingers[i][j].xRot, (j - 1.5F) * 12.0F * D);
            }
        }
        this.crouch(50.0F * in, 100.0F * in);
        this.torso.xRot += (-10.0F * raise + 34.0F * in) * D;
        this.head.xRot += (-20.0F * raise + 18.0F * in) * D;
        this.tendrilBend = 24.0F * in;
        this.tendrilSpread = 1.0F + 0.5F * in;
    }

    /** It looks up at the sky with its arms open; the tendrils point up and shake (missiles from tick 12). */
    private void missiles(float t, float age) {
        float k = hold(t, 0.0F, 8.0F, 28.0F, 34.0F);
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? 1.0F : -1.0F;
            this.arm[i].zRot = Mth.lerp(k, this.arm[i].zRot, side * 140.0F * D);
            this.arm[i].xRot = Mth.lerp(k, this.arm[i].xRot, -12.0F * D);
            this.forearm[i].xRot = Mth.lerp(k, this.forearm[i].xRot, 0.0F);
            for (int j = 0; j < 4; j++) {
                this.fingers[i][j].xRot = Mth.lerp(k, this.fingers[i][j].xRot, 20.0F * D + Mth.sin(age * 1.7F + j) * 10.0F * D);
            }
        }
        this.torso.xRot = Mth.lerp(k, this.torso.xRot, -6.0F * D);
        this.neck.xRot = Mth.lerp(k, this.neck.xRot, -20.0F * D);
        this.head.xRot = Mth.lerp(k, this.head.xRot, -38.0F * D);
        this.tendrilCurl = 1.0F - 0.75F * k;
        this.tendrilSpread = 1.0F - 0.6F * k;
        this.tendrilQuiver = k;
    }

    /** The right hand into the ground, then a yank upwards when the hand under them closes (tick 13). */
    private void grab(float t) {
        float in = ease(t, 0.0F, 4.0F) * (1.0F - ease(t, 12.0F, 15.0F));
        float yank = ease(t, 12.0F, 15.0F) * (1.0F - ease(t, 20.0F, 30.0F));
        this.arm[0].xRot = Mth.lerp(in, this.arm[0].xRot, -38.0F * D);
        this.arm[0].xRot = Mth.lerp(yank, this.arm[0].xRot, -110.0F * D);
        this.forearm[0].xRot = Mth.lerp(in, this.forearm[0].xRot, 0.0F);
        this.forearm[0].xRot = Mth.lerp(yank, this.forearm[0].xRot, -40.0F * D);
        for (int j = 0; j < 4; j++) {
            this.fingers[0][j].xRot = Mth.lerp(in, this.fingers[0][j].xRot, (j - 1.5F) * 14.0F * D);
            this.fingers[0][j].xRot = Mth.lerp(yank, this.fingers[0][j].xRot, -80.0F * D);
        }
        this.crouch(45.0F * in, 90.0F * in);
        this.torso.xRot += (32.0F * in - 6.0F * yank) * D;
        this.torso.yRot += -10.0F * in * D;
        this.head.xRot += 15.0F * in * D;
        this.tendrilBend = 18.0F * in - 12.0F * yank;
    }

    /** Arms out wide, head thrown back, everything trembling: the bell (tick 2) and gravity turning over (tick 10). */
    private void bell(float t, float age) {
        float k = hold(t, 0.0F, 8.0F, 46.0F, 60.0F);
        float shake = hold(t, 8.0F, 10.0F, 36.0F, 44.0F);
        float tremble = Mth.sin(age * 3.1F) * 2.5F * shake * D;
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? 1.0F : -1.0F;
            this.arm[i].zRot = Mth.lerp(k, this.arm[i].zRot, side * 86.0F * D) + tremble;
            this.arm[i].xRot = Mth.lerp(k, this.arm[i].xRot, 0.0F) - tremble;
            this.forearm[i].xRot = Mth.lerp(k, this.forearm[i].xRot, 0.0F);
            for (int j = 0; j < 4; j++) {
                this.fingers[i][j].xRot = Mth.lerp(k, this.fingers[i][j].xRot, 25.0F * D) + tremble * 3.0F;
            }
        }
        this.torso.xRot = Mth.lerp(k, this.torso.xRot, -4.0F * D) + tremble;
        this.neck.xRot = Mth.lerp(k, this.neck.xRot, -14.0F * D);
        this.head.xRot = Mth.lerp(k, this.head.xRot, -32.0F * D) + tremble * 2.0F;
        this.head.zRot = Mth.lerp(k, this.head.zRot, 18.0F * D) + tremble;
        this.tendrilSpread = 1.0F + 0.9F * k;
        this.tendrilCurl = 1.0F - 0.4F * k;
        this.tendrilQuiver = shake;
    }

    /** Clawing its way out of the ground (the renderer lifts it). */
    private void rise(float t) {
        float k = 1.0F - ease(t, 40.0F, 64.0F);
        float claw = Mth.sin(t * 0.3F);
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? 1.0F : -1.0F;
            this.arm[i].xRot = Mth.lerp(k, this.arm[i].xRot, (-160.0F + claw * side * 14.0F) * D);
            this.arm[i].zRot = Mth.lerp(k, this.arm[i].zRot, -side * 10.0F * D);
            this.forearm[i].xRot = Mth.lerp(k, this.forearm[i].xRot, (-20.0F - claw * side * 15.0F) * D);
            for (int j = 0; j < 4; j++) {
                this.fingers[i][j].xRot = Mth.lerp(k, this.fingers[i][j].xRot, (-50.0F + claw * 25.0F) * D);
            }
        }
        this.torso.xRot += 14.0F * k * D;
        this.head.xRot += 28.0F * k * D;
    }

    /** Two tendrils on each shoulder: they grow when the second form begins, then sway. */
    private void tendrils(RecisioneRenderState s, float age) {
        float grow = s.phase >= 3 ? 1.0F : s.phase == 2 ? Mth.clamp(s.phaseTime / 40.0F, 0.0F, 1.0F) : 0.0F;
        for (int side = 0; side < 2; side++) {
            float out = side == 0 ? -1.0F : 1.0F;
            for (int t = 0; t < 2; t++) {
                ModelPart[] seg = this.tendril[side][t];
                if (grow <= 0.0F) {
                    seg[0].visible = false;
                    continue;
                }
                float grown = grow * SEGMENTS;
                float wave = age * 0.05F + t * 1.9F + side * 0.7F;
                for (int k = 0; k < SEGMENTS; k++) {
                    float g = Mth.clamp(grown - k, 0.0F, 1.0F);
                    seg[k].visible = g > 0.0F;
                    seg[k].yScale = g;
                    seg[k].xScale = 0.5F + 0.5F * g;
                    seg[k].zScale = 0.5F + 0.5F * g;
                    float kk = k / (float) (SEGMENTS - 1);
                    float quiver = Mth.sin(age * 2.3F + k * 1.1F + t * 2.0F + side) * 5.0F * this.tendrilQuiver;
                    if (k == 0) {
                        seg[k].zRot = out * (30.0F + 16.0F * t) * this.tendrilSpread * D;
                        seg[k].xRot = (-(16.0F + 10.0F * t) + this.tendrilBend) * D;
                    } else {
                        seg[k].zRot = (-out * (5.0F + 2.0F * t) * this.tendrilCurl + Mth.cos(wave + k * 0.5F) * 4.0F * kk + quiver) * D;
                        seg[k].xRot = (Mth.sin(wave * 1.3F + k * 0.6F) * (3.0F + 6.0F * kk) - 2.0F + this.tendrilBend * 0.25F + quiver) * D;
                    }
                }
            }
        }
    }

    /** Black boxes around it, jumping from one place to another (more of them in the later forms). */
    private void boxes(RecisioneRenderState s, float age) {
        int shown = s.phase == 1 ? 3 : s.phase == 2 ? 7 : BOXES;
        for (int i = 0; i < BOXES; i++) {
            ModelPart b = this.box[i];
            if (i >= shown) {
                b.visible = false;
                continue;
            }
            int seed = s.seed + i * 977;
            float period = 5.0F + hash(seed, 1) * 9.0F;
            int k = Mth.floor((age + hash(seed, 2) * 20.0F) / period);
            b.visible = hash(seed, k * 3) > 0.18F;
            b.x += (hash(seed, k * 3 + 1) - 0.5F) * 14.0F;
            b.y += (hash(seed, k * 3 + 2) - 0.5F) * 18.0F;
            b.z += (hash(seed + 5, k) - 0.5F) * 14.0F;
            float scale = 0.6F + hash(seed + 6, k) * 0.8F;
            b.xScale = scale;
            b.yScale = scale;
            b.zScale = scale;
            b.xRot = hash(seed + 7, k) * 90.0F * D;
            b.yRot = hash(seed + 8, k) * 90.0F * D;
            if (s.deathTime > 0.0F) {
                // flung away while it comes apart
                float spread = 1.0F + s.deathTime * 0.04F;
                b.x *= spread;
                b.z *= spread;
            }
        }
    }

    /** Hanging from the sky by its head, limp, coming apart piece by piece (the renderer lifts it). */
    private void die(float deathTime, int seed) {
        float limp = ease(deathTime, 0.0F, 25.0F);
        this.head.xRot = Mth.lerp(limp, this.head.xRot, 24.0F * D);
        this.head.zRot = Mth.lerp(limp, this.head.zRot, 38.0F * D);
        this.torso.xRot = Mth.lerp(limp, this.torso.xRot, -3.0F * D);
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? 1.0F : -1.0F;
            float sway = Mth.sin(deathTime * 0.15F + i * 2.0F) * 6.0F * D;
            this.arm[i].xRot = Mth.lerp(limp, this.arm[i].xRot, 3.0F * D) + sway;
            this.arm[i].zRot = Mth.lerp(limp, this.arm[i].zRot, side * 2.0F * D);
            this.forearm[i].xRot = Mth.lerp(limp, this.forearm[i].xRot, 0.0F);
            this.leg[i].xRot = Mth.lerp(limp, this.leg[i].xRot, 0.0F) - sway;
            this.shin[i].xRot = Mth.lerp(limp, this.shin[i].xRot, 8.0F * D);
            this.foot[i].xRot = Mth.lerp(limp, this.foot[i].xRot, 40.0F * D);
        }
        for (int i = 0; i < this.loose.length; i++) {
            if (deathTime > 25.0F + hash(seed + 99, i) * 70.0F) {
                this.loose[i].visible = false;
            }
        }
    }

    /** A frame that skips: a limb missing, the head out of place, something bent the wrong way. */
    private void glitch(int seed, int frame) {
        ModelPart gone = this.loose[(int) (hash(seed + 3, frame) * this.loose.length)];
        gone.visible = false;
        this.head.x += (hash(seed + 4, frame) - 0.5F) * 10.0F;
        this.head.zRot += (hash(seed + 5, frame) - 0.5F) * 70.0F * D;
        this.neck.xRot += (hash(seed + 6, frame) - 0.5F) * 30.0F * D;
        int i = frame & 1;
        this.arm[i].zRot += (hash(seed + 7, frame) - 0.5F) * 50.0F * D;
        this.eyes.visible &= hash(seed + 8, frame) > 0.4F;
    }
}
