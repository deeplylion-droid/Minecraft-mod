package com.disabilities.client.mixin;

import com.disabilities.client.Fx;
import com.disabilities.client.Me;
import com.disabilities.condition.Condition;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    /** Asleep or frozen: no walking. Tics: a sudden crouch. Vertigo: you drift sideways while walking. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void disabilities$input(CallbackInfo ci) {
        if (Me.player() == null) {
            return;
        }
        if (Me.frozen()) {
            this.moveVector = Vec2.ZERO;
            this.keyPresses = Input.EMPTY;
            return;
        }
        if (Fx.forcedCrouch()) {
            Input k = this.keyPresses;
            this.keyPresses = new Input(k.forward(), k.backward(), k.left(), k.right(), k.jump(), true, false);
        }
        if (Me.has(Condition.VERTIGO) && this.moveVector.y > 0.1F) {
            float t = Fx.time(0.0F);
            float drift = Mth.sin(t * 0.06F) * 0.3F + Mth.sin(t * 0.17F + 2.0F) * 0.12F;
            this.moveVector = new Vec2(Mth.clamp(this.moveVector.x + drift, -1.0F, 1.0F), this.moveVector.y).normalized();
        }
    }
}
