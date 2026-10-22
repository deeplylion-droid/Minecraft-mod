package com.filorosso.client.mixin;

import com.filorosso.client.Me;
import com.filorosso.client.Puppet;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    /** On the ground you can only crawl: no jumping, no sprinting. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void filorosso$downed(CallbackInfo ci) {
        if (Me.player() != null && Puppet.active()) {
            // a puppet of the Burattinaio: the strings walk you, not the keys
            this.keyPresses = Input.EMPTY;
            this.moveVector = Vec2.ZERO;
            return;
        }
        if (Me.player() != null && Me.downed()) {
            Input k = this.keyPresses;
            this.keyPresses = new Input(k.forward(), k.backward(), k.left(), k.right(), false, k.shift(), false);
        }
    }
}
