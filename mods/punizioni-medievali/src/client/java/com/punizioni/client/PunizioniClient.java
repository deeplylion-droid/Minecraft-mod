package com.punizioni.client;

import com.punizioni.ModParticles;
import com.punizioni.Punizioni;
import com.punizioni.client.bonds.BondsLayer;
import com.punizioni.client.bonds.Captives;
import com.punizioni.client.fx.Disgusts;
import com.punizioni.client.fx.Flies;
import com.punizioni.client.fx.Gibs;
import com.punizioni.client.fx.ScreenFx;
import com.punizioni.client.fx.VictimCamera;
import com.punizioni.client.particle.AshParticle;
import com.punizioni.client.particle.BloodParticle;
import com.punizioni.client.particle.CrackParticle;
import com.punizioni.client.particle.DungParticle;
import com.punizioni.client.particle.EmberParticle;
import com.punizioni.client.particle.FlyParticle;
import com.punizioni.client.outfit.DungLayer;
import com.punizioni.client.outfit.OutfitsLayer;
import com.punizioni.client.outfit.Sway;
import com.punizioni.client.particle.SparkleParticle;
import com.punizioni.client.render.BodyLayer;
import com.punizioni.client.render.MachineRenderer;
import com.punizioni.client.render.PropRenderer;
import com.punizioni.client.render.MarkedSkins;
import com.punizioni.client.render.RatRenderer;
import com.punizioni.client.royal.Capes;
import com.punizioni.client.royal.Guilty;
import com.punizioni.client.royal.Lashes;
import com.punizioni.client.royal.RoyalLayer;
import com.punizioni.inspect.Inspection;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import com.punizioni.entity.ModEntities;
import com.punizioni.item.ModItems;
import com.punizioni.machine.Machine;
import com.punizioni.machine.MachineKind;
import com.punizioni.net.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.HorseRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.HashSet;
import java.util.Set;

public class PunizioniClient implements ClientModInitializer {
    /** Items with a description under their name (lang: item.punizioni.<id>.desc1/2). */
    private static final Set<Item> DESCRIBED = new HashSet<>();
    private static boolean wasHeld;

    @Override
    public void onInitializeClient() {
        for (MachineKind kind : MachineKind.ALL) {
            register(kind);
        }
        EntityRendererRegistry.register(ModEntities.DRAFT_HORSE, HorseRenderer::new);
        EntityRendererRegistry.register(ModEntities.RAT, RatRenderer::new);
        EntityRendererRegistry.register(ModEntities.POOP, ThrownItemRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROAST_PIG, context -> new PropRenderer<>(context, "roast_pig", false));
        EntityRendererRegistry.register(ModEntities.GRUEL_POT, context -> new PropRenderer<>(context, "gruel_pot", true));

        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(new BodyLayer(avatar, context.getResourceManager()));
                helper.register(new RoyalLayer(avatar, context.getResourceManager()));
                helper.register(new BondsLayer(avatar, context.getResourceManager()));
                helper.register(new OutfitsLayer(avatar, context.getResourceManager()));
                helper.register(new DungLayer(avatar));
            }
        });
        ParticleProviderRegistry.getInstance().register(ModParticles.BLOOD, BloodParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(ModParticles.EMBER, EmberParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(ModParticles.ASH, AshParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(ModParticles.SPARKLE, SparkleParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(ModParticles.CRACK, CrackParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(ModParticles.DUNG, DungParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(ModParticles.FLY, FlyParticle.Provider::new);
        MenuScreens.register(Inspection.MENU, ContainerScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(Net.Guilty.TYPE, (payload, context) -> Guilty.receive(payload.ids()));

        ClientPlayNetworking.registerGlobalReceiver(Net.VictimFx.TYPE, (payload, context) -> ScreenFx.hit(payload.kind(), payload.strength()));
        ClientPlayNetworking.registerGlobalReceiver(Net.Disgust.TYPE, (payload, context) -> Disgusts.received(payload.entity()));
        ClientPlayNetworking.registerGlobalReceiver(Net.ChopFx.TYPE, (payload, context) -> {
            if (context.client().level != null) {
                Gibs.chop(context.client().level, payload.machine(), payload.victim(), payload.limb());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.TearFx.TYPE, (payload, context) -> {
            if (context.client().level != null) {
                Gibs.tear(context.client().level, payload.machine(), payload.victim());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.HeadFx.TYPE, (payload, context) -> {
            Minecraft mc = context.client();
            if (mc.player != null && mc.level != null && payload.victim() == mc.player.getId()
                    && mc.level.getEntity(payload.machine()) instanceof Machine machine) {
                VictimCamera.headOff(machine);
                ScreenFx.beheaded();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ScreenFx.tick(mc);
            Gibs.tick(mc);
            VictimCamera.tick(mc);
            Lashes.tick(mc);
            Capes.tick(mc);
            Captives.tick(mc);
            Flies.tick(mc);
            Sway.endFrame();
            // vanilla says "press shift to get off": not from a machine
            boolean held = mc.player != null && mc.player.getVehicle() instanceof Machine;
            if (held && !wasHeld) {
                mc.gui.hud.setOverlayMessage(Component.translatable("punizioni.msg.captured").withStyle(ChatFormatting.DARK_RED), false);
            }
            wasHeld = held;
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(Gibs::submit);
        LevelRenderEvents.COLLECT_SUBMITS.register(Captives::submit);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ScreenFx.reset();
            Gibs.reset();
            VictimCamera.reset();
            MarkedSkins.clear();
            Lashes.reset();
            Capes.reset();
            Captives.reset();
            Guilty.reset();
            Flies.reset();
            Sway.reset();
            Disgusts.reset();
        });

        DESCRIBED.add(ModItems.EXECUTIONER_HOOD);
        DESCRIBED.add(ModItems.EXECUTIONER_APRON);
        DESCRIBED.add(ModItems.EXECUTIONER_BREECHES);
        DESCRIBED.add(ModItems.EXECUTIONER_BOOTS);
        DESCRIBED.add(ModItems.CAT_O_NINE_TAILS);
        DESCRIBED.add(ModItems.EXECUTIONER_AXE);
        DESCRIBED.add(ModItems.EXECUTIONER_MALLET);
        DESCRIBED.add(ModItems.EXECUTIONER_TORCH);
        DESCRIBED.addAll(ModItems.MACHINES.values());
        DESCRIBED.add(ModItems.ROYAL_CROWN);
        DESCRIBED.add(ModItems.ROYAL_CAPE);
        DESCRIBED.add(ModItems.ROYAL_LENS);
        DESCRIBED.add(ModItems.OVERSEER_WHIP);
        DESCRIBED.add(ModItems.PRISONER_ROPE);
        DESCRIBED.add(ModItems.JESTER_HAT);
        DESCRIBED.add(ModItems.JESTER_TUNIC);
        DESCRIBED.add(ModItems.JESTER_HOSE);
        DESCRIBED.add(ModItems.JESTER_SHOES);
        DESCRIBED.add(ModItems.PEASANT_HOOD);
        DESCRIBED.add(ModItems.PEASANT_TUNIC);
        DESCRIBED.add(ModItems.PEASANT_BREECHES);
        DESCRIBED.add(ModItems.PEASANT_WRAPS);
        DESCRIBED.add(ModItems.POOP);
        DESCRIBED.add(ModItems.ROAST_PIG);
        DESCRIBED.add(ModItems.ROAST_LEG);
        DESCRIBED.add(ModItems.ROAST_RIBS);
        DESCRIBED.add(ModItems.ROAST_HAM);
        DESCRIBED.add(ModItems.GRUEL_POT);
        DESCRIBED.add(ModItems.GRUEL);
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, flag, lines) -> {
            if (DESCRIBED.contains(stack.getItem())) {
                String base = "item." + Punizioni.MOD_ID + "." + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + ".desc";
                lines.add(Component.translatable(base + "1").withStyle(ChatFormatting.DARK_RED));
                lines.add(Component.translatable(base + "2").withStyle(ChatFormatting.GRAY));
            }
        });
    }

    private static <T extends Machine> void register(MachineKind kind) {
        @SuppressWarnings("unchecked")
        net.minecraft.world.entity.EntityType<T> type = (net.minecraft.world.entity.EntityType<T>) kind.type();
        EntityRendererRegistry.register(type, context -> new MachineRenderer<T>(context, kind));
    }
}
