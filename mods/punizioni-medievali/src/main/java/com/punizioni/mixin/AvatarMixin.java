package com.punizioni.mixin;

import com.punizioni.data.Curse;
import com.punizioni.data.ModData;
import com.punizioni.witch.CurseShapes;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The cursed have another body: a toad's, a balloon's, or one whose eyes are in the backside. */
@Mixin(Avatar.class)
public abstract class AvatarMixin {
    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    private void punizioni$cursed(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if ((pose == Pose.STANDING || pose == Pose.CROUCHING) && (Object) this instanceof Player player) {
            Curse c = ModData.curse(player);
            if (c != null && c.type() != null) {
                cir.setReturnValue(CurseShapes.dimensions(c.type(), pose == Pose.CROUCHING));
            }
        }
    }
}
