package com.filorosso.client;

import com.filorosso.FiloRosso;
import com.filorosso.client.hud.CoupleHud;
import com.filorosso.client.render.ServantRenderer;
import com.filorosso.client.render.SpezzacuoriRenderer;
import com.filorosso.client.render.ThreadRenderer;
import com.filorosso.client.screen.BreakUpScreen;
import com.filorosso.client.screen.ChoiceScreen;
import com.filorosso.client.screen.CoupleScreen;
import com.filorosso.client.screen.ProposalScreen;
import com.filorosso.entity.ModEntities;
import com.filorosso.net.Net;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;

public class FiloRossoClient implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(FiloRosso.id("main"));
    // J, Y, U, I: free in vanilla and in the mods people play with (Iris uses R/K/O, Simple Voice Chat V/G/M/N/H)
    public static final KeyMapping MENU = key("menu", InputConstants.KEY_J);
    public static final KeyMapping PULL = key("pull", InputConstants.KEY_Y);
    public static final KeyMapping GIFT = key("gift", InputConstants.KEY_U);
    public static final KeyMapping REACH = key("reach", InputConstants.KEY_I);
    public static final KeyMapping[] ABILITY_KEYS = {PULL, GIFT, REACH};

    /** Screens the server asked for, opened as soon as nothing else is on screen. */
    private static boolean pendingChoice;
    private static final Deque<Net.Proposal> PROPOSALS = new ArrayDeque<>();
    private static String pendingBreakUp;

    private static KeyMapping key(String name, int key) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.filorosso." + name, InputConstants.Type.KEYBOARD, key, CATEGORY));
    }

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Net.Candidates.TYPE, (payload, context) -> {
            ClientState.candidates = payload.list();
            if (context.client().gui.screen() instanceof ChoiceScreen choice) {
                choice.refresh();
            } else if (payload.open()) {
                pendingChoice = true;
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.Proposal.TYPE, (payload, context) -> {
            PROPOSALS.removeIf(p -> p.from().equals(payload.from()));
            PROPOSALS.add(payload);
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.BreakUpAsk.TYPE, (payload, context) -> pendingBreakUp = payload.name());
        ClientPlayNetworking.registerGlobalReceiver(Net.ThreadFx.TYPE, (payload, context) -> ThreadRenderer.onFx(payload));
        ClientPlayNetworking.registerGlobalReceiver(Net.WorldState.TYPE, (payload, context) -> ClientState.world = payload);
        ClientPlayNetworking.registerGlobalReceiver(Net.CoupleInfo.TYPE, (payload, context) -> ClientState.info = payload);
        ClientPlayNetworking.registerGlobalReceiver(Net.Cooldowns.TYPE, (payload, context) -> ClientState.cooldowns = payload.readyAt());
        ClientPlayNetworking.registerGlobalReceiver(Net.Roster.TYPE, (payload, context) -> ClientState.roster = new java.util.HashMap<>(payload.colors()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            pendingChoice = false;
            PROPOSALS.clear();
            pendingBreakUp = null;
            ClientState.reset();
            ThreadRenderer.clear();
            CoupleHud.reset();
        });

        ClientTickEvents.END_CLIENT_TICK.register(FiloRossoClient::tick);
        ThreadRenderer.init();

        EntityRendererRegistry.register(ModEntities.SERVANT, ServantRenderer::new);
        EntityRendererRegistry.register(ModEntities.SPEZZACUORI, SpezzacuoriRenderer::new);
        EntityRendererRegistry.register(ModEntities.SCISSORS, context -> new ThrownItemRenderer<>(context, 2.5F, false));
    }

    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        CoupleHud.tick(mc);
        if (player == null || mc.level == null) {
            return;
        }
        ThreadPhysics.tick(mc);

        while (MENU.consumeClick()) {
            if (Me.link().bound()) {
                mc.gui.setScreen(new CoupleScreen());
            } else {
                ClientPlayNetworking.send(Net.OpenMenu.INSTANCE);
            }
        }
        for (int i = 0; i < ABILITY_KEYS.length; i++) {
            while (ABILITY_KEYS[i].consumeClick()) {
                if (Me.link().bound()) {
                    ClientPlayNetworking.send(new Net.UseAbility(i));
                } else {
                    CoupleHud.hintSingle();
                }
            }
        }

        // what the server asked to show, one at a time and never on top of another screen
        if (mc.gui.screen() == null) {
            if (pendingBreakUp != null) {
                mc.gui.setScreen(new BreakUpScreen(pendingBreakUp));
                pendingBreakUp = null;
            } else if (!PROPOSALS.isEmpty() && !Me.link().bound()) {
                mc.gui.setScreen(new ProposalScreen(PROPOSALS.pollFirst()));
            } else if (pendingChoice) {
                pendingChoice = false;
                if (!Me.link().bound()) {
                    mc.gui.setScreen(new ChoiceScreen());
                }
            }
        }
        if (Me.link().bound()) {
            PROPOSALS.clear();
        }

        // partners close together: a few hearts now and then
        if (mc.level.getGameTime() % 30 == 0) {
            for (ThreadLinks.Tie tie : ThreadLinks.all(mc.level)) {
                if (tie.extra()) {
                    continue;
                }
                Player a = tie.a();
                Player b = tie.b();
                if (a.distanceToSqr(b) < 1.6 * 1.6 && !a.isInvisible() && !b.isInvisible() && mc.level.getRandom().nextInt(3) == 0) {
                    double x = (a.getX() + b.getX()) * 0.5;
                    double y = Math.max(a.getY(), b.getY()) + 2.1;
                    double z = (a.getZ() + b.getZ()) * 0.5;
                    mc.level.addParticle(ParticleTypes.HEART, x, y, z, 0.0, 0.05, 0.0);
                }
            }
        }
    }
}
