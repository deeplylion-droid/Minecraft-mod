package com.punizioni.client.mixin;

import com.punizioni.client.fx.ScreenFx;
import com.punizioni.client.fx.VictimCamera;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The victim's eyes where the machine puts the head (VictimCamera), and the field of view of the pain. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private @Nullable Entity entity;
    @Shadow
    private boolean detached;
    @Shadow
    private float xRot;
    @Shadow
    private float yRot;
    @Shadow
    @Final
    private Quaternionf rotation;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;
    @Shadow
    private int matrixPropertiesDirty;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void punizioni$victim(float partial, CallbackInfo ci) {
        if (this.detached || this.entity == null) {
            return;
        }
        VictimCamera.View view = VictimCamera.view(this.entity, partial);
        if (view == null) {
            return;
        }
        this.setPosition(view.position());
        this.rotation.set(view.rotation());
        this.forwards.set(0.0F, 0.0F, -1.0F).rotate(this.rotation);
        this.up.set(0.0F, 1.0F, 0.0F).rotate(this.rotation);
        this.left.set(-1.0F, 0.0F, 0.0F).rotate(this.rotation);
        this.yRot = (float) (Mth.atan2(-this.forwards.x, this.forwards.z) * Mth.RAD_TO_DEG);
        this.xRot = (float) (Math.asin(Mth.clamp(-this.forwards.y, -1.0F, 1.0F)) * Mth.RAD_TO_DEG);
        this.matrixPropertiesDirty |= 3;
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void punizioni$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        float k = ScreenFx.fov(partialTicks);
        if (k != 1.0F) {
            cir.setReturnValue(cir.getReturnValueF() * k);
        }
    }
}
