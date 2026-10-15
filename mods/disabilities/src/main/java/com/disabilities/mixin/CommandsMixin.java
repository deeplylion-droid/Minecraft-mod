package com.disabilities.mixin;

import com.disabilities.server.Speech;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class CommandsMixin {
    /** Mute: /msg, /tell, /w, /me, /say, /teammsg are speech too. */
    @Inject(method = "performPrefixedCommand", at = @At("HEAD"), cancellable = true)
    private void disabilities$mute(CommandSourceStack source, String command, CallbackInfo ci) {
        if (Speech.blocks(source, command)) {
            ci.cancel();
        }
    }
}
