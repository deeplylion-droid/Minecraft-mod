package com.disabilities.client.mixin;

import com.disabilities.client.Me;
import com.disabilities.condition.Condition;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    /** Amnesia: you do not remember any recipe (the book stays closed; its button is removed in DisabilitiesClient). */
    @Inject(method = "isVisibleAccordingToBookData", at = @At("HEAD"), cancellable = true)
    private void disabilities$closed(CallbackInfoReturnable<Boolean> cir) {
        if (Me.has(Condition.AMNESIA)) {
            cir.setReturnValue(false);
        }
    }
}
