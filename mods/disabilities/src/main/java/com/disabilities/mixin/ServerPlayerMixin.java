package com.disabilities.mixin;

import com.disabilities.server.Ailments;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    /** Jumps cost breath (asthma) and stamina (obesity). */
    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void disabilities$jump(CallbackInfo ci) {
        Ailments.onJump((ServerPlayer) (Object) this);
    }
}
