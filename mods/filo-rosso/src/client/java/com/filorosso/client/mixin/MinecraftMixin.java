package com.filorosso.client.mixin;

import com.filorosso.client.Me;
import com.filorosso.client.ThreadPhysics;
import com.filorosso.data.ModData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    /** On the ground: no hitting, no mining, no using things (the server agrees). */
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void filorosso$attack(CallbackInfoReturnable<Boolean> cir) {
        if (Me.downed()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void filorosso$mine(boolean down, CallbackInfo ci) {
        if (down && Me.downed()) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void filorosso$use(CallbackInfo ci) {
        if (Me.downed()) {
            ci.cancel();
        }
    }

    /** Your partner shines through walls when they are on the ground, or when jealousy strikes. */
    @Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
    private void filorosso$partner(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !(entity instanceof Player p) || Me.player() == null) {
            return;
        }
        if (Me.link().isPartner(p.getUUID()) && (ModData.downed(p) || ThreadPhysics.jealous)) {
            cir.setReturnValue(true);
        }
    }
}
