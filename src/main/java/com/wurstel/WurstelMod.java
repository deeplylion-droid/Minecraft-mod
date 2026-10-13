package com.wurstel;

import com.wurstel.config.WurstelConfig;
import com.wurstel.registry.ModItems;
import com.wurstel.registry.ModSounds;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Würstel Gigante: a belt that carries a 4-block würstel. While wearing it, bare-hand punches
 * become floppy würstel slaps.
 */
@Mod(WurstelMod.MODID)
public class WurstelMod {
    public static final String MODID = "wurstel";

    public WurstelMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        modBus.addListener(WurstelMod::addToCreativeTabs);

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, WurstelConfig.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, WurstelConfig.SERVER_SPEC);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.WURSTEL_BELT);
        } else if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(ModItems.WURSTEL);
        }
    }
}
