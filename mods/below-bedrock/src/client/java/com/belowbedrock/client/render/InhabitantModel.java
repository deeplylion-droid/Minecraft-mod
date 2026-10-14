package com.belowbedrock.client.render;

import com.belowbedrock.entity.Inhabitant;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** A player model, but wrong: arms too long, no idle sway, the head tilted, arms out when he runs. */
public class InhabitantModel extends PlayerModel<Inhabitant> {
    private static final float ARM_LENGTH = 1.3F;

    public InhabitantModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(Inhabitant entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        this.rightArm.yScale = ARM_LENGTH;
        this.leftArm.yScale = ARM_LENGTH;
        this.rightArm.zRot = 0.05F;
        this.leftArm.zRot = -0.05F;
        if (entity.getMode() == Inhabitant.Mode.CHASE) {
            this.rightArm.xRot = -1.4F + Mth.cos(ageInTicks * 0.9F) * 0.12F;
            this.leftArm.xRot = -1.4F - Mth.cos(ageInTicks * 0.9F) * 0.12F;
        } else if (!entity.getMainHandItem().isEmpty()) {
            this.rightArm.xRot = -0.9F; // holding something out for you
        }
        float tilt = entity.getTilt();
        this.head.zRot = tilt;
        this.hat.zRot = tilt;
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
    }
}
