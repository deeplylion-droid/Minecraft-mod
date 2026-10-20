package com.disabilities.mixin;

import com.disabilities.condition.Condition;
import com.disabilities.condition.ModData;
import com.disabilities.condition.Picks;
import com.disabilities.server.Chooser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs on both sides (the conditions are synced), so prediction and server agree. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    /** Wheelchair: no ladders, vines or scaffolding. */
    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void disabilities$noClimbing(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && ModData.has((Player) (Object) this, Condition.WHEELCHAIR)) {
            cir.setReturnValue(false);
        }
    }

    /** Narcolepsy: lying on the ground while asleep. */
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void disabilities$asleep(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (ModData.asleep(self) && !self.isPassenger() && !self.isSpectator()) {
            self.setPose(Pose.SLEEPING);
            ci.cancel();
        }
    }

    /** Badges after the name: in chat, above the head, in death messages. */
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void disabilities$badges(CallbackInfoReturnable<Component> cir) {
        Picks picks = ModData.picks((Player) (Object) this);
        if (picks.isEmpty()) {
            return;
        }
        StringBuilder glyphs = new StringBuilder();
        for (Condition c : picks.list()) {
            glyphs.append(c.glyph());
        }
        cir.setReturnValue(cir.getReturnValue().copy().append(Component.literal(" ")).append(Chooser.badge(glyphs.toString())));
    }

    /** Obesity: twice as hungry. */
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float disabilities$hungry(float amount) {
        return ModData.has((Player) (Object) this, Condition.OBESITY) ? amount * 2.0F : amount;
    }
}
