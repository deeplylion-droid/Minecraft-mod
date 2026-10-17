package com.filorosso.client.render;

import com.filorosso.entity.Recisione;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recisione, la Marionetta: a giant puppet of black cord whose strings were cut, and still rise from
 * its shoulders. The model and its four animations (idle, walk, attack1 = the strings lash down,
 * death = it collapses) come from geometry/recisione.bbmodel, played like Blockbench plays them;
 * the other attacks are posed here, on top of the idle. Now and then whole frames skip, and black
 * boxes glitch around it.
 *
 * Poses below are written in Blockbench's terms (degrees, Y up, front -Z, right = +X): on a limb that
 * hangs down +X swings it forward and +Z swings it towards +X; on something pointing up +X tilts it
 * back.
 */
public class RecisioneModel extends EntityModel<RecisioneRenderState> {
    private static final float D = Mth.DEG_TO_RAD;
    private static final int BOXES = 10;
    private static final float L_THIGH = 36.04F, L_SHIN = 33.07F, LEG = 69.0F;
    /** Distance (walk animation units, 4 per block) of one walk loop: two steps, 60 px. */
    private static final float WALK_UNITS = 15.0F;

    private final BbModel.Animation idle, walk, attack, death;
    private final ModelPart body, chest, neck, head;
    private final ModelPart[] arm = new ModelPart[2], arm2 = new ModelPart[2], hand = new ModelPart[2];
    private final ModelPart[] leg = new ModelPart[2], leg2 = new ModelPart[2], foot = new ModelPart[2];
    private final ModelPart[][] finger = new ModelPart[2][3], fingerTip = new ModelPart[2][3];
    private final ModelPart[][] wire = new ModelPart[2][2];
    private final ModelPart[][] string = new ModelPart[2][7], shortString = new ModelPart[2][4];
    private final ModelPart[] headString = new ModelPart[4];
    private final float[][] stringRest = new float[2][7 * 3];
    private final ModelPart[] box = new ModelPart[BOXES];
    /** Parts that may drop out for a glitch frame or while it falls apart (never the trunk). */
    private final ModelPart[] loose;

    public RecisioneModel(BbModel bb) {
        super(root(bb), RenderTypes::entityCutout);
        ModelPart boxes = this.root().getChild("boxes");
        for (int i = 0; i < BOXES; i++) {
            this.box[i] = boxes.getChild("box" + i);
        }
        this.idle = bb.animation("idle");
        this.walk = bb.animation("walk");
        this.attack = bb.animation("attack1");
        this.death = bb.animation("death");
        this.body = bb.bone("body");
        this.chest = bb.bone("chest");
        this.neck = bb.bone("neck");
        this.head = bb.bone("head");
        List<ModelPart> loose = new ArrayList<>();
        String[] sides = {"right", "left"};
        for (int i = 0; i < 2; i++) {
            String s = sides[i];
            this.arm[i] = bb.bone(s + "arm");
            this.arm2[i] = bb.bone(s + "arm2");
            this.hand[i] = bb.bone(s + "hand");
            this.leg[i] = bb.bone(s + "leg");
            this.leg2[i] = bb.bone(s + "leg2");
            this.foot[i] = bb.bone(s + "foot");
            for (int j = 0; j < 3; j++) {
                this.finger[i][j] = bb.bone(s + "finger" + (j + 1));
                this.fingerTip[i][j] = bb.bone(s + "finger" + (j + 1) + "b");
                loose.add(this.fingerTip[i][j]);
            }
            this.wire[i][0] = bb.bone(s + "wire");
            this.wire[i][1] = bb.bone(s + "wire2");
            for (int k = 0; k < 7; k++) {
                this.string[i][k] = bb.bone(s + "string" + (k == 0 ? "" : String.valueOf(k + 1)));
                PartPose rest = this.string[i][k].getInitialPose();
                this.stringRest[i][k * 3] = rest.xRot();
                this.stringRest[i][k * 3 + 1] = rest.yRot();
                this.stringRest[i][k * 3 + 2] = rest.zRot();
            }
            for (int k = 0; k < 4; k++) {
                this.shortString[i][k] = bb.bone(s + "stringb" + (k == 0 ? "" : String.valueOf(k + 1)));
            }
            loose.add(this.arm2[i]);
            loose.add(this.hand[i]);
            loose.add(this.foot[i]);
            loose.add(this.wire[i][0]);
            loose.add(this.string[i][3]);
            loose.add(this.string[i][5]);
            loose.add(this.shortString[i][1]);
        }
        for (int k = 0; k < 4; k++) {
            this.headString[k] = bb.bone("headstring" + (k == 0 ? "" : String.valueOf(k + 1)));
        }
        loose.add(this.headString[1]);
        loose.add(this.leg2[0]);
        loose.add(this.leg2[1]);
        this.loose = loose.toArray(new ModelPart[0]);
    }

    /** The bbmodel's bones, plus black boxes that glitch around it (not part of the model itself). */
    private static ModelPart root(BbModel bb) {
        Map<String, ModelPart> boxes = new LinkedHashMap<>();
        for (int i = 0; i < BOXES; i++) {
            float size = 5.0F + (i % 4) * 2.0F;
            ModelPart b = new ModelPart(List.of(BbModel.solidCube(size, 127.25F, 127.25F, 128.0F, 128.0F)), Map.of());
            float a = i * 2.39996F;
            b.setInitialPose(PartPose.offset(Mth.cos(a) * (22.0F + (i % 3) * 7.0F), 24.0F - (20.0F + i * 13.0F), Mth.sin(a) * (16.0F + (i % 2) * 8.0F)));
            b.resetPose();
            boxes.put("box" + i, b);
        }
        Map<String, ModelPart> top = new LinkedHashMap<>();
        top.put("model", bb.root);
        top.put("boxes", new ModelPart(List.of(), boxes));
        return new ModelPart(List.of(), top);
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

    /** Is this frame one that skips? (the renderer asks too, for the same frame) */
    static boolean glitchFrame(RecisioneRenderState s) {
        return hash(s.seed, Mth.floor(s.ageInTicks)) < s.glitch;
    }

    /** Adds a rotation given in Blockbench degrees. */
    private static void rot(ModelPart p, float x, float y, float z) {
        p.xRot -= x * D;
        p.yRot -= y * D;
        p.zRot += z * D;
    }

    /** Adds an offset given in Blockbench px. */
    private static void move(ModelPart p, float x, float y, float z) {
        p.x -= x;
        p.y -= y;
        p.z += z;
    }

    /**
     * Sinks the pelvis by {@code drop} px and bends both legs so the feet stay where they are on the
     * ground (knees forward, like the walk).
     */
    private void crouch(float drop) {
        if (drop <= 0.0F) {
            return;
        }
        float dn = LEG - drop;
        float c = Math.min(dn, L_THIGH + L_SHIN - 0.01F);
        float g = (float) Math.acos(Mth.clamp((L_THIGH * L_THIGH + c * c - L_SHIN * L_SHIN) / (2 * L_THIGH * c), -1.0F, 1.0F));
        float inner = (float) Math.acos(Mth.clamp((L_THIGH * L_THIGH + L_SHIN * L_SHIN - c * c) / (2 * L_THIGH * L_SHIN), -1.0F, 1.0F));
        float a = g / D;
        float b = -(180.0F - inner / D);
        move(this.body, 0, -drop, 0);
        for (int i = 0; i < 2; i++) {
            move(this.leg[i], 0, -drop, 0);
            rot(this.leg[i], a, 0, 0);
            rot(this.leg2[i], b, 0, 0);
            rot(this.foot[i], -(a + b), 0, 0);
        }
    }

    /** Straightens a side's long string towards straight up (k = 0..1) and makes it quiver. */
    private void stringsUp(int i, float k, float quiver, float age) {
        for (int s = 0; s < 7; s++) {
            ModelPart p = this.string[i][s];
            p.xRot -= this.stringRest[i][s * 3] * k;
            p.zRot -= this.stringRest[i][s * 3 + 2] * k;
            if (quiver > 0.0F) {
                rot(p, Mth.sin(age * 2.3F + s * 1.1F + i * 2.0F) * 4.0F * quiver, 0, Mth.cos(age * 1.9F + s * 0.9F + i) * 4.0F * quiver);
            }
        }
    }

    /** Spreads the strings out to the sides (degrees at the root of each one). */
    private void stringsSpread(float deg, float back) {
        for (int i = 0; i < 2; i++) {
            float out = i == 0 ? -1.0F : 1.0F;
            rot(this.string[i][0], back, 0, out * deg);
            rot(this.shortString[i][0], back * 1.3F, 0, out * deg * 1.4F);
        }
        rot(this.headString[0], back * 0.6F, 0, 0);
    }

    // ------------------------------------------------------------------ pose

    @Override
    public void setupAnim(RecisioneRenderState s) {
        super.setupAnim(s);
        for (ModelPart part : this.allParts()) {
            part.visible = true;
        }
        float age = s.ageInTicks;
        int seed = s.seed;
        if (s.deathTime > 0.0F) {
            this.death.apply(s.deathTime / 20.0F, 1.0F);
            this.comeApart(s.deathTime, seed);
            this.boxes(s, age);
            return;
        }
        float t = s.actionTime;
        int action = s.action;
        // how much the current action takes over from walking and standing
        float busy = switch (action) {
            case Recisione.NONE -> 0.0F;
            case Recisione.SLAM -> 1.0F;
            default -> hold(t, 0.0F, 6.0F, Recisione.duration(action) - 8.0F, Recisione.duration(action));
        };
        float walking = Mth.clamp(s.walkAnimationSpeed * 3.0F, 0.0F, 1.0F) * (1.0F - busy);
        this.idle.apply(age / 20.0F + (seed & 0xFF) * 0.37F, 1.0F - 0.65F * walking - 0.55F * busy);
        this.walk.apply(s.walkAnimationPos / WALK_UNITS * this.walk.length, walking);
        switch (action) {
            case Recisione.SLAM -> this.attack.apply(t / 20.0F, 1.0F);
            case Recisione.ROOTS -> this.roots(t, age);
            case Recisione.MISSILES -> this.missiles(t, age);
            case Recisione.GRAB -> this.grab(t);
            case Recisione.BELL -> this.bell(t, age);
            case Recisione.RISE -> this.rise(t, age);
            case Recisione.BLINK -> {
                // squeezed into a line by the renderer: the strings stand straight up
                this.stringsUp(0, 1.0F, 1.0F, age);
                this.stringsUp(1, 1.0F, 1.0F, age);
            }
            default -> {
            }
        }
        // it turns its head towards whoever it looks at (less when busy)
        float look = 1.0F - 0.6F * busy;
        this.head.yRot += Mth.clamp(s.yRot, -50.0F, 50.0F) * 0.6F * D * look;
        this.head.xRot += Mth.clamp(s.xRot, -30.0F, 30.0F) * 0.5F * D * look;
        if (s.sinceHurt < 6.0F) {
            // hit: it jerks back, the strings flinch
            float k = 1.0F - s.sinceHurt / 6.0F;
            rot(this.chest, 6.0F * k, 0, 0);
            rot(this.head, 14.0F * k, 0, 0);
            this.stringsSpread(6.0F * k, 10.0F * k);
        }
        this.boxes(s, age);
        if (glitchFrame(s)) {
            this.glitch(seed, Mth.floor(age));
        }
    }

    /** Arms up, then both hands down into the ground (tick 14): the ring of roots runs out from there. */
    private void roots(float t, float age) {
        float raise = ease(t, 0.0F, 10.0F) * (1.0F - ease(t, 11.0F, 15.0F));
        float in = ease(t, 11.0F, 15.0F) * (1.0F - ease(t, 34.0F, 44.0F));
        for (int i = 0; i < 2; i++) {
            float sx = i == 0 ? 1.0F : -1.0F;
            rot(this.arm[i], 150.0F * raise + 52.0F * in, 0, sx * (26.0F * raise + 12.0F * in));
            rot(this.arm2[i], -20.0F * raise + 8.0F * in, 0, 0);
            rot(this.hand[i], -10.0F * raise - 14.0F * in, 0, 0);
            for (int j = 0; j < 3; j++) {
                rot(this.finger[i][j], -20.0F * in, 0, sx * (j - 1) * 14.0F * in);
                rot(this.fingerTip[i][j], -12.0F * in, 0, 0);
            }
        }
        this.crouch(22.0F * in + 3.0F * raise);
        rot(this.chest, 8.0F * raise - 34.0F * in, 0, 0);
        rot(this.head, 14.0F * raise + 18.0F * in, 0, 0);
        this.stringsSpread(22.0F * in, -8.0F * raise);
        float quiver = hold(t, 14.0F, 16.0F, 28.0F, 34.0F);
        this.stringsUp(0, 0.0F, quiver, age);
        this.stringsUp(1, 0.0F, quiver, age);
    }

    /** It looks up at the sky with its arms open; the strings stand straight up and shake (missiles from tick 12). */
    private void missiles(float t, float age) {
        float k = hold(t, 0.0F, 8.0F, 28.0F, 34.0F);
        for (int i = 0; i < 2; i++) {
            float sx = i == 0 ? 1.0F : -1.0F;
            rot(this.arm[i], 12.0F * k, 0, sx * 135.0F * k);
            rot(this.arm2[i], 0, 0, sx * 10.0F * k);
            for (int j = 0; j < 3; j++) {
                rot(this.finger[i][j], (-18.0F + Mth.sin(age * 1.7F + j) * 10.0F) * k, 0, 0);
            }
            this.stringsUp(i, k, k, age);
        }
        rot(this.chest, 6.0F * k, 0, 0);
        rot(this.neck, 14.0F * k, 0, 0);
        rot(this.head, 34.0F * k, 0, 0);
        for (ModelPart p : this.headString) {
            rot(p, -4.0F * k + Mth.sin(age * 2.1F) * 3.0F * k, 0, 0);
        }
    }

    /** The right hand into the ground, then a yank upwards when the hand under them closes (tick 13). */
    private void grab(float t) {
        float in = ease(t, 0.0F, 4.0F) * (1.0F - ease(t, 12.0F, 15.0F));
        float yank = ease(t, 12.0F, 15.0F) * (1.0F - ease(t, 20.0F, 30.0F));
        rot(this.arm[0], 50.0F * in + 150.0F * yank, 0, 6.0F * in);
        rot(this.arm2[0], 6.0F * in - 30.0F * yank, 0, 0);
        for (int j = 0; j < 3; j++) {
            rot(this.finger[0][j], -24.0F * in + 50.0F * yank, 0, (j - 1) * 16.0F * in);
            rot(this.fingerTip[0][j], -10.0F * in + 40.0F * yank, 0, 0);
        }
        rot(this.arm[1], -10.0F * in, 0, -8.0F * in);
        this.crouch(18.0F * in);
        rot(this.chest, -30.0F * in + 10.0F * yank, 12.0F * in, 0);
        rot(this.head, 12.0F * in - 10.0F * yank, 0, 0);
        this.stringsSpread(-6.0F * in + 14.0F * yank, -20.0F * in + 10.0F * yank);
    }

    /** Arms out wide, head thrown back, everything trembling: the bell (tick 2) and gravity turning over (tick 10). */
    private void bell(float t, float age) {
        float k = hold(t, 0.0F, 8.0F, 46.0F, 60.0F);
        float shake = hold(t, 8.0F, 10.0F, 36.0F, 44.0F);
        float tremble = Mth.sin(age * 3.1F) * 2.5F * shake;
        for (int i = 0; i < 2; i++) {
            float sx = i == 0 ? 1.0F : -1.0F;
            rot(this.arm[i], tremble, 0, sx * 86.0F * k + tremble);
            for (int j = 0; j < 3; j++) {
                rot(this.finger[i][j], -25.0F * k + tremble * 3.0F, 0, 0);
            }
            this.stringsUp(i, 0.0F, shake, age);
        }
        rot(this.chest, 4.0F * k + tremble, 0, 0);
        rot(this.neck, 14.0F * k, 0, 0);
        rot(this.head, 30.0F * k + tremble * 2.0F, 0, -18.0F * k + tremble);
        this.stringsSpread(30.0F * k, 6.0F * k);
    }

    /** Clawing its way out of the ground (the renderer lifts it), the strings first. */
    private void rise(float t, float age) {
        float k = 1.0F - ease(t, 40.0F, 64.0F);
        float claw = Mth.sin(t * 0.3F);
        for (int i = 0; i < 2; i++) {
            float sx = i == 0 ? 1.0F : -1.0F;
            rot(this.arm[i], (160.0F + claw * sx * 14.0F) * k, 0, -sx * 10.0F * k);
            rot(this.arm2[i], (20.0F + claw * sx * 15.0F) * k, 0, 0);
            for (int j = 0; j < 3; j++) {
                rot(this.finger[i][j], (40.0F - claw * 25.0F) * k, 0, 0);
            }
            this.stringsUp(i, k, k * 0.6F, age);
        }
        rot(this.chest, -10.0F * k, 0, 0);
        rot(this.head, -24.0F * k, 0, 0);
    }

    /** Black boxes around it, jumping from one place to another (more of them in the later forms). */
    private void boxes(RecisioneRenderState s, float age) {
        int shown = s.phase == 1 ? 3 : s.phase == 2 ? 6 : BOXES;
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
                // flung away while it comes apart, and gone
                float spread = 1.0F + s.deathTime * 0.05F;
                b.x *= spread;
                b.z *= spread;
                b.visible &= s.deathTime < 60.0F;
            }
        }
    }

    /** Once it has collapsed (the death animation lasts 64 ticks) it comes apart piece by piece. */
    private void comeApart(float deathTime, int seed) {
        for (int i = 0; i < this.loose.length; i++) {
            if (deathTime > 66.0F + hash(seed + 99, i) * 28.0F) {
                this.loose[i].visible = false;
            }
        }
        if (deathTime > 92.0F) {
            this.head.visible = false;
        }
    }

    /** A frame that skips: a piece missing, the head out of place, something bent the wrong way. */
    private void glitch(int seed, int frame) {
        ModelPart gone = this.loose[(int) (hash(seed + 3, frame) * this.loose.length)];
        gone.visible = false;
        this.head.x += (hash(seed + 4, frame) - 0.5F) * 10.0F;
        this.head.zRot += (hash(seed + 5, frame) - 0.5F) * 70.0F * D;
        this.neck.xRot += (hash(seed + 6, frame) - 0.5F) * 30.0F * D;
        int i = frame & 1;
        this.arm[i].zRot += (hash(seed + 7, frame) - 0.5F) * 50.0F * D;
        this.string[1 - i][0].zRot += (hash(seed + 9, frame) - 0.5F) * 40.0F * D;
    }
}
