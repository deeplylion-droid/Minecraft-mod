package com.punizioni.mixin;

import com.punizioni.data.ModData;
import com.punizioni.machine.Machine;
import com.punizioni.server.Deaths;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No getting out of a machine by crouching; no drops for a punishment's death; crawling without feet. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void punizioni$held(CallbackInfoReturnable<Boolean> cir) {
        if (((Player) (Object) this).getVehicle() instanceof Machine) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void punizioni$keepItems(ServerLevel level, CallbackInfo ci) {
        if (Deaths.keeps((Player) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "getBaseExperienceReward", at = @At("RETURN"), cancellable = true)
    private void punizioni$keepXp(ServerLevel level, CallbackInfoReturnable<Integer> cir) {
        if (Deaths.keeps((Player) (Object) this)) {
            cir.setReturnValue(0);
        }
    }

    /** Without feet you crawl (the swimming pose on land, like in a one-block gap). */
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void punizioni$crawl(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (ModData.body(self).feetMissing() == 2 && !self.isPassenger() && !self.isFallFlying() && !self.isSleeping() && !self.isSpectator()
                && !self.getAbilities().flying) {
            self.setPose(Pose.SWIMMING);
            ci.cancel();
        }
    }
}
