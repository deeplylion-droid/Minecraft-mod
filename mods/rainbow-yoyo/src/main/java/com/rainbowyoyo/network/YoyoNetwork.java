package com.rainbowyoyo.network;

import com.rainbowyoyo.RainbowYoyoMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class YoyoNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RainbowYoyoMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.messageBuilder(YoyoActionPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(YoyoActionPacket::encode)
                .decoder(YoyoActionPacket::decode)
                .consumerMainThread(YoyoActionPacket::handle)
                .add();
    }

    public static void sendToServer(YoyoActionPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    private YoyoNetwork() {
    }
}
