package com.filorosso.server;

import com.filorosso.data.Couple;
import com.filorosso.data.CoupleBook;
import com.filorosso.data.ModData;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shared life: the two partners have one life. What one loses the other loses too, what one gets
 * back the other gets back. A deadly hit only knocks down whoever takes it and leaves the partner
 * at half a heart: either they get their partner back on their feet, or both are gone.
 */
public final class SharedLife {
    private SharedLife() {
    }

    private static boolean sharing;

    public static void init() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseTaken, taken, blocked) -> {
            if (sharing || taken <= 0.0F || !(entity instanceof ServerPlayer player)) {
                return;
            }
            ServerPlayer partner = partner(player);
            if (partner == null) {
                return;
            }
            // whoever was hit falls first: the shared damage never kills
            float amount = Math.min(taken, partner.getHealth() - 1.0F);
            if (amount <= 0.0F) {
                return;
            }
            sharing = true;
            try {
                partner.setHealth(partner.getHealth() - amount);
                ClientboundHurtAnimationPacket hurt = new ClientboundHurtAnimationPacket(partner);
                partner.connection.send(hurt);
                for (ServerPlayer viewer : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(partner)) {
                    viewer.connection.send(hurt);
                }
                partner.level().playSound(null, partner.getX(), partner.getY(), partner.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.8F, 1.0F);
            } finally {
                sharing = false;
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (alive) {
                return;
            }
            ServerPlayer partner = partner(newPlayer);
            if (partner != null) {
                // back from the dead: the couple starts again with at least three hearts
                float v = Math.max(6.0F, partner.getHealth());
                newPlayer.setHealth(Math.min(v, newPlayer.getMaxHealth()));
                partner.setHealth(Math.min(v, partner.getMaxHealth()));
            }
        });
    }

    /** The partner who shares this player's life right now (online, alive, standing, playing). */
    private static ServerPlayer partner(ServerPlayer player) {
        if (ModData.downed(player) || player.isCreative() || player.isSpectator()) {
            return null;
        }
        ServerPlayer partner = Bonds.partner(player);
        if (partner == null || !partner.isAlive() || partner.isRemoved() || ModData.downed(partner) || partner.isCreative() || partner.isSpectator()) {
            return null;
        }
        return partner;
    }

    /** From the LivingEntity mixin: healing one heals the other. */
    public static void onHeal(LivingEntity entity, float amount) {
        if (sharing || amount <= 0.0F || !(entity instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        ServerPlayer partner = partner(player);
        if (partner == null) {
            return;
        }
        sharing = true;
        try {
            partner.heal(amount);
        } finally {
            sharing = false;
        }
    }

    /** A deadly hit knocked this player down: the partner is left with half a heart. */
    public static void onDowned(ServerPlayer partner) {
        if (partner != null && partner.isAlive() && !partner.isCreative() && !partner.isSpectator()) {
            partner.setHealth(Math.min(partner.getHealth(), 1.0F));
        }
    }

    /** Back on their feet: the couple shares the revived player's hearts. */
    public static void onRevived(ServerPlayer revived, ServerPlayer partner) {
        if (partner != null && partner.isAlive()) {
            float v = Math.max(partner.getHealth(), revived.getHealth());
            partner.setHealth(Math.min(v, partner.getMaxHealth()));
            revived.setHealth(Math.min(v, revived.getMaxHealth()));
        }
    }

    /** Every second the two lives are made equal again (the lower one counts). */
    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % 20 != 0) {
            return;
        }
        for (Couple c : CoupleBook.get(server).couples) {
            ServerPlayer a = Bonds.player(server, c.a);
            ServerPlayer b = a == null ? null : partner(a);
            if (b == null) {
                continue;
            }
            float ha = a.getHealth();
            float hb = b.getHealth();
            if (Math.abs(ha - hb) > 0.25F) {
                float v = Math.min(ha, hb);
                a.setHealth(Math.min(v, a.getMaxHealth()));
                b.setHealth(Math.min(v, b.getMaxHealth()));
            }
        }
    }
}
