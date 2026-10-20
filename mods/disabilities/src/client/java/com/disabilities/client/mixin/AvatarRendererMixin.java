package com.disabilities.client.mixin;

import com.disabilities.client.ClientHooks;
import com.disabilities.client.render.Visuals;
import com.disabilities.condition.Condition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void disabilities$extract(Avatar entity, AvatarRenderState state, float partial, CallbackInfo ci) {
        ClientHooks.extract(entity, state, partial);
    }

    private static boolean disabilities$special(AvatarRenderState s) {
        return s.isPassenger || s.isFallFlying || s.swimAmount > 0.0F || s.pose == Pose.SLEEPING || s.isAutoSpinAttack;
    }

    /** Wheelchair: the body sits 4 pixels lower. Asleep on the floor: lifted so it does not sink into it. */
    @Inject(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true)
    private void disabilities$offset(AvatarRenderState state, CallbackInfoReturnable<Vec3> cir) {
        Visuals v = state.getData(Visuals.KEY);
        if (v == null) {
            return;
        }
        if (state.pose == Pose.SLEEPING && state.bedOrientation == null) {
            cir.setReturnValue(cir.getReturnValue().add(0.0, 0.2 * state.scale, 0.0));
        } else if (v.has(Condition.WHEELCHAIR) && !disabilities$special(state)) {
            cir.setReturnValue(cir.getReturnValue().add(0.0, -4.0 / 16.0 * 0.9375 * state.scale, 0.0));
        }
    }

    /** Vertigo: the whole body sways. */
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL"))
    private void disabilities$sway(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        Visuals v = state.getData(Visuals.KEY);
        if (v != null && v.has(Condition.VERTIGO) && !disabilities$special(state)) {
            poseStack.rotateDegrees(Axis.ZP, Mth.sin(v.time * 0.08F) * 4.0F + Mth.sin(v.time * 0.19F + 1.0F) * 1.5F);
        }
    }

    /** Speech bubbles (tics, gestures, "Zzz", "?") one line above the name. */
    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("TAIL"))
    private void disabilities$bubble(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
                                     CallbackInfo ci) {
        Visuals v = state.getData(Visuals.KEY);
        if (v == null || v.bubble == null || state.isInvisible) {
            return;
        }
        Vec3 attachment = state.nameTagAttachment != null ? state.nameTagAttachment : new Vec3(0.0, state.boundingBoxHeight, 0.0);
        int lines = (state.nameTag != null ? 1 : 0) + (state.scoreText != null ? 1 : 0);
        poseStack.pushPose();
        poseStack.translate(0.0F, lines * 9.0F * 1.15F * 0.025F + 0.03F, 0.0F);
        collector.submitNameTag(poseStack, attachment, 0, v.bubble, !state.isDiscrete, state.lightCoords, camera);
        poseStack.popPose();
    }
}
