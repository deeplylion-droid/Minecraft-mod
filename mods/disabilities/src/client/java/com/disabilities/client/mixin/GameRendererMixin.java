package com.disabilities.client.mixin;

import com.disabilities.Disabilities;
import com.disabilities.client.Fx;
import com.disabilities.client.Me;
import com.disabilities.condition.Condition;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Unique
    private static final Identifier DISABILITIES_MYOPIA = Disabilities.id("myopia");
    @Unique
    private static final Identifier DISABILITIES_TEARS = Disabilities.id("tears");
    @Unique
    private static final Identifier DISABILITIES_GRAYSCALE = Disabilities.id("grayscale");
    @Unique
    private static boolean disabilities$grayscaleFailed;

    @Shadow
    @Final
    private RenderTarget mainRenderTarget;
    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;
    @Shadow
    @Final
    private Minecraft minecraft;

    /** World post effects: short-sighted blur, tears. */
    @Inject(method = "update", at = @At("TAIL"))
    private void disabilities$postEffects(DeltaTracker deltaTracker, CallbackInfo ci) {
        GameRenderer self = (GameRenderer) (Object) this;
        if (Me.has(Condition.MYOPIA)) {
            self.getRequestedPostEffects().add(DISABILITIES_MYOPIA);
        }
        if (Fx.tears(0.0F) > 0.3F) {
            self.getRequestedPostEffects().add(DISABILITIES_TEARS);
        }
    }

    /** Achromatopsia: black and white over everything, inventory included (after the GUI is drawn). */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V", shift = At.Shift.AFTER))
    private void disabilities$grayscale(CallbackInfo ci) {
        if (disabilities$grayscaleFailed || !Me.has(Condition.ACHROMATOPSIA)) {
            return;
        }
        try {
            PostChain chain = this.minecraft.getShaderManager().getPostChain(DISABILITIES_GRAYSCALE, LevelTargetBundle.MAIN_TARGETS);
            if (chain != null) {
                chain.process(this.mainRenderTarget, this.resourcePool);
            }
        } catch (RuntimeException e) {
            disabilities$grayscaleFailed = true;
            Disabilities.LOGGER.error("Grayscale post effect failed", e);
        }
    }

    /** Camera roll: vertigo, stumbles, sneezes. */
    @Inject(method = "bobHurt", at = @At("HEAD"))
    private void disabilities$roll(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        float roll = Fx.roll(cameraState.cameraEntityPartialTicks);
        if (roll != 0.0F) {
            poseStack.rotateDegrees(Axis.ZP, roll);
        }
    }
}
