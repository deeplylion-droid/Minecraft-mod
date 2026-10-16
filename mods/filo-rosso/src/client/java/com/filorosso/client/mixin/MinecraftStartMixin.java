package com.filorosso.client.mixin;

import com.filorosso.client.GraphicsFallback;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.PreferredGraphicsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * After a crash while loading, vanilla switches "Default" to OpenGL for good. On Windows that is
 * only right if Vulkan is what crashed; if it was OpenGL (or we do not know), keep "Default", which
 * tries Vulkan first.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftStartMixin {
    @WrapWithCondition(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;set(Ljava/lang/Object;)V"))
    private boolean filorosso$keepDefault(OptionInstance<?> option, Object value) {
        return !(value == PreferredGraphicsApi.OPENGL && GraphicsFallback.enabled()
                && !GraphicsFallback.VULKAN.equals(GraphicsFallback.crashedWith()));
    }
}
