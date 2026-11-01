package com.punizioni.server;

import com.punizioni.ModDamage;
import com.punizioni.machine.Machine;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Dying in a punishment (inside a machine, or of a punishment's damage, like bleeding after the
 * chopping block) does not cost the inventory or the experience: the PlayerMixin skips the drops and
 * the new player gets everything back.
 */
public final class Deaths {
    private Deaths() {
    }

    private static final Set<UUID> KEEP = new HashSet<>();

    public static boolean keeps(Player player) {
        return KEEP.contains(player.getUUID());
    }

    public static void init() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer p && (p.getVehicle() instanceof Machine || source.is(ModDamage.PUNISHMENT))) {
                KEEP.add(p.getUUID());
            }
            return true;
        });
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (!alive && KEEP.remove(oldPlayer.getUUID())) {
                newPlayer.getInventory().replaceWith(oldPlayer.getInventory());
                newPlayer.experienceLevel = oldPlayer.experienceLevel;
                newPlayer.totalExperience = oldPlayer.totalExperience;
                newPlayer.experienceProgress = oldPlayer.experienceProgress;
            }
        });
    }
}
