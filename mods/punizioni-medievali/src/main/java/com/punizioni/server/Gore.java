package com.punizioni.server;

import com.punizioni.ModParticles;
import com.punizioni.net.Net;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Blood, for everyone to see, and the jolt on the victim's own screen. */
public final class Gore {
    private Gore() {
    }

    /** A spray of blood from a point. */
    public static void burst(ServerLevel level, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(ModParticles.BLOOD, true, true, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    /** Blood spurting along a direction (a cut, a stab): several puffs along the jet. */
    public static void jet(ServerLevel level, Vec3 at, Vec3 dir, int count, double speed) {
        Vec3 d = dir.normalize();
        for (int i = 0; i < count; i++) {
            double k = 0.6 + level.getRandom().nextDouble() * 0.8;
            // count = 0 makes the particle fly exactly along (dx, dy, dz) with that speed
            level.sendParticles(ModParticles.BLOOD, true, true, at.x, at.y, at.z, 0,
                    d.x + (level.getRandom().nextDouble() - 0.5) * 0.35, d.y + (level.getRandom().nextDouble() - 0.5) * 0.35,
                    d.z + (level.getRandom().nextDouble() - 0.5) * 0.35, speed * k);
        }
    }

    /** Smoke and ash, for the fires. */
    public static void smoke(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, count, spread, spread * 0.5, spread, 0.02);
    }

    /** On the victim's own screen: a red flash and a jolt (or worse, depending on the kind). */
    public static void hurtScreen(LivingEntity victim, int kind, float strength) {
        if (victim instanceof ServerPlayer p && ServerPlayNetworking.canSend(p, Net.VictimFx.TYPE)) {
            ServerPlayNetworking.send(p, new Net.VictimFx(kind, strength));
        }
    }

    /** A payload for everyone who sees this entity (and the entity itself, if it is a player). */
    public static void toAll(Entity around, CustomPacketPayload payload) {
        for (ServerPlayer viewer : PlayerLookup.tracking(around)) {
            if (ServerPlayNetworking.canSend(viewer, payload.type())) {
                ServerPlayNetworking.send(viewer, payload);
            }
        }
        if (around instanceof ServerPlayer self && ServerPlayNetworking.canSend(self, payload.type())) {
            ServerPlayNetworking.send(self, payload);
        }
    }
}
