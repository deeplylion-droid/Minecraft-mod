package com.filorosso.mixin;

import com.filorosso.data.Link;
import com.filorosso.data.ModData;
import com.filorosso.util.Text;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs on both sides (the link and the status are synced). */
@Mixin(Player.class)
public abstract class PlayerMixin {
    /** The couple's heart after the name: above the head, in chat, in death messages. */
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void filorosso$heart(CallbackInfoReturnable<Component> cir) {
        Link link = ModData.link((Player) (Object) this);
        if (link.bound()) {
            cir.setReturnValue(cir.getReturnValue().copy().append(Component.literal(" ")).append(Text.heart(link.color())));
        }
    }

    /** Downed: lying on the ground, crawling. */
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void filorosso$downed(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (ModData.downed(self) && !self.isPassenger() && !self.isSpectator()) {
            self.setPose(Pose.SWIMMING);
            ci.cancel();
        }
    }
}
