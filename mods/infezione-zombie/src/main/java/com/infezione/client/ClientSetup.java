package com.infezione.client;

import com.infezione.InfezioneMod;
import com.infezione.client.hud.InfectedHearts;
import com.infezione.client.hud.InfectionHud;
import com.infezione.client.particle.BloodParticle;
import com.infezione.client.particle.CoughParticle;
import com.infezione.client.particle.FlyParticle;
import com.infezione.client.render.BoilsLayer;
import com.infezione.client.render.InfectedEyesLayer;
import com.infezione.registry.ModParticles;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client setup on the mod bus: the glowing eyes on the players, the overlays, the particles. */
@Mod.EventBusSubscriber(modid = InfezioneMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            EntityRenderer<? extends Player> renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer player) {
                player.addLayer(new InfectedEyesLayer(player));
                player.addLayer(new BoilsLayer(player));
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("infection_vignette", InfectionHud::vignette);
        event.registerAbove(VanillaGuiOverlay.PLAYER_HEALTH.id(), "infected_hearts", InfectedHearts::render);
        event.registerAboveAll("infection_effects", InfectionHud::effects);
        event.registerAboveAll("infection_panel", InfectionHud::panel);
    }

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.FLY.get(), FlyParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BLOOD.get(), BloodParticle.Provider::new);
        event.registerSpriteSet(ModParticles.PUS.get(), BloodParticle.Provider::new);
        event.registerSpriteSet(ModParticles.COUGH.get(), CoughParticle.Provider::new);
        event.registerSpriteSet(ModParticles.COUGH_BLOOD.get(), CoughParticle.BloodyProvider::new);
    }

    private ClientSetup() {
    }
}
