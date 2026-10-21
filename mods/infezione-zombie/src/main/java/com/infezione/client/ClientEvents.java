package com.infezione.client;

import com.infezione.InfezioneMod;
import com.infezione.client.fx.BoilsFx;
import com.infezione.client.fx.ScreenShake;
import com.infezione.client.fx.SymptomFx;
import com.infezione.client.hud.InfectedHearts;
import com.infezione.client.hud.InfectionHud;
import com.infezione.client.render.InfectedPoses;
import com.infezione.client.skin.InfectedSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client game events: ticking the effects and the skins, the poses of the players, the hearts, the camera. */
@Mod.EventBusSubscriber(modid = InfezioneMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        InfectedSkins.tick(mc);
        if (!mc.isPaused()) {
            ClientInfections.tick(mc);
            SymptomFx.tick(mc);
            BoilsFx.tick(mc);
            InfectionHud.tick();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        InfectedPoses.onRenderPre(event);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        InfectedPoses.onRenderPost(event);
    }

    /** First person: the boils of the arm. */
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        BoilsFx.onRenderArm(event);
    }

    /** The infected hearts take the place of vanilla's. */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()
                && Minecraft.getInstance().getCameraEntity() instanceof Player player && InfectedHearts.replaces(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        ScreenShake.apply(event);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientInfections.clear();
        InfectedSkins.clear();
        BoilsFx.clear();
        ScreenShake.clear();
    }
}
