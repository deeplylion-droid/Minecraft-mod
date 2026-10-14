package com.jumpscares.client.scare;

import com.jumpscares.scare.ScareKind;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Moves the parts of a monster (jaw, arms, legs, hair) for one frame. */
public final class ScareAnimator {
    private static final float[] JAW = {32.0F, 18.0F, 0.0F, 34.0F, 38.0F};

    private ScareAnimator() {
    }

    private static void rot(ModelPart part, float x, float y, float z) {
        if (part != null) {
            part.xRot += x * Mth.DEG_TO_RAD;
            part.yRot += y * Mth.DEG_TO_RAD;
            part.zRot += z * Mth.DEG_TO_RAD;
        }
    }

    public static void animate(ScareKind kind, ScareModel model, ScarePose p, float t) {
        model.reset();
        ModelPart jaw = model.part("jaw");
        rot(jaw, JAW[kind.ordinal()] * p.jaw, 0.0F, 0.0F);
        if (kind == ScareKind.LADY && jaw != null) {
            jaw.y += 12.0F * p.jaw; // the jaw unhinges and slides down, opening a black hole
        }
        float grab = p.reach * 6.0F * Mth.sin(t * 1.9F);
        rot(model.part("arm_l"), -38.0F * p.reach + grab, 26.0F * p.reach, 0.0F);
        rot(model.part("arm_r"), -38.0F * p.reach - grab, -26.0F * p.reach, 0.0F);
        switch (kind) {
            case CLOWN -> rot(model.part("hat"), 0.0F, 0.0F, 9.0F * Mth.sin(t * 1.1F));
            case LADY -> {
                for (int i = 1; i <= 3; i++) {
                    rot(model.part("strand_" + i), 3.0F * Mth.sin(t * 0.31F + i), 0.0F, 4.0F * Mth.sin(t * 0.37F + i * 2.0F));
                }
                rot(model.part("hair_l"), 0.0F, 0.0F, 3.0F * Mth.sin(t * 0.3F));
                rot(model.part("hair_r"), 0.0F, 0.0F, -3.0F * Mth.sin(t * 0.33F));
            }
            case SPIDER -> {
                rot(model.part("fang_l"), 0.0F, 0.0F, 26.0F * p.jaw);
                rot(model.part("fang_r"), 0.0F, 0.0F, -26.0F * p.jaw);
                rot(model.part("palp_l"), -20.0F * p.jaw, 0.0F, 0.0F);
                rot(model.part("palp_r"), -20.0F * p.jaw, 0.0F, 0.0F);
                for (int i = 1; i <= 4; i++) {
                    for (int side = -1; side <= 1; side += 2) {
                        String name = "leg_" + (side < 0 ? "l" : "r") + i;
                        float w = p.legs * Mth.sin(t * 2.6F + i * 1.3F + (side > 0 ? 1.7F : 0.0F));
                        float w2 = p.legs * Mth.sin(t * 3.1F + i * 0.9F + (side > 0 ? 0.6F : 0.0F));
                        rot(model.part(name), 0.0F, 12.0F * w2 * -side, 22.0F * w * -side);
                        rot(model.part(name + "_b"), 0.0F, 0.0F, 18.0F * w2 * -side);
                    }
                }
            }
            default -> {
            }
        }
    }
}
