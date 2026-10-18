package com.chiappe.client;

import com.chiappe.Chiappe;
import com.chiappe.ModParticles;
import com.chiappe.net.Net;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;

public class ChiappeClient implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Chiappe.id("main"));
    // Z: free in vanilla 26.3 (G is the quick actions now) and in Iris, Simple Voice Chat and the other mods of the group
    public static final KeyMapping FART = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.chiappe.fart", InputConstants.Type.KEYBOARD, InputConstants.KEY_Z, CATEGORY));

    /** Ticks after joining when the key hint is shown (-1: shown). */
    private static int hintIn = -1;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Net.FartFx.TYPE, (payload, context) ->
                FartFx.play(context.client(), payload.entity(), payload.level()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> hintIn = 60);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            hintIn = -1;
            FartFx.reset();
            Shake.reset();
        });
        ClientTickEvents.END_CLIENT_TICK.register(ChiappeClient::tick);

        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(new ButtLayer(avatar));
            }
        });
        ParticleProviderRegistry.getInstance().register(ModParticles.GAS, GasParticle.Provider::new);
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Chiappe.id("fart_cooldown"), CooldownHud::extract);
    }

    private static void tick(Minecraft mc) {
        Cheeks.tick(mc);
        if (mc.player == null || mc.level == null) {
            return;
        }
        while (FART.consumeClick()) {
            if (FartFx.cooldown(mc) <= 0.0F && !mc.player.isSpectator()) {
                ClientPlayNetworking.send(Net.Fart.INSTANCE);
            }
        }
        if (hintIn > 0 && --hintIn == 0) {
            hintIn = -1;
            mc.player.sendSystemMessage(Component.translatable("chiappe.hint", FART.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
