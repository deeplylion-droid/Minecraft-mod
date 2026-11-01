package com.punizioni.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.punizioni.client.render.FirstPersonArm;
import com.punizioni.client.render.Poses;
import com.punizioni.client.render.Visuals;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players in the machines and after them: what is drawn (Visuals), their poses and the first-person stump. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void punizioni$extract(Avatar entity, AvatarRenderState state, float partial, CallbackInfo ci) {
        Visuals.extract(entity, state, partial);
    }

    /** Kneeling and sitting at the block: lower; stretched by the horses: on longer legs. */
    @Inject(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true)
    private void punizioni$offset(AvatarRenderState state, CallbackInfoReturnable<Vec3> cir) {
        Vec3 offset = Poses.offset(state);
        if (offset != Vec3.ZERO) {
            cir.setReturnValue(cir.getReturnValue().add(offset));
        }
    }

    /** Lying on the rack and on the table, face down under the blade, turning with the wheel, swinging from the rope. */
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL"))
    private void punizioni$rotations(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        Poses.rotations(state, poseStack);
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void punizioni$handBefore(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skin, ModelPart arm, boolean sleeve,
                                      CallbackInfo ci) {
        FirstPersonArm.before((AvatarRenderer<?>) (Object) this, arm);
    }

    @Inject(method = "renderHand", at = @At("TAIL"))
    private void punizioni$handAfter(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skin, ModelPart arm, boolean sleeve,
                                     CallbackInfo ci) {
        FirstPersonArm.after((AvatarRenderer<?>) (Object) this, poseStack, collector, light, skin, arm);
    }
}
