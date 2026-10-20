package com.chiappe.client.mixin;

import com.chiappe.client.Cheeks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player in the inventory gets smaller cheeks, or at level 25 the little window would be all cheek. */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("HEAD"))
    private static void chiappe$start(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int size, float offsetY,
                                      float mouseX, float mouseY, LivingEntity entity, CallbackInfo ci) {
        Cheeks.inGui = true;
    }

    @Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("RETURN"))
    private static void chiappe$end(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int size, float offsetY,
                                    float mouseX, float mouseY, LivingEntity entity, CallbackInfo ci) {
        Cheeks.inGui = false;
    }
}
