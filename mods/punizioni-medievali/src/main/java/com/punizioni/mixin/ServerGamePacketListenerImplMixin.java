package com.punizioni.mixin;

import com.punizioni.server.Curses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A balloon floating over the ground is not flying: no "Flying is not enabled" kick. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "getMaximumFlyingTicks", at = @At("RETURN"), cancellable = true)
    private void punizioni$balloon(Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (entity == this.player && Curses.floats(this.player)) {
            cir.setReturnValue(Integer.MAX_VALUE);
        }
    }
}
