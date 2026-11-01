package com.punizioni.client.mixin;

import com.punizioni.client.render.Poses;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The poses of the punished (and the parts the BodyLayer draws instead), after vanilla's. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void punizioni$pose(AvatarRenderState state, CallbackInfo ci) {
        Poses.apply((PlayerModel) (Object) this, state);
    }
}
