package com.jumpscares.client;

import com.jumpscares.client.scare.ScareClient;
import com.jumpscares.client.scare.ScareModels;
import com.jumpscares.client.scare.ScareRenderer;
import com.jumpscares.net.ScarePayload;
import com.jumpscares.net.WitnessPayload;
import com.jumpscares.registry.ModEntities;
import com.jumpscares.scare.ScareKind;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.server.packs.PackType;

public class JumpscaresClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.SCARE_PEARL, ThrownItemRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(ScarePayload.TYPE,
                (payload, context) -> ScareClient.start(ScareKind.byId(payload.kind()), payload.seed()));
        ClientPlayNetworking.registerGlobalReceiver(WitnessPayload.TYPE,
                (payload, context) -> ScareClient.witness(payload.victim(), ScareKind.byId(payload.kind()), payload.seed()));
        ClientTickEvents.END_CLIENT_TICK.register(ScareClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ScareClient.reset());
        WorldRenderEvents.AFTER_ENTITIES.register(ScareRenderer::renderWitnesses);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(ScareModels.INSTANCE);
    }
}
