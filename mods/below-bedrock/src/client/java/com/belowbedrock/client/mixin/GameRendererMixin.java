package com.belowbedrock.client.mixin;

import com.belowbedrock.client.ScreenFx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Our screen pass runs where vanilla runs its own (spectator) post effects: after the world, before the HUD. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
    private void belowbedrock$screenFx(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        ScreenFx.renderPost(deltaTracker.getGameTimeDeltaPartialTick(false), deltaTracker.getGameTimeDeltaTicks());
    }
}
