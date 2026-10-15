package com.disabilities.mixin;

import com.disabilities.condition.Condition;
import com.disabilities.condition.ModData;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Avatar.class)
public abstract class AvatarMixin {
    /** Wheelchair: a sitting body is lower and the chair is wider. */
    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    private void disabilities$seated(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if ((pose == Pose.STANDING || pose == Pose.CROUCHING) && (Object) this instanceof Player player
                && ModData.has(player, Condition.WHEELCHAIR)) {
            EntityDimensions d = cir.getReturnValue();
            cir.setReturnValue(d.scale(0.75F / d.width(), 1.62F / d.height()).withEyeHeight(1.38F));
        }
    }
}
