package com.belowbedrock;

import com.belowbedrock.fall.VoidFall;
import com.belowbedrock.haunt.Director;
import com.belowbedrock.haunt.FadedCommand;
import com.belowbedrock.net.ScreenFxPayload;
import com.belowbedrock.registry.ModAttachments;
import com.belowbedrock.registry.ModEntities;
import com.belowbedrock.registry.ModGameRules;
import com.belowbedrock.registry.ModItems;
import com.belowbedrock.registry.ModLoot;
import com.belowbedrock.world.FadedWorld;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sotto la Bedrock: a glitched pickaxe breaks the bedrock, the void does not kill you but lets you
 * fall for half a minute, and you wake up in a faded copy of your own world where someone lives.
 */
public class BelowBedrock implements ModInitializer {
    public static final String MODID = "belowbedrock";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    @Override
    public void onInitialize() {
        ModItems.register();
        ModEntities.register();
        ModLoot.register();
        ModGameRules.register();
        ModAttachments.register();
        ScreenFxPayload.register();
        VoidFall.register();
        FadedWorld.register();
        Director.register();
        FadedCommand.register();
    }
}
