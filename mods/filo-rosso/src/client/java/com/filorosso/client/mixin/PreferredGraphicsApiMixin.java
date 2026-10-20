package com.filorosso.client.mixin;

import com.filorosso.client.GraphicsFallback;
import com.mojang.renderpearl.api.device.GpuBackend;
import com.mojang.renderpearl.backend.opengl.GlBackend;
import com.mojang.renderpearl.backend.vulkan.VulkanBackend;
import net.minecraft.client.PreferredGraphicsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * On Windows "Default" tries Vulkan first (some NVIDIA drivers crash the game while it loads in
 * OpenGL), unless Vulkan is what crashed the last start. An API chosen in the video settings keeps
 * its vanilla order. Every start writes down the API it tries first (see {@link GraphicsFallback}).
 */
@Mixin(PreferredGraphicsApi.class)
public abstract class PreferredGraphicsApiMixin {
    @Inject(method = "getBackendsToTry", at = @At("HEAD"), cancellable = true)
    private void filorosso$vulkanFirst(CallbackInfoReturnable<GpuBackend[]> cir) {
        if (!GraphicsFallback.enabled()) {
            return;
        }
        PreferredGraphicsApi self = (PreferredGraphicsApi) (Object) this;
        boolean vulkanFirst = switch (self) {
            case VULKAN -> true;
            case OPENGL -> false;
            default -> !GraphicsFallback.VULKAN.equals(GraphicsFallback.crashedWith());
        };
        GraphicsFallback.trying(vulkanFirst ? GraphicsFallback.VULKAN : GraphicsFallback.OPENGL);
        cir.setReturnValue(vulkanFirst ? new GpuBackend[]{new VulkanBackend(), new GlBackend()} : new GpuBackend[]{new GlBackend(), new VulkanBackend()});
    }
}
