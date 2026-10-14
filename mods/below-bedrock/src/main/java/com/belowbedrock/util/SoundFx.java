package com.belowbedrock.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Vanilla sounds that only one player hears. */
public final class SoundFx {
    private SoundFx() {
    }

    public static Holder<SoundEvent> holder(SoundEvent sound) {
        return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
    }

    /** A sound at a fixed place in the world, heard only by {@code player}. */
    public static void at(ServerPlayer player, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(sound, source, pos.x, pos.y, pos.z, volume, pitch, player.getRandom().nextLong()));
    }

    public static void at(ServerPlayer player, SoundEvent sound, SoundSource source, Vec3 pos, float volume, float pitch) {
        at(player, holder(sound), source, pos, volume, pitch);
    }

    /** A sound that follows the player around (it does not fade away while they fall). */
    public static void onPlayer(ServerPlayer player, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        player.connection.send(new ClientboundSoundEntityPacket(sound, source, player, volume, pitch, player.getRandom().nextLong()));
    }

    public static void onPlayer(ServerPlayer player, SoundEvent sound, SoundSource source, float volume, float pitch) {
        onPlayer(player, holder(sound), source, volume, pitch);
    }

    /** A point {@code distance} blocks behind the player, at ear height. */
    public static Vec3 behind(ServerPlayer player, double distance) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0.0, 0.0, 1.0);
        }
        return player.getEyePosition().subtract(flat.normalize().scale(distance));
    }

    public static void stopAll(ServerPlayer player) {
        player.connection.send(new ClientboundStopSoundPacket(null, null));
    }
}
