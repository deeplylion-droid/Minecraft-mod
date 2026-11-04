package com.disabilities.client;

import com.disabilities.Disabilities;
import com.disabilities.Sided;
import com.disabilities.client.hud.Overlays;
import com.disabilities.client.render.Accessories;
import com.disabilities.client.render.ConditionLayer;
import com.disabilities.client.render.FirstPersonAids;
import com.disabilities.client.render.PaleSkins;
import com.disabilities.client.screen.ChooserScreen;
import com.disabilities.client.screen.GestureScreen;
import com.disabilities.condition.Condition;
import com.disabilities.condition.ModData;
import com.disabilities.gesture.Gesture;
import com.disabilities.net.EpisodeKind;
import com.disabilities.net.Net;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public class DisabilitiesClient implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Disabilities.id("main"));
    public static final KeyMapping GESTURES = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.disabilities.gestures", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, CATEGORY));

    /** The chooser the server asked for, opened as soon as nothing else is on screen. */
    private static int pendingChooser = -1;
    /** Last known conditions of each player, to refresh their size when they change. */
    private static final Map<Integer, Integer> SEEN = new HashMap<>();

    @Override
    public void onInitializeClient() {
        Sided.sonar = player -> Fx.sonar();

        ClientPlayNetworking.registerGlobalReceiver(Net.OpenChooser.TYPE, (payload, context) -> {
            if (context.client().gui.screen() instanceof ChooserScreen chooser) {
                chooser.update(payload.taken());
            } else {
                pendingChooser = payload.taken();
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.ShowGesture.TYPE, (payload, context) -> {
            Gesture gesture = Gesture.byOrdinal(payload.gesture());
            if (gesture != null) {
                Bubbles.gesture(payload.entityId(), gesture);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.Bubble.TYPE, (payload, context) ->
                Bubbles.show(payload.entityId(), payload.text(), payload.ticks()));
        ClientPlayNetworking.registerGlobalReceiver(Net.Episode.TYPE, (payload, context) ->
                Fx.onEpisode(EpisodeKind.byOrdinal(payload.kind()), payload.arg()));
        ClientPlayNetworking.registerGlobalReceiver(Net.Roster.TYPE, (payload, context) -> ClientHooks.setRoster(payload.masks()));
        ClientPlayNetworking.registerGlobalReceiver(Net.Motion.TYPE, (payload, context) ->
                Motions.start(payload.entityId(), com.disabilities.net.MotionKind.byOrdinal(payload.kind()), payload.arg()));
        ClientPlayNetworking.registerGlobalReceiver(Net.Heart.TYPE, (payload, context) -> Fx.setBpm(payload.bpm()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            pendingChooser = -1;
            SEEN.clear();
            Bubbles.clear();
            Motions.clear();
            Fx.reset();
            ClientHooks.setRoster(Map.of());
        });

        ClientTickEvents.END_CLIENT_TICK.register(DisabilitiesClient::tick);

        // mute: no chat, no talking commands
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (Me.has(Condition.MUTE)) {
                muteHint();
                // the others see you try
                ClientPlayNetworking.send(Net.TriedToSpeak.INSTANCE);
                return false;
            }
            return true;
        });
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            String root = command.split(" ", 2)[0].toLowerCase(java.util.Locale.ROOT);
            if (Me.has(Condition.MUTE) && java.util.Set.of("msg", "tell", "w", "me", "say", "teammsg", "tm").contains(root)) {
                muteHint();
                return false;
            }
            return true;
        });

        // amnesia: no recipe book button
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof AbstractRecipeBookScreen<?> && Me.has(Condition.AMNESIA)) {
                Screens.getWidgets(screen).removeIf(w -> w instanceof ImageButton);
            }
        });

        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(new ConditionLayer(avatar));
            }
        });
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Accessories.ID, Accessories.INSTANCE);
        FirstPersonAids.init();
    }

    private static void muteHint() {
        Minecraft.getInstance().gui.hud.setOverlayMessage(Component.translatable("disabilities.msg.mute").withStyle(ChatFormatting.RED), false);
    }

    private static int bumpCooldown;
    private static double lastVy;

    private static void tick(Minecraft mc) {
        Fx.tick(mc);
        Overlays.tick(mc);
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        bumps(player);
        // the chooser cannot be avoided: it comes back whenever no other screen is open
        if (!ModData.picks(player).settled()) {
            if (pendingChooser >= 0 && mc.gui.screen() == null) {
                mc.gui.setScreen(new ChooserScreen(pendingChooser));
            }
        } else {
            pendingChooser = -1;
        }
        // players whose conditions changed get their size recomputed (wheelchair)
        for (Player p : mc.level.players()) {
            int mask = ModData.picks(p).mask();
            Integer seen = SEEN.put(p.getId(), mask);
            if (seen == null || seen != mask) {
                p.refreshDimensions();
            }
        }
        while (GESTURES.consumeClick()) {
            if (Me.has(Condition.MUTE)) {
                mc.gui.setScreen(new GestureScreen());
            } else {
                mc.gui.hud.setOverlayMessage(Component.translatable("disabilities.msg.not_mute").withStyle(ChatFormatting.GRAY), false);
            }
        }
        if (Me.has(Condition.AMNESIA) && mc.debugEntries.isOverlayVisible()) {
            mc.debugEntries.setOverlayVisible(false);
        }
        if (mc.level.getGameTime() % 1200 == 0) {
            SEEN.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        }
    }

    /** Blind without the cane walking into a wall, a giant hitting the ceiling: the server hurts and shows it. */
    private static void bumps(LocalPlayer player) {
        if (bumpCooldown > 0) {
            bumpCooldown--;
        }
        double vy = player.getDeltaMovement().y;
        if (bumpCooldown == 0 && !Me.lying()) {
            if (Me.has(Condition.GIGANTISM) && player.verticalCollision && !player.onGround() && lastVy > 0.05) {
                bumpCooldown = 20;
                ClientPlayNetworking.send(new Net.Bump(1));
            } else if (Me.has(Condition.BLIND) && player.horizontalCollision && player.input.getMoveVector().y > 0.5F
                    && !player.getMainHandItem().is(com.disabilities.item.ModItems.WHITE_CANE)
                    && !player.getOffhandItem().is(com.disabilities.item.ModItems.WHITE_CANE)) {
                bumpCooldown = 40;
                ClientPlayNetworking.send(new Net.Bump(0));
            }
        }
        lastVy = vy;
    }

    static void clearSkins() {
        PaleSkins.clear();
    }
}
