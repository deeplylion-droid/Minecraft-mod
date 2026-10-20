package com.frusta.client;

import com.frusta.FrustaMod;
import com.frusta.client.hud.WhipHud;
import com.frusta.client.render.KneelLayer;
import com.frusta.client.render.WhipAnchorLayer;
import com.frusta.registry.ModEnchantments;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client setup on the mod bus: layers on the players, the HUD, the Sottomissione book's look. */
@Mod.EventBusSubscriber(modid = FrustaMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            EntityRenderer<? extends Player> renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer player) {
                player.addLayer(new WhipAnchorLayer(player));
                player.addLayer(new KneelLayer(player));
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "whip_charge", WhipHud::charge);
        event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "kneel_vignette", WhipHud::kneel);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Enchanted books with Sottomissione get their own cover (see the overrides of enchanted_book.json).
        event.enqueueWork(() -> ItemProperties.register(Items.ENCHANTED_BOOK, FrustaMod.id("sottomissione"),
                (stack, level, entity, seed) ->
                        EnchantmentHelper.getEnchantments(stack).containsKey(ModEnchantments.SOTTOMISSIONE.get()) ? 1.0F : 0.0F));
    }

    private ClientSetup() {
    }
}
