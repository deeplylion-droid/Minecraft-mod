package com.punizioni.server;

import com.punizioni.ModSounds;
import com.punizioni.item.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The jester's bells: they ring at every step (faster running), at every jump and landing, and when
 * the jester gets hit. The more pieces of motley, the more bells, the louder.
 */
public final class Jester {
    private Jester() {
    }

    /** A jingle every this many blocks walked. */
    private static final double STEP = 1.1;

    /** Per jester: last x, last z, walked since the last jingle, on the ground (1) or not. */
    private static final Map<UUID, double[]> WALK = new HashMap<>();

    public static void init() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (entity instanceof ServerPlayer p && taken > 0.0F && bells(p) > 0) {
                jingle(p, 0.9F, 1.25F);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> WALK.remove(handler.player.getUUID()));
    }

    /** How many pieces of motley (each has bells). */
    public static int bells(Player p) {
        int n = 0;
        n += p.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.JESTER_HAT) ? 1 : 0;
        n += p.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.JESTER_TUNIC) ? 1 : 0;
        n += p.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.JESTER_HOSE) ? 1 : 0;
        n += p.getItemBySlot(EquipmentSlot.FEET).is(ModItems.JESTER_SHOES) ? 1 : 0;
        return n;
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            int bells = bells(p);
            if (bells == 0 || p.isSpectator()) {
                WALK.remove(p.getUUID());
                continue;
            }
            double[] w = WALK.computeIfAbsent(p.getUUID(), id -> new double[]{p.getX(), p.getZ(), 0.0, p.onGround() ? 1.0 : 0.0});
            double dx = p.getX() - w[0];
            double dz = p.getZ() - w[1];
            double d = Math.sqrt(dx * dx + dz * dz);
            w[0] = p.getX();
            w[1] = p.getZ();
            boolean ground = p.onGround();
            if (d < 2.0 && ground && !p.isPassenger()) {
                w[2] += d;
                if (w[2] >= STEP) {
                    w[2] = 0.0;
                    jingle(p, 0.3F + 0.1F * bells, 0.9F + p.getRandom().nextFloat() * 0.25F);
                }
            }
            if (ground != w[3] > 0.0) {
                // a jump, or the landing
                w[3] = ground ? 1.0 : 0.0;
                if (!p.isPassenger() && !p.getAbilities().flying) {
                    jingle(p, 0.45F + 0.12F * bells, ground ? 0.85F : 1.1F);
                }
            }
        }
    }

    private static void jingle(ServerPlayer p, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), ModSounds.JINGLE, SoundSource.PLAYERS, volume, pitch);
    }
}
