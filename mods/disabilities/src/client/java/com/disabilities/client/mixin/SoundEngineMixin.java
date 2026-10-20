package com.disabilities.client.mixin;

import com.disabilities.client.Fx;
import com.disabilities.client.Me;
import com.disabilities.condition.Condition;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    /** Deaf: no sound at all (and no subtitles). Blind: sounds show where they come from. */
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At("HEAD"), cancellable = true)
    private void disabilities$play(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (Me.has(Condition.DEAF)) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
            return;
        }
        Fx.onSound(sound);
    }

    /** Silences what was already playing (music, loops) when deafness starts. */
    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"), cancellable = true)
    private void disabilities$volume(float volume, SoundSource source, CallbackInfoReturnable<Float> cir) {
        if (Me.has(Condition.DEAF)) {
            cir.setReturnValue(0.0F);
        }
    }
}
