package com.jumpscares.net;

import com.jumpscares.scare.ScareKind;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class ScareNet {
    private ScareNet() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(ScarePayload.TYPE, ScarePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WitnessPayload.TYPE, WitnessPayload.CODEC);
    }

    /** False when the player's client does not have the mod. */
    public static boolean sendScare(ServerPlayer victim, ScareKind kind, long seed) {
        if (!ServerPlayNetworking.canSend(victim, ScarePayload.TYPE)) {
            return false;
        }
        ServerPlayNetworking.send(victim, new ScarePayload(kind.ordinal(), seed));
        return true;
    }

    public static void sendWitness(ServerPlayer witness, ServerPlayer victim, ScareKind kind, long seed) {
        if (ServerPlayNetworking.canSend(witness, WitnessPayload.TYPE)) {
            ServerPlayNetworking.send(witness, new WitnessPayload(victim.getId(), kind.ordinal(), seed));
        }
    }
}
