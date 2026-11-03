package com.punizioni.client.witch;

import com.punizioni.client.render.BbModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The cursed player's toad (geometry/toad.bbmodel), posed from what the player does: it breathes,
 * its throat swells when it croaks, the back legs kick out when it hops and the front ones reach
 * for the landing, the sticky tongue shoots out at every attack, the bulging eyes roll and blink.
 * Turns are written in Blockbench's degrees (the model's own).
 */
public class ToadModel extends Model<ToadModel.Pose> {
    /** What the pose is made of, for one toad, this frame. */
    public record Pose(float time, float walkPos, float walkSpeed, float air, float croak, float swing, float hurt, float headYaw, float headPitch,
                       int seed) {
    }

    /** How far the tongue reaches (px of the model) at full stretch. */
    public static final float TONGUE = 52.0F;

    private final @Nullable ModelPart body, head, jaw, throat, eyeLeft, eyeRight, armLeft, armRight, handLeft, handRight;
    private final @Nullable ModelPart legLeft, legRight, shinLeft, shinRight, footLeft, footRight, tongue, tongueTip;

    public ToadModel(BbModel bb) {
        super(bb.root, RenderTypes::entityCutout);
        this.body = bb.find("body");
        this.head = bb.find("head");
        this.jaw = bb.find("jaw");
        this.throat = bb.find("throat");
        this.eyeLeft = bb.find("eye_left");
        this.eyeRight = bb.find("eye_right");
        this.armLeft = bb.find("arm_left");
        this.armRight = bb.find("arm_right");
        this.handLeft = bb.find("hand_left");
        this.handRight = bb.find("hand_right");
        this.legLeft = bb.find("leg_left");
        this.legRight = bb.find("leg_right");
        this.shinLeft = bb.find("shin_left");
        this.shinRight = bb.find("shin_right");
        this.footLeft = bb.find("foot_left");
        this.footRight = bb.find("foot_right");
        this.tongue = bb.find("tongue");
        this.tongueTip = bb.find("tongue_tip");
    }

    /** Adds a turn in Blockbench's degrees. */
    private static void rot(@Nullable ModelPart p, float x, float y, float z) {
        if (p != null) {
            p.xRot -= x * Mth.DEG_TO_RAD;
            p.yRot -= y * Mth.DEG_TO_RAD;
            p.zRot += z * Mth.DEG_TO_RAD;
        }
    }

    /** Moves by Blockbench's pixels (y up, -z forward). */
    private static void move(@Nullable ModelPart p, float x, float y, float z) {
        if (p != null) {
            p.x -= x;
            p.y -= y;
            p.z += z;
        }
    }

    private static void scale(@Nullable ModelPart p, float x, float y, float z) {
        if (p != null) {
            p.xScale *= x;
            p.yScale *= y;
            p.zScale *= z;
        }
    }

    @Override
    public void setupAnim(Pose p) {
        this.resetPose();
        float t = p.time() + p.seed() % 97;
        float breath = Mth.sin(t * 0.16F);
        float air = p.air();
        float ground = 1.0F - air;

        // breathing: the flanks heave, the throat pulses
        scale(this.body, 1.0F - 0.012F * breath, 1.0F + 0.03F * breath * ground, 1.0F);
        float sac = 1.0F + 0.1F * Math.max(0.0F, Mth.sin(t * 0.42F)) * ground;

        // croaking: the throat blows up like a bubble, the jaw drops, the head tips back
        if (p.croak() >= 0.0F && p.croak() < 18.0F) {
            float c = Mth.sin(p.croak() / 18.0F * Mth.PI);
            float pulse = 1.0F + 0.12F * Mth.sin(p.croak() * 2.4F);
            sac += 1.5F * c * pulse;
            rot(this.jaw, 10.0F * c, 0.0F, 0.0F);
            rot(this.head, -7.0F * c, 0.0F, 0.0F);
            scale(this.body, 1.0F + 0.04F * c, 1.0F + 0.05F * c, 1.0F);
        }
        scale(this.throat, sac, sac, sac);

        // the head follows the eyes a little
        rot(this.head, -p.headPitch() * 0.35F, -p.headYaw() * 0.45F, 0.0F);

        // the eyes roll on their own and blink every few seconds
        rot(this.eyeLeft, 0.0F, 9.0F * Mth.sin(t * 0.051F + 1.0F), 0.0F);
        rot(this.eyeRight, 0.0F, 9.0F * Mth.sin(t * 0.047F), 0.0F);
        float blink = (t + p.seed() % 50) % 83.0F;
        if (blink < 3.0F) {
            float b = 1.0F - Mth.sin(blink / 3.0F * Mth.PI) * 0.85F;
            scale(this.eyeLeft, 1.0F, b, 1.0F);
            scale(this.eyeRight, 1.0F, b, 1.0F);
        }

        // crawling on the ground: the legs step in turn
        float walk = Mth.clamp(p.walkSpeed() * 1.8F, 0.0F, 1.0F) * ground;
        float step = Mth.sin(p.walkPos() * 0.9F);
        rot(this.armLeft, 14.0F * step * walk, 0.0F, 0.0F);
        rot(this.armRight, -14.0F * step * walk, 0.0F, 0.0F);
        rot(this.legLeft, -10.0F * step * walk, 0.0F, 0.0F);
        rot(this.legRight, 10.0F * step * walk, 0.0F, 0.0F);

        // hopping: the back legs kick out behind, the front ones reach forward, the body stretches
        rot(this.legLeft, -48.0F * air, 0.0F, -8.0F * air);
        rot(this.legRight, -48.0F * air, 0.0F, 8.0F * air);
        rot(this.shinLeft, 62.0F * air, 0.0F, 0.0F);
        rot(this.shinRight, 62.0F * air, 0.0F, 0.0F);
        rot(this.footLeft, 30.0F * air, 0.0F, 0.0F);
        rot(this.footRight, 30.0F * air, 0.0F, 0.0F);
        rot(this.armLeft, 42.0F * air, 0.0F, -10.0F * air);
        rot(this.armRight, 42.0F * air, 0.0F, 10.0F * air);
        rot(this.handLeft, -30.0F * air, 0.0F, 0.0F);
        rot(this.handRight, -30.0F * air, 0.0F, 0.0F);
        rot(this.body, -9.0F * air, 0.0F, 0.0F);
        scale(this.body, 1.0F - 0.05F * air, 1.0F - 0.04F * air, 1.0F + 0.1F * air);

        // hurt: a shiver
        if (p.hurt() > 0.0F) {
            float h = p.hurt() / 10.0F;
            rot(this.body, 0.0F, 0.0F, 6.0F * Mth.sin(p.hurt() * 2.6F) * h);
            scale(this.body, 1.0F + 0.06F * h, 1.0F - 0.06F * h, 1.0F + 0.06F * h);
        }

        // the tongue: out and back in one swing
        float out = p.swing() >= 0.0F ? Mth.sin(Mth.clamp(p.swing(), 0.0F, 1.0F) * Mth.PI) : 0.0F;
        if (this.tongue != null) {
            this.tongue.visible = out > 0.02F;
            scale(this.tongue, 1.0F, 1.0F, 1.0F + (TONGUE - 1.0F) * out);
        }
        if (this.tongueTip != null) {
            this.tongueTip.visible = out > 0.02F;
            move(this.tongueTip, 0.0F, 0.0F, -(TONGUE - 1.0F) * out);
        }
        if (out > 0.02F) {
            rot(this.jaw, 18.0F * Math.min(1.0F, out * 3.0F), 0.0F, 0.0F);
        }
    }
}
