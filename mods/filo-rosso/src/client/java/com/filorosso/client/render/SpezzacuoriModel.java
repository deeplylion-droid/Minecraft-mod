package com.filorosso.client.render;

import com.filorosso.entity.Spezzacuori;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Lo Spezzacuori's body, posed by hand every frame: hunched, breathing, a beating broken heart,
 * long arms that swing when he walks, and one pose per attack (smash, pound, throw, grab,
 * scream, rising from the ground, falling on his knees when he dies).
 */
public class SpezzacuoriModel extends EntityModel<SpezzacuoriRenderState> {
    private static final float D = Mth.DEG_TO_RAD;

    private final ModelPart hips;
    private final ModelPart flap;
    private final ModelPart torso;
    private final ModelPart heart;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart hair;
    private final ModelPart[] arm = new ModelPart[2];
    private final ModelPart[] forearm = new ModelPart[2];
    private final ModelPart[] hand = new ModelPart[2];
    private final ModelPart[] fingers = new ModelPart[2];
    private final ModelPart[] leg = new ModelPart[2];
    private final ModelPart[] shin = new ModelPart[2];
    private final ModelPart[] foot = new ModelPart[2];

    public SpezzacuoriModel(CubeModels.Baked baked) {
        super(baked.root(), RenderTypes::entityCutout);
        this.hips = baked.part("hips");
        this.flap = baked.part("flap");
        this.torso = baked.part("torso");
        this.heart = baked.part("heart");
        this.neck = baked.part("neck");
        this.head = baked.part("head");
        this.jaw = baked.part("jaw");
        this.hair = baked.part("hair");
        String[] sides = {"r", "l"};
        for (int i = 0; i < 2; i++) {
            this.arm[i] = baked.part("arm_" + sides[i]);
            this.forearm[i] = baked.part("forearm_" + sides[i]);
            this.hand[i] = baked.part("hand_" + sides[i]);
            this.fingers[i] = baked.part("fingers_" + sides[i]);
            this.leg[i] = baked.part("leg_" + sides[i]);
            this.shin[i] = baked.part("shin_" + sides[i]);
            this.foot[i] = baked.part("foot_" + sides[i]);
        }
    }

    /** 0 before a, 1 after b, smooth in between. */
    private static float ease(float t, float a, float b) {
        float k = Mth.clamp((t - a) / (b - a), 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    /** Goes 0 -> 1 between a and b, stays, then 1 -> 0 between c and d. */
    private static float hold(float t, float a, float b, float c, float d) {
        return ease(t, a, b) * (1.0F - ease(t, c, d));
    }

    @Override
    public void setupAnim(SpezzacuoriRenderState s) {
        super.setupAnim(s);
        float age = s.ageInTicks;
        float walk = s.walkAnimationPos * 0.42F;
        float speed = Math.min(1.0F, s.walkAnimationSpeed * 1.4F);
        float side = 1.0F;

        // --- at rest: hunched over, breathing, arms hanging down to the knees
        float breath = Mth.sin(age * 0.07F);
        this.torso.xRot = (22.0F + breath * 2.0F + speed * 6.0F) * D;
        this.neck.xRot = -10.0F * D;
        this.head.xRot = -16.0F * D + Mth.clamp(s.xRot, -40.0F, 40.0F) * 0.6F * D;
        this.head.yRot = Mth.clamp(s.yRot, -60.0F, 60.0F) * 0.7F * D;
        this.head.zRot = Mth.sin(age * 0.031F) * 4.0F * D;
        this.jaw.xRot = (5.0F + Math.max(0.0F, breath) * 3.0F) * D;
        this.hair.xRot = (6.0F + speed * 10.0F) * D;
        for (int i = 0; i < 2; i++) {
            side = i == 0 ? 1.0F : -1.0F;
            float swing = Mth.cos(walk + (i == 0 ? 0.0F : Mth.PI)) * 0.55F * speed;
            this.arm[i].xRot = -24.0F * D - swing + Mth.sin(age * 0.05F + i) * 2.0F * D;
            this.arm[i].zRot = side * (7.0F + Math.max(0.0F, breath) * 2.0F) * D;
            this.forearm[i].xRot = -16.0F * D - Math.max(0.0F, -swing) * 0.4F;
            this.fingers[i].xRot = (-14.0F + Mth.sin(age * 0.09F + i * 2.0F) * 6.0F) * D;
            float step = Mth.cos(walk + (i == 0 ? Mth.PI : 0.0F));
            this.leg[i].xRot = step * 0.6F * speed;
            this.shin[i].xRot = Math.max(0.0F, Mth.sin(walk + (i == 0 ? Mth.PI : 0.0F))) * 0.75F * speed;
            this.foot[i].xRot = -this.shin[i].xRot * 0.5F;
        }
        this.hips.y += Math.abs(Mth.sin(walk)) * 2.2F * speed;
        this.torso.yRot = Mth.sin(walk) * 0.08F * speed;
        this.flap.xRot = -Math.abs(Mth.sin(walk)) * 0.4F * speed;

        // the broken heart beats (faster when he is angry)
        float rate = s.raging ? 0.55F : 0.32F;
        float phase = (age * rate) % (Mth.PI * 2.0F);
        float beat = Math.max(0.0F, Mth.sin(phase * 2.0F)) * (phase < Mth.PI ? 1.0F : 0.0F);
        float hs = 1.0F + beat * 0.16F;
        this.heart.xScale = hs;
        this.heart.yScale = hs;
        this.heart.zScale = hs;

        float t = s.actionTime;
        switch (s.action) {
            case Spezzacuori.SLAM -> this.slam(t);
            case Spezzacuori.POUND -> this.pound(t);
            case Spezzacuori.THROW -> this.throwScissors(t);
            case Spezzacuori.GRAB -> this.grab(t);
            case Spezzacuori.SCREAM -> this.scream(t, age);
            case Spezzacuori.RISE -> this.rise(t);
            default -> {
            }
        }
        if (s.deathTime > 0.0F) {
            this.die(s.deathTime);
        }
    }

    /** Right arm up over the head, then down like a hammer (hit at tick 12). */
    private void slam(float t) {
        float up = ease(t, 0.0F, 10.0F) * (1.0F - ease(t, 10.0F, 12.5F));
        float down = ease(t, 10.0F, 12.5F) * (1.0F - ease(t, 16.0F, 26.0F));
        this.arm[0].xRot = Mth.lerp(up, this.arm[0].xRot, -168.0F * D);
        this.arm[0].xRot = Mth.lerp(down, this.arm[0].xRot, -62.0F * D);
        this.forearm[0].xRot = Mth.lerp(up, this.forearm[0].xRot, -40.0F * D);
        this.forearm[0].xRot = Mth.lerp(down, this.forearm[0].xRot, -6.0F * D);
        this.torso.xRot += (-12.0F * up + 22.0F * down) * D;
        this.torso.yRot += (12.0F * up - 8.0F * down) * D;
        this.jaw.xRot += 18.0F * down * D;
        this.fingers[0].xRot += -30.0F * down * D;
    }

    /** Both fists up, then on the ground: a shock wave (hit at tick 20). */
    private void pound(float t) {
        float up = ease(t, 0.0F, 15.0F) * (1.0F - ease(t, 15.0F, 20.0F));
        float down = ease(t, 15.0F, 20.0F) * (1.0F - ease(t, 28.0F, 40.0F));
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? 1.0F : -1.0F;
            this.arm[i].xRot = Mth.lerp(up, this.arm[i].xRot, -172.0F * D);
            this.arm[i].xRot = Mth.lerp(down, this.arm[i].xRot, -78.0F * D);
            this.arm[i].zRot = Mth.lerp(up, this.arm[i].zRot, -side * 14.0F * D);
            this.forearm[i].xRot = Mth.lerp(up, this.forearm[i].xRot, -30.0F * D);
            this.forearm[i].xRot = Mth.lerp(down, this.forearm[i].xRot, -20.0F * D);
            this.fingers[i].xRot += -40.0F * Math.max(up, down) * D;
        }
        this.crouch(-26.0F * down, 40.0F * down, 0.0F);
        this.torso.xRot += (-20.0F * up + 30.0F * down) * D;
        this.head.xRot += (-14.0F * up + 10.0F * down) * D;
        this.jaw.xRot += 30.0F * down * D;
    }

    /** Arm back, then a whip forward: the scissors fly (released at tick 14). */
    private void throwScissors(float t) {
        float back = ease(t, 0.0F, 10.0F) * (1.0F - ease(t, 10.0F, 14.0F));
        float fwd = ease(t, 10.0F, 14.0F) * (1.0F - ease(t, 18.0F, 30.0F));
        this.arm[0].xRot = Mth.lerp(back, this.arm[0].xRot, 70.0F * D);
        this.arm[0].zRot = Mth.lerp(back, this.arm[0].zRot, 30.0F * D);
        this.arm[0].xRot = Mth.lerp(fwd, this.arm[0].xRot, -120.0F * D);
        this.forearm[0].xRot = Mth.lerp(back, this.forearm[0].xRot, -60.0F * D);
        this.forearm[0].xRot = Mth.lerp(fwd, this.forearm[0].xRot, -10.0F * D);
        this.fingers[0].xRot += 25.0F * fwd * D;
        this.torso.yRot += (24.0F * back - 26.0F * fwd) * D;
        this.torso.xRot += (-6.0F * back + 10.0F * fwd) * D;
    }

    /** Both hands forward, the blades close, then a fling to the side (tick 12). */
    private void grab(float t) {
        float reach = ease(t, 0.0F, 10.0F) * (1.0F - ease(t, 20.0F, 34.0F));
        float close = ease(t, 10.0F, 13.0F) * (1.0F - ease(t, 20.0F, 30.0F));
        float fling = hold(t, 12.0F, 18.0F, 22.0F, 34.0F);
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? 1.0F : -1.0F;
            this.arm[i].xRot = Mth.lerp(reach, this.arm[i].xRot, -82.0F * D);
            this.arm[i].zRot = Mth.lerp(reach, this.arm[i].zRot, -side * 10.0F * D);
            this.forearm[i].xRot = Mth.lerp(reach, this.forearm[i].xRot, -8.0F * D);
            this.fingers[i].xRot = Mth.lerp(reach, this.fingers[i].xRot, 30.0F * D);
            this.fingers[i].xRot = Mth.lerp(close, this.fingers[i].xRot, -70.0F * D);
        }
        this.torso.xRot += 10.0F * reach * D;
        this.torso.yRot += 40.0F * fling * D;
        this.jaw.xRot += 20.0F * reach * D;
    }

    /** Head thrown back, arms open wide, mouth wide open (tick 10). */
    private void scream(float t, float age) {
        float k = hold(t, 0.0F, 9.0F, 40.0F, 50.0F);
        float shake = Mth.sin(age * 2.7F) * 3.0F * k * D;
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? 1.0F : -1.0F;
            this.arm[i].xRot = Mth.lerp(k, this.arm[i].xRot, -40.0F * D);
            this.arm[i].zRot = Mth.lerp(k, this.arm[i].zRot, side * 78.0F * D);
            this.forearm[i].xRot = Mth.lerp(k, this.forearm[i].xRot, -30.0F * D);
            this.fingers[i].xRot = Mth.lerp(k, this.fingers[i].xRot, 25.0F * D);
        }
        this.torso.xRot = Mth.lerp(k, this.torso.xRot, 2.0F * D);
        this.head.xRot = Mth.lerp(k, this.head.xRot, -42.0F * D) + shake;
        this.head.zRot += shake;
        this.jaw.xRot = Mth.lerp(k, this.jaw.xRot, 48.0F * D);
        this.hair.xRot += 25.0F * k * D;
    }

    /** Clawing his way out of the ground (the renderer lifts him). */
    private void rise(float t) {
        float up = 1.0F - ease(t, 30.0F, 60.0F);
        float claw = Mth.sin(t * 0.35F);
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? 1.0F : -1.0F;
            this.arm[i].xRot = Mth.lerp(up, this.arm[i].xRot, (-160.0F + claw * side * 12.0F) * D);
            this.arm[i].zRot = Mth.lerp(up, this.arm[i].zRot, -side * 12.0F * D);
            this.fingers[i].xRot = Mth.lerp(up, this.fingers[i].xRot, (-40.0F + claw * 20.0F) * D);
        }
        this.head.xRot += -20.0F * up * D;
        this.jaw.xRot += 30.0F * up * D;
    }

    /**
     * Bends both legs (degrees: thigh forward, knee, ankle) and lowers the body by as much as the
     * legs got shorter, so the feet (or the knees) stay on the ground.
     */
    private void crouch(float thigh, float knee, float ankle) {
        float a = thigh * D;
        float b = (thigh + knee) * D;
        float drop = 22.0F * (1.0F - Mth.cos(a)) + 17.0F * (1.0F - Mth.cos(b)) + 4.0F * Math.max(0.0F, Mth.sin(b)) * 0.5F;
        for (int i = 0; i < 2; i++) {
            this.leg[i].xRot = thigh * D;
            this.shin[i].xRot = knee * D;
            this.foot[i].xRot = ankle * D;
            this.leg[i].y += drop;
        }
        this.hips.y += drop;
    }

    /** On his knees, then face down; the heart stops. */
    private void die(float deathTime) {
        float knees = ease(deathTime, 0.0F, 22.0F);
        float fall = ease(deathTime, 26.0F, 60.0F);
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? 1.0F : -1.0F;
            this.arm[i].xRot = Mth.lerp(knees, this.arm[i].xRot, -10.0F * D);
            this.arm[i].xRot = Mth.lerp(fall, this.arm[i].xRot, -150.0F * D);
            this.arm[i].zRot = Mth.lerp(fall, this.arm[i].zRot, side * 20.0F * D);
        }
        this.crouch(-10.0F * knees, 95.0F * knees, -30.0F * knees);
        this.torso.xRot = Mth.lerp(knees, this.torso.xRot, 35.0F * D);
        this.torso.xRot = Mth.lerp(fall, this.torso.xRot, 88.0F * D);
        this.head.xRot = Mth.lerp(knees, this.head.xRot, 30.0F * D);
        this.jaw.xRot = Mth.lerp(knees, this.jaw.xRot, 35.0F * D);
        float hs = Mth.lerp(ease(deathTime, 10.0F, 50.0F), this.heart.xScale, 0.5F);
        this.heart.xScale = hs;
        this.heart.yScale = hs;
        this.heart.zScale = hs;
    }
}
