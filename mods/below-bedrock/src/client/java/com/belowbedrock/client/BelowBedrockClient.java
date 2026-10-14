package com.belowbedrock.client;

import com.belowbedrock.BelowBedrock;
import com.belowbedrock.client.render.InhabitantRenderer;
import com.belowbedrock.net.ScreenFxPayload;
import com.belowbedrock.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public class BelowBedrockClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.INHABITANT, InhabitantRenderer::new);
        DimensionRenderingRegistry.registerDimensionEffects(BelowBedrock.id("faded"), new FadedEffects());
        ClientPlayNetworking.registerGlobalReceiver(ScreenFxPayload.TYPE, (payload, context) -> ScreenFx.receive(payload));
        ClientTickEvents.END_CLIENT_TICK.register(ScreenFx::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ScreenFx.reset());
        HudRenderCallback.EVENT.register(HudFx::render);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return BelowBedrock.id("screen_fx");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                ScreenFx.reload();
            }
        });
    }
}
