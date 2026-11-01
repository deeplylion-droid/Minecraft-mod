package com.punizioni.client;

import com.punizioni.ModParticles;
import com.punizioni.Punizioni;
import com.punizioni.client.fx.Gibs;
import com.punizioni.client.fx.ScreenFx;
import com.punizioni.client.fx.VictimCamera;
import com.punizioni.client.particle.AshParticle;
import com.punizioni.client.particle.BloodParticle;
import com.punizioni.client.particle.EmberParticle;
import com.punizioni.client.render.BodyLayer;
import com.punizioni.client.render.MachineRenderer;
import com.punizioni.client.render.MarkedSkins;
import com.punizioni.client.render.RatRenderer;
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

        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(new BodyLayer(avatar, context.getResourceManager()));
            }
        });
        ParticleProviderRegistry.getInstance().register(ModParticles.BLOOD, BloodParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(ModParticles.EMBER, EmberParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(ModParticles.ASH, AshParticle.Provider::new);

        ClientPlayNetworking.registerGlobalReceiver(Net.VictimFx.TYPE, (payload, context) -> ScreenFx.hit(payload.kind(), payload.strength()));
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
            // vanilla says "press shift to get off": not from a machine
            boolean held = mc.player != null && mc.player.getVehicle() instanceof Machine;
            if (held && !wasHeld) {
                mc.gui.hud.setOverlayMessage(Component.translatable("punizioni.msg.captured").withStyle(ChatFormatting.DARK_RED), false);
            }
            wasHeld = held;
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(Gibs::submit);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ScreenFx.reset();
            Gibs.reset();
            VictimCamera.reset();
            MarkedSkins.clear();
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
