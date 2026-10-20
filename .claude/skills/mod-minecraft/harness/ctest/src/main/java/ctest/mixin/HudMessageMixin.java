package ctest.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test only: logs the action bar messages (couple hits, hints). */
@Mixin(Hud.class)
public class HudMessageMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void ctest$overlay(Component message, boolean animate, CallbackInfo ci) {
        System.out.println("[ctest] overlay " + message.getString());
    }
}
