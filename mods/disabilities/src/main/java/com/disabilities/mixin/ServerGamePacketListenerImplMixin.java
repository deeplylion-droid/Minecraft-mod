package com.disabilities.mixin;

import com.disabilities.condition.Condition;
import com.disabilities.condition.ModData;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    /** One arm: no swapping into the off hand. */
    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void disabilities$noOffhand(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && this.player != null && ModData.has(this.player, Condition.ONE_ARM)) {
            ci.cancel();
        }
    }
}
