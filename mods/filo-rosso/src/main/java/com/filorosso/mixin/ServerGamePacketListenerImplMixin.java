package com.filorosso.mixin;

import com.filorosso.server.Tether;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hanging from the thread is not flying: no "Flying is not enabled" kick. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "getMaximumFlyingTicks", at = @At("RETURN"), cancellable = true)
    private void filorosso$hanging(Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (entity == this.player && Tether.held(this.player)) {
            cir.setReturnValue(Integer.MAX_VALUE);
        }
    }
}
