package com.wurstel.client;

import com.wurstel.WurstelMod;
import com.wurstel.client.anim.WurstelAnimManager;
import com.wurstel.client.render.WurstelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client game events: animation ticks and the first-person würstel. */
@Mod.EventBusSubscriber(modid = WurstelMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) {
            WurstelAnimManager.tick(mc.level);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            WurstelRenderer.renderFirstPerson(event);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WurstelAnimManager.clear();
    }

    private ClientEvents() {
    }
}
