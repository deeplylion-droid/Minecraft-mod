package com.filorosso.client.mixin;

import com.mojang.renderpearl.api.device.GpuBackend;
import com.mojang.renderpearl.backend.opengl.GlBackend;
import com.mojang.renderpearl.backend.vulkan.VulkanBackend;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * On Windows the "Default" graphics API tries Vulkan first (then OpenGL, as vanilla does when Vulkan
 * is missing): some NVIDIA drivers crash the game while it loads in OpenGL. An explicit choice in
 * the video settings is always respected, and after a crash at startup vanilla switches to OpenGL.
 */
@Mixin(PreferredGraphicsApi.class)
public abstract class PreferredGraphicsApiMixin {
    @Inject(method = "getBackendsToTry", at = @At("HEAD"), cancellable = true)
    private void filorosso$vulkanFirst(CallbackInfoReturnable<GpuBackend[]> cir) {
        if ((Object) this == PreferredGraphicsApi.DEFAULT && Util.getPlatform() == Util.OS.WINDOWS) {
            cir.setReturnValue(new GpuBackend[]{new VulkanBackend(), new GlBackend()});
        }
    }
}
