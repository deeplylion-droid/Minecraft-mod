package com.disabilities.client.mixin;

import com.disabilities.client.Dyslexia;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Dyslexia: every piece of text drawn, in menus and in the world, goes through here. */
@Mixin(Font.class)
public abstract class FontMixin {
    @ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence disabilities$scramble(FormattedCharSequence text) {
        return Dyslexia.apply(text);
    }

    @ModifyVariable(method = "prepare8xTextOutline(Lnet/minecraft/util/FormattedCharSequence;FFI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence disabilities$scrambleOutline(FormattedCharSequence text) {
        return Dyslexia.apply(text);
    }
}
