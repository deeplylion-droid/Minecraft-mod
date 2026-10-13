package com.rainbowyoyo.client;

import com.rainbowyoyo.RainbowYoyoMod;
import com.rainbowyoyo.client.render.YoyoRenderer;
import com.rainbowyoyo.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client setup on the mod bus. */
@Mod.EventBusSubscriber(modid = RainbowYoyoMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.YOYO.get(), YoyoRenderer::new);
    }

    private ClientSetup() {
    }
}
