package com.disabilities.mixin;

import com.disabilities.server.Speech;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class CommandsMixin {
    /** Mute: /msg, /tell, /w, /me, /say, /teammsg are speech too (every command typed by a player passes here). */
    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void disabilities$mute(ParseResults<CommandSourceStack> command, String commandString, CallbackInfo ci) {
        if (Speech.blocks(command.getContext().getSource(), commandString)) {
            ci.cancel();
        }
    }
}
