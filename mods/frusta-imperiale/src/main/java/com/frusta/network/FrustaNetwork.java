package com.frusta.network;

import com.frusta.FrustaMod;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class FrustaNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            FrustaMod.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.messageBuilder(LashPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LashPacket::encode)
                .decoder(LashPacket::decode)
                .consumerMainThread(LashPacket::handle)
                .add();
        CHANNEL.messageBuilder(MovePacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MovePacket::encode)
                .decoder(MovePacket::decode)
                .consumerMainThread(MovePacket::handle)
                .add();
        CHANNEL.messageBuilder(KneelPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KneelPacket::encode)
                .decoder(KneelPacket::decode)
                .consumerMainThread(KneelPacket::handle)
                .add();
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    /** To every player who can see the entity, not to the entity itself. */
    public static void sendToTracking(Entity entity, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
    }

    /** To every player who can see the entity, and to the entity itself if it is a player. */
    public static void sendToTrackingAndSelf(Entity entity, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), packet);
    }

    private FrustaNetwork() {
    }
}
