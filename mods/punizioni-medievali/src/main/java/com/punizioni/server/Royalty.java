package com.punizioni.server;

import com.punizioni.ModSounds;
import com.punizioni.item.ModItems;
import com.punizioni.misdeed.Misdeeds;
import com.punizioni.net.Net;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The crown and the lens on the server: putting the crown on is announced to everyone with a
 * fanfare; whoever holds the lens learns every second which subjects nearby are guilty (they glow red
 * on that client only).
 */
public final class Royalty {
    private Royalty() {
    }

    /** How far the lens sees the guilty glow (blocks). */
    public static final double GUILTY_RANGE = 48.0;
    /** One coronation a minute per player, so taking the crown off and on does not spam the chat. */
    private static final long CORONATION_COOLDOWN = 20L * 60L;

    private static final Map<UUID, Long> CROWNED = new HashMap<>();

    public static void init() {
        ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, previous, current) -> {
            // tickCount: not when a player joins or respawns already wearing it
            if (slot == EquipmentSlot.HEAD && entity instanceof ServerPlayer king && king.tickCount > 40 && current.is(ModItems.ROYAL_CROWN)
                    && !previous.is(ModItems.ROYAL_CROWN)) {
                crowned(king);
            }
        });
    }

    private static void crowned(ServerPlayer king) {
        long now = king.level().getGameTime();
        Long last = CROWNED.get(king.getUUID());
        if (last != null && now - last < CORONATION_COOLDOWN) {
            return;
        }
        CROWNED.put(king.getUUID(), now);
        king.level().playSound(null, king.getX(), king.getY() + 1.0, king.getZ(), ModSounds.FANFARE, SoundSource.PLAYERS, 1.0F, 1.0F);
        Component message = Component.translatable("punizioni.msg.crowned", king.getName()).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : king.level().getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(message);
        }
    }

    /** Every second: the guilty nearby, to each lens holder. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer holder : server.getPlayerList().getPlayers()) {
            if (!(holder.getMainHandItem().is(ModItems.ROYAL_LENS) || holder.getOffhandItem().is(ModItems.ROYAL_LENS))
                    || !ServerPlayNetworking.canSend(holder, Net.Guilty.TYPE)) {
                continue;
            }
            long now = holder.level().getGameTime();
            List<Integer> ids = new ArrayList<>();
            for (ServerPlayer other : holder.level().players()) {
                if (other != holder && other.distanceToSqr(holder) < GUILTY_RANGE * GUILTY_RANGE && Misdeeds.guilty(other.getUUID(), now)) {
                    ids.add(other.getId());
                }
            }
            ServerPlayNetworking.send(holder, new Net.Guilty(ids));
        }
    }
}
