package com.punizioni.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.punizioni.client.fx.VictimCamera;
import com.punizioni.client.render.FirstPersonArm;
import com.punizioni.machine.Machine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Your hands in first person: a hand that is gone holds nothing (the stump shows instead of the
 * item); chained in a machine, or seeing from a head in a basket, there are no hands in view at all.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {
    @ModifyExpressionValue(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z", ordinal = 0))
    private boolean punizioni$noHand(boolean empty, @Local(argsOnly = true) InteractionHand hand) {
        return empty || FirstPersonArm.handGone(hand);
    }

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void punizioni$held(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
                                FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (mc.player.getVehicle() instanceof Machine || VictimCamera.sinceHead(partialTicks) >= 0.0F)) {
            ci.cancel();
        }
    }
}
