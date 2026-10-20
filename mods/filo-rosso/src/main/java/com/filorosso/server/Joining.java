package com.filorosso.server;

import com.filorosso.data.Couple;
import com.filorosso.data.ModData;
import com.filorosso.net.Net;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Joining: the thread runs on the clients, so a player without the mod is sent back with a message;
 * the others get their bond, the current events and, if they are still free, the list of players.
 * Also feeding the partner by hand (right click with food).
 */
public final class Joining {
    private Joining() {
    }

    private static final Map<UUID, Integer> WAITING = new HashMap<>();

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> WAITING.put(handler.player.getUUID(), 0));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer p = handler.player;
            WAITING.remove(p.getUUID());
            Bonds.onLeave(p);
            Tether.forget(p);
            Challenges.forget(p);
            server.execute(() -> Bonds.refreshLists(server));
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer feeder) || !(entity instanceof ServerPlayer partner) || ModData.downed(partner)) {
                return InteractionResult.PASS;
            }
            ItemStack stack = feeder.getItemInHand(hand);
            FoodProperties food = stack.get(DataComponents.FOOD);
            Couple couple = Bonds.coupleOf(feeder);
            if (food == null || couple == null || !couple.has(partner.getUUID()) || !partner.canEat(food.canAlwaysEat())) {
                return InteractionResult.PASS;
            }
            partner.getFoodData().eat(food);
            if (!feeder.isCreative()) {
                stack.shrink(1);
            }
            ServerLevel sl = (ServerLevel) partner.level();
            sl.playSound(null, partner.getX(), partner.getY(), partner.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            Bonds.hearts(partner, 6);
            Affinity.onFeed(feeder, partner);
            return InteractionResult.SUCCESS;
        });
    }

    public static void tick(MinecraftServer server) {
        if (WAITING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> it = WAITING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (ServerPlayNetworking.canSend(player, Net.Candidates.TYPE)) {
                it.remove();
                ready(player);
            } else if (e.getValue() > 100) {
                it.remove();
                player.connection.disconnect(Component.literal(
                        "Questo mondo usa la mod Filo Rosso: installala (con Fabric API) per entrare.\n"
                                + "This world uses the Filo Rosso mod: install it (with Fabric API) to join."));
            } else {
                e.setValue(e.getValue() + 1);
            }
        }
    }

    private static void ready(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Events.sendTo(player);
        Bonds.onReady(player);
        Abilities.sendCooldowns(player);
        Couple couple = Bonds.coupleOf(player);
        if (couple != null) {
            Challenges.sendInfo(server, couple);
            ServerPlayer partner = Bonds.partner(player);
            if (partner != null && (partner.level() != player.level() || partner.distanceTo(player) > Tether.length(server) + 4.0)) {
                Tether.reunite(partner, player, server.overworld().getGameTime() - 1000);
            }
        }
        Scores.refresh(server);
    }
}
