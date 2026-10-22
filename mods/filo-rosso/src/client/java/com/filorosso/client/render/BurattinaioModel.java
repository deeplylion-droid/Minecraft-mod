package com.filorosso.client.render;

import com.filorosso.entity.Burattinaio;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Il Burattinaio: geometry/burattinaio.bbmodel (made with the monster skill; open
 * blockbench/burattinaio.bbmodel in Blockbench), with its four animations played the way Blockbench
 * plays them: idle (hanging, working its strings), walk (gliding), attack1 (the strings that command)
 * and death (its strings snap, it drops, the mask comes off). The other attacks are posed here on top
 * of the idle, in Blockbench degrees (on a limb that hangs down +X swings it forward, +Z swings it
 * towards +X).
 */
public class BurattinaioModel extends EntityModel<BurattinaioRenderState> {
    private static final float D = Mth.DEG_TO_RAD;
    /** Walk animation units (4 per block) of one walk loop (3 s at its gliding speed). */
    private static final float WALK_UNITS = 30.0F;

    private final BbModel.Animation idle, walk, attack, death;
    private final ModelPart body, chest, head, mask;
    private final ModelPart[] arm = new ModelPart[2], arm2 = new ModelPart[2], hand = new ModelPart[2], control = new ModelPart[2];
    private final ModelPart[] headString = new ModelPart[3];
    /** Parts that may drop out for a glitch frame (never the trunk). */
    private final ModelPart[] loose;

    public BurattinaioModel(BbModel bb) {
        super(bb.root, RenderTypes::entityCutout);
        this.idle = bb.animation("idle");
        this.walk = bb.animation("walk");
        this.attack = bb.animation("attack1");
        this.death = bb.animation("death");
        this.body = bb.bone("body");
        this.chest = bb.bone("chest");
        this.head = bb.bone("head");
        this.mask = bb.bone("mask");
        List<ModelPart> loose = new ArrayList<>();
        String[] sides = {"right", "left"};
        for (int i = 0; i < 2; i++) {
            String s = sides[i];
            this.arm[i] = bb.bone(s + "arm");
            this.arm2[i] = bb.bone(s + "arm2");
            this.hand[i] = bb.bone(s + "hand");
            this.control[i] = bb.bone(s + "control");
            loose.add(this.control[i]);
            loose.add(bb.bone(s + "doll"));
            loose.add(bb.bone(s + "string1"));
            loose.add(bb.bone(s + "string2"));
            loose.add(bb.bone(s + "upstring"));
        }
        for (int k = 0; k < 3; k++) {
            this.headString[k] = bb.bone("headstring" + (k + 1));
            loose.add(this.headString[k]);
        }
        for (int k = 1; k <= 10; k++) {
            loose.add(bb.bone("robe" + k));
        }
        this.loose = loose.toArray(new ModelPart[0]);
    }

    private static float ease(float t, float a, float b) {
        float k = Mth.clamp((t - a) / (b - a), 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    private static float hold(float t, float a, float b, float c, float d) {
        return ease(t, a, b) * (1.0F - ease(t, c, d));
    }

    private static void rot(ModelPart p, float x, float y, float z) {
        p.xRot -= x * D;
        p.yRot -= y * D;
        p.zRot += z * D;
    }

    static boolean glitchFrame(BurattinaioRenderState s) {
        return RecisioneModel.hash(s.seed, Mth.floor(s.ageInTicks)) < s.glitch;
    }

    @Override
    public void setupAnim(BurattinaioRenderState s) {
        super.setupAnim(s);
        for (ModelPart part : this.allParts()) {
            part.visible = true;
        }
        float age = s.ageInTicks;
        if (s.deathTime > 0.0F) {
            this.death.apply(s.deathTime / 20.0F, 1.0F);
            if (s.deathTime > 80.0F) {
                // falling to dust, piece by piece
                for (int i = 0; i < this.loose.length; i++) {
                    if (s.deathTime > 80.0F + RecisioneModel.hash(s.seed + 31, i) * 14.0F) {
                        this.loose[i].visible = false;
                    }
                }
            }
            return;
        }
        float t = s.actionTime;
        int action = s.action;
        float busy = switch (action) {
            case Burattinaio.NONE -> 0.0F;
            case Burattinaio.CONTROL -> 1.0F;
            default -> hold(t, 0.0F, 6.0F, Burattinaio.duration(action) - 8.0F, Burattinaio.duration(action));
        };
        float walking = Mth.clamp(s.walkAnimationSpeed * 4.0F, 0.0F, 1.0F) * (1.0F - busy);
        this.idle.apply(age / 20.0F + (s.seed & 0xFF) * 0.37F, 1.0F - 0.6F * walking - 0.5F * busy);
        this.walk.apply(s.walkAnimationPos / WALK_UNITS * this.walk.length, walking);
        switch (action) {
            case Burattinaio.CONTROL -> this.attack.apply(t / 20.0F, 1.0F);
            case Burattinaio.DESCENT -> this.descent(t, age);
            case Burattinaio.RAIN -> this.rain(t, age);
            case Burattinaio.DARK -> this.dark(t, age);
            case Burattinaio.ARRIVE -> this.arrive(t, age);
            default -> {
            }
        }
        // it turns its head towards whoever it looks at, with a puppet's jerks
        float look = 1.0F - 0.6F * busy;
        float jerk = Mth.floor(age / 7.0F) * 7.0F == Mth.floor(age) ? 1.0F : 0.0F;
        this.head.yRot += Mth.clamp(s.yRot, -55.0F, 55.0F) * 0.65F * D * look;
        this.head.xRot += Mth.clamp(s.xRot, -35.0F, 35.0F) * 0.55F * D * look;
        this.head.zRot += jerk * (RecisioneModel.hash(s.seed + 9, Mth.floor(age)) - 0.5F) * 10.0F * D;
        if (s.sinceHurt < 6.0F) {
            // hit: the porcelain head snaps back, the arms flinch
            float k = 1.0F - s.sinceHurt / 6.0F;
            rot(this.head, -16.0F * k, 0, 9.0F * k);
            rot(this.chest, -5.0F * k, 0, 0);
            rot(this.arm[0], -10.0F * k, 0, 12.0F * k);
            rot(this.arm[1], -10.0F * k, 0, -12.0F * k);
        }
        if (glitchFrame(s)) {
            int frame = Mth.floor(age);
            this.loose[(int) (RecisioneModel.hash(s.seed + 3, frame) * this.loose.length)].visible = false;
            this.head.zRot += (RecisioneModel.hash(s.seed + 5, frame) - 0.5F) * 80.0F * D;
            this.mask.x += (RecisioneModel.hash(s.seed + 6, frame) - 0.5F) * 6.0F;
        }
    }

    /** Both arms up, then the hands crash down in front of it (tick 20), then it stays low. */
    private void descent(float t, float age) {
        float up = ease(t, 0.0F, 14.0F) * (1.0F - ease(t, 16.0F, 20.0F));
        float down = ease(t, 16.0F, 20.0F) * (1.0F - ease(t, 34.0F, 44.0F));
        for (int i = 0; i < 2; i++) {
            float sx = i == 0 ? 1.0F : -1.0F;
            rot(this.arm[i], 150.0F * up + 70.0F * down, 0, sx * (-10.0F * up - 30.0F * down));
            rot(this.arm2[i], -30.0F * up + 10.0F * down, 0, 0);
            rot(this.hand[i], -20.0F * up + 40.0F * down, 0, 0);
            rot(this.control[i], 30.0F * down, 0, 0);
        }
        rot(this.chest, -10.0F * up + 30.0F * down, 0, 0);
        rot(this.head, -20.0F * up + 25.0F * down, 0, 0);
    }

    /** Both arms raised to the dark: the threads fall from up there. */
    private void rain(float t, float age) {
        float k = hold(t, 0.0F, 10.0F, 44.0F, 56.0F);
        float shake = Mth.sin(age * 2.7F) * 3.0F * hold(t, 8.0F, 12.0F, 38.0F, 44.0F);
        for (int i = 0; i < 2; i++) {
            float sx = i == 0 ? 1.0F : -1.0F;
            rot(this.arm[i], 40.0F * k + shake, 0, sx * 100.0F * k);
            rot(this.arm2[i], 0, 0, sx * 20.0F * k);
            rot(this.control[i], -40.0F * k + shake * 2.0F, 0, 0);
        }
        rot(this.chest, -8.0F * k, 0, 0);
        rot(this.head, -40.0F * k, 0, 0);
    }

    /** Arms out, everything trembling, while the lights go out. */
    private void dark(float t, float age) {
        float k = hold(t, 0.0F, 6.0F, 58.0F, 70.0F);
        float tremble = Mth.sin(age * 3.3F) * 4.0F * k;
        for (int i = 0; i < 2; i++) {
            float sx = i == 0 ? 1.0F : -1.0F;
            rot(this.arm[i], tremble, 0, sx * 60.0F * k + tremble);
        }
        rot(this.head, 25.0F * k + tremble, 0, -20.0F * k);
    }

    /** Lowered down from the dark on its own strings: limp, head hanging, then it wakes. */
    private void arrive(float t, float age) {
        float limp = 1.0F - ease(t, 60.0F, 90.0F);
        for (int i = 0; i < 2; i++) {
            float sx = i == 0 ? 1.0F : -1.0F;
            rot(this.arm[i], 10.0F * limp, 0, -sx * 34.0F * limp);
            rot(this.arm2[i], 20.0F * limp, 0, 0);
            rot(this.hand[i], 30.0F * limp, 0, 0);
        }
        rot(this.head, 40.0F * limp, 0, 12.0F * limp);
        rot(this.chest, 12.0F * limp, 0, 0);
        if (t > 60.0F && t < 64.0F) {
            // it wakes with a jerk
            rot(this.head, -30.0F, 0, 0);
        }
    }
}
