package com.infezione.network;

import com.infezione.InfezioneMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class InfezioneNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            InfezioneMod.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.messageBuilder(StatePacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StatePacket::encode)
                .decoder(StatePacket::decode)
                .consumerMainThread(StatePacket::handle)
                .add();
        CHANNEL.messageBuilder(SymptomPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SymptomPacket::encode)
                .decoder(SymptomPacket::decode)
                .consumerMainThread(SymptomPacket::handle)
                .add();
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** To every player who can see the entity, and to the entity itself if it is a player. */
    public static void sendToTrackingAndSelf(Entity entity, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), packet);
    }

    private InfezioneNetwork() {
    }
}
