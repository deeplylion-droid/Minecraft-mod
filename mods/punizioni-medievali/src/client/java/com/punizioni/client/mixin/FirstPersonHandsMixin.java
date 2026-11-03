package com.punizioni.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.punizioni.client.fx.VictimCamera;
import com.punizioni.client.render.FirstPersonArm;
import com.punizioni.client.royal.FirstPersonLash;
import com.punizioni.data.ModData;
import com.punizioni.item.ModItems;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
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
 * item); chained in a machine, seeing from a head in a basket, or turned into a toad, there are no
 * hands in view at all.
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
        FirstPersonLash.begin(poseStack);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (mc.player.getVehicle() instanceof Machine || VictimCamera.sinceHead(partialTicks) >= 0.0F
                || ModData.isBound(mc.player) || ModData.cursed(mc.player, com.punizioni.witch.CurseKind.TOAD))) {
            ci.cancel();
        }
    }

    /** The overseer's whip: its lash from the tip of the handle vanilla is about to draw. */
    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void punizioni$lash(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
                                InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
                                SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (itemStack.is(ModItems.OVERSEER_WHIP) && playerState.avatarRenderState != null) {
            HumanoidArm main = playerState.avatarRenderState.mainArm;
            FirstPersonLash.draw(poseStack, collector, hand == InteractionHand.MAIN_HAND ? main : main.getOpposite(), light, partialTicks);
        }
    }
}
