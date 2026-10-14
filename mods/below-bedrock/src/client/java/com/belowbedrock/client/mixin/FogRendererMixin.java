package com.belowbedrock.client.mixin;

import com.belowbedrock.client.ScreenFx;
import com.belowbedrock.world.Faded;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The faded world's haze: closer than the render distance, and it closes in when He is near. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private static void belowbedrock$haze(Camera camera, FogRenderer.FogMode mode, float renderDistance, boolean thick,
                                          float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !Faded.is(minecraft.level) || camera.getFluidInCamera() != FogType.NONE) {
            return;
        }
        if (camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return;
        }
        float end = ScreenFx.fogEnd(renderDistance, partialTick);
        RenderSystem.setShaderFogStart(mode == FogRenderer.FogMode.FOG_SKY ? 0.0F : end * 0.06F);
        RenderSystem.setShaderFogEnd(end);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
}
