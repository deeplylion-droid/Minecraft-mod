package com.disabilities.client.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/** How the accessories move: wheels spin with the distance travelled, crutches swing with the gait. */
final class Animations {
    private Animations() {
    }

    static AccessoryModel attach(String name, AccessoryModel model) {
        return switch (name) {
            case Accessories.WHEELCHAIR -> model.animate(Animations::wheelchair);
            case Accessories.CRUTCHES -> model.animate(Animations::crutches);
            default -> model;
        };
    }

    private static void wheelchair(AccessoryModel model, AvatarRenderState state) {
        float spin = -state.walkAnimationPos * 0.55F;
        rotX(model.part("wheel_l"), spin);
        rotX(model.part("wheel_r"), spin);
        rotX(model.part("caster_l"), spin * 3.0F);
        rotX(model.part("caster_r"), spin * 3.0F);
    }

    private static void crutches(AccessoryModel model, AvatarRenderState state) {
        float swing = Poses.crutchSwing(state);
        ModelPart left = model.part("crutch_l");
        ModelPart right = model.part("crutch_r");
        if (left != null) {
            left.xRot = -swing - 0.08F;
            left.zRot = 0.07F;
        }
        if (right != null) {
            right.xRot = -swing - 0.08F;
            right.zRot = -0.07F;
        }
    }

    private static void rotX(ModelPart part, float angle) {
        if (part != null) {
            part.xRot = angle;
        }
    }
}
