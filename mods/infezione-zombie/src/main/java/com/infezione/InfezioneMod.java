package com.infezione;

import com.infezione.config.InfezioneConfig;
import com.infezione.network.InfezioneNetwork;
import com.infezione.registry.ModItems;
import com.infezione.registry.ModParticles;
import com.infezione.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Infezione Zombie: a zombie infection in five stages (Morso, Febbre, Delirio, Agonia, Zombie), given
 * with a command. It shows on the screen of the infected player and on their body for everyone: the skin
 * turns grey and veined, the eyes go milky, blood, coughs, twitches, convulsions. At stage 5 the player
 * is a zombie; until stage 4 an antidote can still cure them.
 */
@Mod(InfezioneMod.MODID)
public class InfezioneMod {
    public static final String MODID = "infezione";
    public static final Logger LOGGER = LogUtils.getLogger();

    public InfezioneMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        modBus.addListener(InfezioneMod::addToCreativeTabs);
        InfezioneNetwork.register();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, InfezioneConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, InfezioneConfig.CLIENT_SPEC);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(ModItems.ANTIDOTE);
        }
    }
}
