package com.rainbowyoyo;

import com.mojang.logging.LogUtils;
import com.rainbowyoyo.config.YoyoConfig;
import com.rainbowyoyo.network.YoyoNetwork;
import com.rainbowyoyo.registry.ModEntities;
import com.rainbowyoyo.registry.ModItems;
import com.rainbowyoyo.registry.ModSounds;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Yo-Yo Arcobaleno: a giant rainbow yo-yo to throw, spin, pull tricks with and fight with.
 * Everything that moves is decided by the server and replayed smoothly by every client.
 */
@Mod(RainbowYoyoMod.MODID)
public class RainbowYoyoMod {
    public static final String MODID = "rainbowyoyo";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RainbowYoyoMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        modBus.addListener(RainbowYoyoMod::addToCreativeTabs);
        YoyoNetwork.register();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, YoyoConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, YoyoConfig.CLIENT_SPEC);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.RAINBOW_YOYO);
        }
    }
}
